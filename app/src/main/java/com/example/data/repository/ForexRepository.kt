package com.example.data.repository

import com.example.core.NetworkGuardian
import com.example.data.model.DataFreshness
import com.example.data.model.ForexHistoryPoint
import com.example.data.model.ForexQuote
import com.example.data.model.ForexWatchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class ForexRepository(
    private val networkGuardian: NetworkGuardian
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchQuotesForWatchlist(
        watchlist: List<ForexWatchItem>
    ): List<ForexQuote> = withContext(Dispatchers.IO) {
        if (watchlist.isEmpty()) return@withContext emptyList()

        val utcFormat = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val endCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val startCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_YEAR, -21)
        }
        val startDate = dateFormat.format(startCal.time)
        val endDate = dateFormat.format(endCal.time)

        val quotes = mutableListOf<ForexQuote>()
        val cachePayload = JSONObject()

        try {
            val erApiRates = fetchOpenErApiUsdRates()
            val nowLabel = utcFormat.format(Date())

            for (item in watchlist) {
                val base = item.baseCurrency.uppercase()
                val quote = item.quoteCurrency.uppercase()
                val pair = "$base/$quote"

                val frankfurterResult = fetchFrankfurterPair(base, quote, startDate, endDate)
                if (frankfurterResult != null) {
                    val (latestRate, prevRate, apiDate, history) = frankfurterResult
                    quotes += ForexQuote(
                        pair = pair,
                        base = base,
                        quote = quote,
                        rate = latestRate,
                        previousRate = prevRate,
                        timestampLabel = "$apiDate (Ref: $nowLabel)",
                        providerName = "Frankfurter (ECB)",
                        freshness = DataFreshness.REALTIME_API,
                        history = history
                    )
                    cachePayload.put(
                        pair,
                        JSONObject().apply {
                            put("rate", latestRate)
                            put("prev", prevRate ?: latestRate)
                            put("provider", "Frankfurter (ECB)")
                            put("ts", "$apiDate • $nowLabel")
                            put("history", JSONArray().apply {
                                history.forEach { pt ->
                                    put(JSONObject().apply {
                                        put("d", pt.dateLabel)
                                        put("r", pt.rate)
                                    })
                                }
                            })
                        }
                    )
                } else if (erApiRates != null) {
                    val computedRate = computeCrossRate(erApiRates, base, quote)
                    if (computedRate != null) {
                        quotes += ForexQuote(
                            pair = pair,
                            base = base,
                            quote = quote,
                            rate = computedRate,
                            previousRate = null,
                            timestampLabel = nowLabel,
                            providerName = "Open ER-API",
                            freshness = DataFreshness.REALTIME_API,
                            history = emptyList()
                        )
                        cachePayload.put(
                            pair,
                            JSONObject().apply {
                                put("rate", computedRate)
                                put("provider", "Open ER-API")
                                put("ts", nowLabel)
                            }
                        )
                    } else {
                        quotes += ForexQuote(
                            pair = pair,
                            base = base,
                            quote = quote,
                            rate = null,
                            previousRate = null,
                            timestampLabel = nowLabel,
                            providerName = "Unavailable",
                            freshness = DataFreshness.UNAVAILABLE,
                            errorNote = "Currency code $base or $quote not returned by free market feeds."
                        )
                    }
                } else {
                    quotes += ForexQuote(
                        pair = pair,
                        base = base,
                        quote = quote,
                        rate = null,
                        previousRate = null,
                        timestampLabel = nowLabel,
                        providerName = "Unavailable",
                        freshness = DataFreshness.UNAVAILABLE,
                        errorNote = "Market feed unavailable right now."
                    )
                }
            }

            if (cachePayload.length() > 0) {
                networkGuardian.saveForexCache(cachePayload.toString(), nowLabel)
            }
            return@withContext quotes
        } catch (e: Exception) {
            val cached = networkGuardian.forexCacheFlow.firstOrNull()
            if (cached != null) {
                return@withContext parseCachedQuotes(watchlist, cached.first, cached.second)
            }
            return@withContext watchlist.map { item ->
                ForexQuote(
                    pair = item.pairLabel,
                    base = item.baseCurrency,
                    quote = item.quoteCurrency,
                    rate = null,
                    previousRate = null,
                    timestampLabel = "Unavailable",
                    providerName = "Offline",
                    freshness = DataFreshness.UNAVAILABLE,
                    errorNote = e.localizedMessage ?: "Unable to reach market data API."
                )
            }
        }
    }

    private data class FrankfurterData(
        val latestRate: Double,
        val previousRate: Double?,
        val dateLabel: String,
        val history: List<ForexHistoryPoint>
    )

    private suspend fun fetchFrankfurterPair(
        base: String,
        quote: String,
        startDate: String,
        endDate: String
    ): FrankfurterData? {
        if (base == quote) {
            return FrankfurterData(1.0, 1.0, endDate, listOf(ForexHistoryPoint(endDate, 1.0)))
        }
        return try {
            val url = "https://api.frankfurter.dev/v1/$startDate..$endDate?base=$base&symbols=$quote"
            val bodyStr = networkGuardian.executeGuarded("fx_ff_${base}_$quote", maxRetries = 1) {
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    resp.body?.string() ?: throw IllegalStateException("Empty body")
                }
            }
            val json = JSONObject(bodyStr)
            val ratesObj = json.optJSONObject("rates") ?: return null
            val sortedDates = ratesObj.keys().asSequence().toList().sorted()
            val history = sortedDates.mapNotNull { d ->
                val dayRates = ratesObj.optJSONObject(d)
                val value = dayRates?.optDouble(quote, Double.NaN) ?: Double.NaN
                if (!value.isNaN()) ForexHistoryPoint(d, value) else null
            }
            if (history.isEmpty()) return null
            val latest = history.last()
            val prev = if (history.size >= 2) history[history.size - 2].rate else null
            FrankfurterData(
                latestRate = latest.rate,
                previousRate = prev,
                dateLabel = latest.dateLabel,
                history = history
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchOpenErApiUsdRates(): JSONObject? {
        return try {
            val url = "https://open.er-api.com/v6/latest/USD"
            val bodyStr = networkGuardian.executeGuarded("fx_er_usd", maxRetries = 1) {
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    resp.body?.string() ?: throw IllegalStateException("Empty body")
                }
            }
            val json = JSONObject(bodyStr)
            if (json.optString("result") == "success") json.optJSONObject("rates") else null
        } catch (_: Exception) {
            null
        }
    }

    private fun computeCrossRate(usdRates: JSONObject, base: String, quote: String): Double? {
        val usdToBase = if (base == "USD") 1.0 else usdRates.optDouble(base, Double.NaN)
        val usdToQuote = if (quote == "USD") 1.0 else usdRates.optDouble(quote, Double.NaN)
        if (usdToBase.isNaN() || usdToQuote.isNaN() || usdToBase == 0.0) return null
        return usdToQuote / usdToBase
    }

    private fun parseCachedQuotes(
        watchlist: List<ForexWatchItem>,
        cacheJson: String,
        cachedTs: String
    ): List<ForexQuote> {
        val root = try {
            JSONObject(cacheJson)
        } catch (_: Exception) {
            JSONObject()
        }
        return watchlist.map { item ->
            val pair = item.pairLabel
            val obj = root.optJSONObject(pair)
            if (obj != null) {
                val histArr = obj.optJSONArray("history")
                val hist = mutableListOf<ForexHistoryPoint>()
                if (histArr != null) {
                    for (i in 0 until histArr.length()) {
                        val pt = histArr.optJSONObject(i) ?: continue
                        hist += ForexHistoryPoint(pt.optString("d"), pt.optDouble("r"))
                    }
                }
                ForexQuote(
                    pair = pair,
                    base = item.baseCurrency,
                    quote = item.quoteCurrency,
                    rate = obj.optDouble("rate"),
                    previousRate = if (obj.has("prev")) obj.optDouble("prev") else null,
                    timestampLabel = "Cached ($cachedTs)",
                    providerName = obj.optString("provider", "Cached"),
                    freshness = DataFreshness.CACHED_DELAYED,
                    history = hist,
                    errorNote = "Showing last verified cached price due to weak/offline connection."
                )
            } else {
                ForexQuote(
                    pair = pair,
                    base = item.baseCurrency,
                    quote = item.quoteCurrency,
                    rate = null,
                    previousRate = null,
                    timestampLabel = "Unavailable",
                    providerName = "Offline",
                    freshness = DataFreshness.UNAVAILABLE,
                    errorNote = "No cached rate available for $pair."
                )
            }
        }
    }
}
