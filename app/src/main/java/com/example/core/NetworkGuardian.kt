package com.example.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_guardian_prefs")

enum class ConnectionQuality(val label: String) {
    STRONG("Online • High Speed"),
    WEAK("Weak / Unvalidated Link"),
    OFFLINE("Offline Mode")
}

data class NetworkGuardianState(
    val quality: ConnectionQuality = ConnectionQuality.STRONG,
    val transportLabel: String = "Checking...",
    val downstreamKbps: Int = 0,
    val activeRequests: Int = 0,
    val retryCount: Int = 0,
    val lastError: String? = null
)

class NetworkGuardian(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(evaluateCurrentNetwork())
    val state: StateFlow<NetworkGuardianState> = _state.asStateFlow()

    private val inFlightMutex = Mutex()
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<Any?>>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshState()
        }

        override fun onLost(network: Network) {
            refreshState()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            refreshState()
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {
            // Fallback to polling via evaluateCurrentNetwork()
        }
    }

    fun refreshState() {
        val current = evaluateCurrentNetwork()
        _state.value = _state.value.copy(
            quality = current.quality,
            transportLabel = current.transportLabel,
            downstreamKbps = current.downstreamKbps
        )
    }

    private fun evaluateCurrentNetwork(): NetworkGuardianState {
        val network = connectivityManager.activeNetwork
            ?: return NetworkGuardianState(ConnectionQuality.OFFLINE, "No Active Network", 0)
        val caps = connectivityManager.getNetworkCapabilities(network)
            ?: return NetworkGuardianState(ConnectionQuality.OFFLINE, "No Capabilities", 0)

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val kbps = caps.linkDownstreamBandwidthKbps

        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Network"
        }

        val quality = when {
            !hasInternet -> ConnectionQuality.OFFLINE
            !isValidated || (kbps in 1..499) -> ConnectionQuality.WEAK
            else -> ConnectionQuality.STRONG
        }

        return NetworkGuardianState(
            quality = quality,
            transportLabel = transport,
            downstreamKbps = kbps
        )
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> executeGuarded(
        dedupKey: String,
        maxRetries: Int = 2,
        block: suspend () -> T
    ): T {
        val existingDeferred = inFlightMutex.withLock {
            inFlightRequests[dedupKey] as? Deferred<T>
        }
        if (existingDeferred != null && existingDeferred.isActive) {
            return existingDeferred.await()
        }

        val deferred = CompletableDeferred<T>()
        inFlightMutex.withLock {
            inFlightRequests[dedupKey] = deferred as Deferred<Any?>
        }

        _state.value = _state.value.copy(
            activeRequests = _state.value.activeRequests + 1,
            lastError = null
        )

        try {
            var attempt = 0
            var delayMs = 650L
            while (true) {
                try {
                    val result = block()
                    deferred.complete(result)
                    return result
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempt++
                    val retryable = e is IOException || e is SocketTimeoutException
                    if (!retryable || attempt > maxRetries) {
                        _state.value = _state.value.copy(
                            lastError = e.localizedMessage ?: "Network operation failed"
                        )
                        throw e
                    }
                    _state.value = _state.value.copy(
                        retryCount = _state.value.retryCount + 1,
                        lastError = "Weak connection — retrying ($attempt/$maxRetries)..."
                    )
                    delay(delayMs)
                    delayMs *= 2
                }
            }
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            inFlightMutex.withLock {
                inFlightRequests.remove(dedupKey)
            }
            _state.value = _state.value.copy(
                activeRequests = (_state.value.activeRequests - 1).coerceAtLeast(0)
            )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(lastError = null)
    }

    val draftMessageFlow: Flow<String> = appContext.jarvisDataStore.data.map { prefs ->
        prefs[KEY_DRAFT_MESSAGE].orEmpty()
    }

    suspend fun saveDraftMessage(draft: String) {
        appContext.jarvisDataStore.edit { prefs ->
            prefs[KEY_DRAFT_MESSAGE] = draft
        }
    }

    suspend fun saveForexCache(json: String, timestampLabel: String) {
        appContext.jarvisDataStore.edit { prefs ->
            prefs[KEY_FOREX_CACHE_JSON] = json
            prefs[KEY_FOREX_CACHE_TS] = timestampLabel
        }
    }

    val forexCacheFlow: Flow<Pair<String, String>?> = appContext.jarvisDataStore.data.map { prefs ->
        val json = prefs[KEY_FOREX_CACHE_JSON]
        val ts = prefs[KEY_FOREX_CACHE_TS]
        if (!json.isNullOrBlank() && !ts.isNullOrBlank()) json to ts else null
    }

    suspend fun saveImageGalleryJson(json: String) {
        appContext.jarvisDataStore.edit { prefs ->
            prefs[KEY_IMAGE_GALLERY_JSON] = json
        }
    }

    val imageGalleryJsonFlow: Flow<String> = appContext.jarvisDataStore.data.map { prefs ->
        prefs[KEY_IMAGE_GALLERY_JSON].orEmpty()
    }

    suspend fun saveUpdateManifestUrl(url: String) {
        appContext.jarvisDataStore.edit { prefs ->
            prefs[KEY_UPDATE_MANIFEST_URL] = url.trim()
        }
    }

    val updateManifestUrlFlow: Flow<String> = appContext.jarvisDataStore.data.map { prefs ->
        prefs[KEY_UPDATE_MANIFEST_URL].orEmpty()
    }

    suspend fun saveLastUpdateJson(json: String) {
        appContext.jarvisDataStore.edit { prefs ->
            prefs[KEY_LAST_UPDATE_JSON] = json
        }
    }

    val lastUpdateJsonFlow: Flow<String> = appContext.jarvisDataStore.data.map { prefs ->
        prefs[KEY_LAST_UPDATE_JSON].orEmpty()
    }

    fun release() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
    }

    private companion object {
        val KEY_DRAFT_MESSAGE = stringPreferencesKey("draft_message")
        val KEY_FOREX_CACHE_JSON = stringPreferencesKey("forex_cache_json")
        val KEY_FOREX_CACHE_TS = stringPreferencesKey("forex_cache_ts")
        val KEY_IMAGE_GALLERY_JSON = stringPreferencesKey("image_gallery_json")
        val KEY_UPDATE_MANIFEST_URL = stringPreferencesKey("update_manifest_url")
        val KEY_LAST_UPDATE_JSON = stringPreferencesKey("last_update_json")
    }
}
