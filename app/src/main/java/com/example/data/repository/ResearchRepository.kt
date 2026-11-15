package com.example.data.repository

import com.example.core.NetworkGuardian
import com.example.data.model.JarvisLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WebSearchHit(
    val title: String,
    val snippet: String,
    val url: String,
    val timestamp: String,
    val provider: String
)

class ResearchRepository(
    private val networkGuardian: NetworkGuardian
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun searchWebSources(
        query: String,
        language: JarvisLanguage
    ): List<WebSearchHit> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val hits = mutableListOf<WebSearchHit>()
        val wikiLang = when (language) {
            JarvisLanguage.BANGLA -> "bn"
            JarvisLanguage.ARABIC -> "ar"
            JarvisLanguage.ENGLISH -> "en"
        }

        // 1. Wikipedia API search (localized + English fallback if sparse)
        hits += queryWikipedia(cleanQuery, wikiLang)
        if (wikiLang != "en" && hits.size < 3) {
            hits += queryWikipedia(cleanQuery, "en")
        }

        // 2. DuckDuckGo Instant Answer API
        hits += queryDuckDuckGo(cleanQuery)

        hits.distinctBy { it.url }.take(8)
    }

    private suspend fun queryWikipedia(query: String, langSubdomain: String): List<WebSearchHit> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url =
                "https://$langSubdomain.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&srlimit=5&format=json&utf8=1"
            val bodyStr = networkGuardian.executeGuarded("wiki_${langSubdomain}_$encoded", maxRetries = 1) {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "JarvisAiGuardianAndroid/1.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("Wikipedia HTTP ${resp.code}")
                    resp.body?.string() ?: throw IllegalStateException("Empty Wikipedia response")
                }
            }
            val root = JSONObject(bodyStr)
            val searchArr = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
            val results = mutableListOf<WebSearchHit>()
            for (i in 0 until searchArr.length()) {
                val item = searchArr.optJSONObject(i) ?: continue
                val title = item.optString("title")
                val rawSnippet = item.optString("snippet")
                    .replace(Regex("<[^>]*>"), "")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                val timestamp = item.optString("timestamp").take(10)
                val articleUrl =
                    "https://$langSubdomain.wikipedia.org/wiki/${URLEncoder.encode(title.replace(" ", "_"), "UTF-8")}"
                if (title.isNotBlank()) {
                    results += WebSearchHit(
                        title = title,
                        snippet = rawSnippet,
                        url = articleUrl,
                        timestamp = timestamp.ifBlank { "Date unspecified" },
                        provider = "Wikipedia ($langSubdomain)"
                    )
                }
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun queryDuckDuckGo(query: String): List<WebSearchHit> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val bodyStr = networkGuardian.executeGuarded("ddg_$encoded", maxRetries = 1) {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "JarvisAiGuardianAndroid/1.0")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("DuckDuckGo HTTP ${resp.code}")
                    resp.body?.string() ?: throw IllegalStateException("Empty DuckDuckGo response")
                }
            }
            val json = JSONObject(bodyStr)
            val results = mutableListOf<WebSearchHit>()
            val abstractText = json.optString("AbstractText")
            val abstractUrl = json.optString("AbstractURL")
            val heading = json.optString("Heading")
            val abstractSource = json.optString("AbstractSource")
            if (abstractText.isNotBlank() && abstractUrl.isNotBlank()) {
                results += WebSearchHit(
                    title = heading.ifBlank { abstractSource },
                    snippet = abstractText,
                    url = abstractUrl,
                    timestamp = "Instant Answer",
                    provider = abstractSource.ifBlank { "DuckDuckGo" }
                )
            }
            val related = json.optJSONArray("RelatedTopics")
            if (related != null) {
                for (i in 0 until minOf(related.length(), 4)) {
                    val obj = related.optJSONObject(i) ?: continue
                    val text = obj.optString("Text")
                    val firstUrl = obj.optString("FirstURL")
                    if (text.isNotBlank() && firstUrl.isNotBlank()) {
                        results += WebSearchHit(
                            title = text.take(70),
                            snippet = text,
                            url = firstUrl,
                            timestamp = "Web Reference",
                            provider = "DuckDuckGo"
                        )
                    }
                }
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }
}
