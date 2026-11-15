package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.NetworkGuardian
import com.example.core.NetworkGuardianState
import com.example.data.model.ConversationMessage
import com.example.data.model.JarvisLanguage
import com.example.data.model.UserMemoryItem
import com.example.data.model.UserProfile
import com.example.data.model.VoiceCommandEntry
import com.example.data.repository.JarvisAiRepository
import com.example.data.repository.JarvisFirestoreRepository
import com.example.update.ApkVerificationStatus
import com.example.update.InstallLaunchResult
import com.example.update.UpdateCheckOutcome
import com.example.update.VersionUpdateManager
import com.example.update.VersionUpdateUiState
import com.example.voice.VoiceEngineState
import com.example.voice.VoiceGuardianEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val streamingReply: String = "",
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val draftText: String = ""
)

class JarvisCoreViewModel(
    private val appContext: Context,
    private val currentUserId: String,
    private val displayName: String,
    private val firestoreRepo: JarvisFirestoreRepository,
    private val aiRepo: JarvisAiRepository,
    private val networkGuardian: NetworkGuardian,
    private val voiceEngine: VoiceGuardianEngine,
    private val versionUpdateManager: VersionUpdateManager
) : ViewModel() {

    val networkState: StateFlow<NetworkGuardianState> = networkGuardian.state
    val voiceState: StateFlow<VoiceEngineState> = voiceEngine.state

    val userProfile: StateFlow<UserProfile?> = firestoreRepo.observeUserProfile(currentUserId)
        .catch { e -> unwrapAndLog("observeUserProfile", e); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val conversations: StateFlow<List<ConversationMessage>> = firestoreRepo.observeConversations(currentUserId)
        .catch { e -> unwrapAndLog("observeConversations", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val voiceCommands: StateFlow<List<VoiceCommandEntry>> = firestoreRepo.observeVoiceCommands(currentUserId)
        .catch { e -> unwrapAndLog("observeVoiceCommands", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val memories: StateFlow<List<UserMemoryItem>> = firestoreRepo.observeMemories(currentUserId)
        .catch { e -> unwrapAndLog("observeMemories", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _chatState = MutableStateFlow(ChatUiState())
    val chatState: StateFlow<ChatUiState> = _chatState.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(JarvisLanguage.ENGLISH)
    val selectedLanguage: StateFlow<JarvisLanguage> = _selectedLanguage.asStateFlow()

    private val initialInstalled = versionUpdateManager.getInstalledVersionInfo()
    private val _versionUpdateState = MutableStateFlow(
        VersionUpdateUiState(
            installed = initialInstalled,
            manifestUrl = VersionUpdateManager.DEFAULT_MANIFEST_URL,
            versionHistory = versionUpdateManager.getBundledHistory(initialInstalled.versionCode)
        )
    )
    val versionUpdateState: StateFlow<VersionUpdateUiState> = _versionUpdateState.asStateFlow()

    private var activeGenerationJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val profile = firestoreRepo.ensureUserProfile(currentUserId, displayName)
                val lang = JarvisLanguage.fromCode(profile.preferredLanguage)
                _selectedLanguage.value = lang
                voiceEngine.updateSettings(lang, profile.speechRate.toFloat(), !profile.voiceResponseEnabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize user profile", e)
            }
        }

        viewModelScope.launch {
            userProfile.collect { profile ->
                if (profile != null) {
                    val lang = JarvisLanguage.fromCode(profile.preferredLanguage)
                    _selectedLanguage.value = lang
                    voiceEngine.updateSettings(
                        language = lang,
                        rate = profile.speechRate.toFloat(),
                        muted = !profile.voiceResponseEnabled || voiceEngine.state.value.isMuted
                    )
                }
            }
        }

        viewModelScope.launch {
            networkGuardian.draftMessageFlow.collect { savedDraft ->
                if (_chatState.value.draftText.isBlank() && savedDraft.isNotBlank()) {
                    _chatState.value = _chatState.value.copy(draftText = savedDraft)
                }
            }
        }

        versionUpdateManager.cleanupObsoleteApks()

        viewModelScope.launch {
            networkGuardian.updateManifestUrlFlow.collect { savedUrl ->
                val effectiveUrl = savedUrl.ifBlank { VersionUpdateManager.DEFAULT_MANIFEST_URL }
                if (_versionUpdateState.value.manifestUrl != effectiveUrl) {
                    _versionUpdateState.value = _versionUpdateState.value.copy(manifestUrl = effectiveUrl)
                }
                if (savedUrl.isNotBlank() && _versionUpdateState.value.lastCheckedLabel == "Not checked yet") {
                    checkForAppUpdates(effectiveUrl)
                }
            }
        }

        viewModelScope.launch {
            networkGuardian.lastUpdateJsonFlow.collect { cachedJson ->
                if (cachedJson.isNotBlank() && _versionUpdateState.value.remoteManifest == null) {
                    runCatching {
                        val parsed = VersionUpdateManager.parseRemoteVersionManifest(
                            cachedJson,
                            initialInstalled.packageName
                        )
                        val isNewer = parsed.latestVersionCode > initialInstalled.versionCode
                        _versionUpdateState.value = _versionUpdateState.value.copy(
                            remoteManifest = parsed,
                            updateAvailable = isNewer,
                            versionHistory = versionUpdateManager.mergeVersionHistory(parsed, initialInstalled.versionCode)
                        )
                    }
                }
            }
        }
    }

    fun updateDraft(text: String) {
        _chatState.value = _chatState.value.copy(draftText = text)
        viewModelScope.launch {
            networkGuardian.saveDraftMessage(text)
        }
    }

    fun setLanguage(language: JarvisLanguage) {
        _selectedLanguage.value = language
        val rate = (userProfile.value?.speechRate ?: 1.0).toFloat()
        voiceEngine.updateSettings(language, rate, voiceEngine.state.value.isMuted)
        viewModelScope.launch {
            try {
                firestoreRepo.updateUserProfileSettings(currentUserId, preferredLanguage = language.code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save language setting", e)
            }
        }
    }

    fun startVoiceListening() {
        voiceEngine.startListening(_selectedLanguage.value)
    }

    fun stopVoiceListening() {
        voiceEngine.stopListening()
    }

    fun toggleMute() {
        val nextMuted = !voiceEngine.state.value.isMuted
        val rate = (userProfile.value?.speechRate ?: 1.0).toFloat()
        voiceEngine.updateSettings(_selectedLanguage.value, rate, nextMuted)
    }

    fun interruptJarvis() {
        activeGenerationJob?.cancel()
        voiceEngine.stopSpeaking()
        voiceEngine.stopListening()
        _chatState.value = _chatState.value.copy(isGenerating = false)
    }

    fun handleSpokenTranscript(transcript: String, wakeTriggered: Boolean) {
        sendAssistantMessage(
            prompt = transcript,
            source = "voice",
            speakReply = true,
            wakeTriggered = wakeTriggered
        )
    }

    fun sendAssistantMessage(
        prompt: String,
        source: String = "chat",
        speakReply: Boolean = false,
        wakeTriggered: Boolean = false
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank() || _chatState.value.isGenerating) return

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            val lang = _selectedLanguage.value
            _chatState.value = _chatState.value.copy(
                isGenerating = true,
                streamingReply = "",
                errorMessage = null,
                draftText = ""
            )
            networkGuardian.saveDraftMessage("")

            try {
                firestoreRepo.addConversationMessage(
                    userId = currentUserId,
                    sessionId = "main",
                    role = "user",
                    content = cleanPrompt,
                    language = lang.code,
                    source = source
                )

                val historyPairs = conversations.value.takeLast(10).map { it.role to it.content }
                val approvedMem = if (userProfile.value?.memoryOptIn != false) {
                    memories.value.filter { it.approved }.map { "${it.title}: ${it.content}" }
                } else emptyList()

                var finalReply = ""
                aiRepo.streamConversation(cleanPrompt, historyPairs, lang, approvedMem)
                    .collect { partial ->
                        finalReply = partial
                        _chatState.value = _chatState.value.copy(streamingReply = partial)
                    }

                firestoreRepo.addConversationMessage(
                    userId = currentUserId,
                    sessionId = "main",
                    role = "assistant",
                    content = finalReply,
                    language = lang.code,
                    source = source
                )

                if (source == "voice") {
                    firestoreRepo.addVoiceCommand(
                        userId = currentUserId,
                        transcript = cleanPrompt,
                        responseSummary = finalReply.take(800),
                        language = lang.code,
                        wakeTriggered = wakeTriggered
                    )
                }

                _chatState.value = _chatState.value.copy(
                    isGenerating = false,
                    streamingReply = ""
                )

                if (speakReply && (userProfile.value?.voiceResponseEnabled != false)) {
                    voiceEngine.speak(finalReply)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat timed out", e)
                _chatState.value = _chatState.value.copy(
                    isGenerating = false,
                    errorMessage = "Request timed out after 60s. Your message was preserved."
                )
            } catch (e: CancellationException) {
                _chatState.value = _chatState.value.copy(isGenerating = false)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Conversation error", e)
                _chatState.value = _chatState.value.copy(
                    isGenerating = false,
                    errorMessage = e.localizedMessage ?: "Assistant request failed."
                )
            }
        }
    }

    fun clearChatError() {
        _chatState.value = _chatState.value.copy(errorMessage = null)
        voiceEngine.clearError()
        networkGuardian.clearError()
    }

    fun updateSettings(
        voiceResponseEnabled: Boolean? = null,
        speechRate: Double? = null,
        confirmSensitiveActions: Boolean? = null,
        memoryOptIn: Boolean? = null,
        wakePhraseEnabled: Boolean? = null
    ) {
        viewModelScope.launch {
            try {
                firestoreRepo.updateUserProfileSettings(
                    userId = currentUserId,
                    speechRate = speechRate,
                    voiceResponseEnabled = voiceResponseEnabled,
                    confirmSensitiveActions = confirmSensitiveActions,
                    memoryOptIn = memoryOptIn,
                    wakePhraseEnabled = wakePhraseEnabled
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update settings", e)
            }
        }
    }

    fun saveMemoryItem(title: String, content: String, category: String, approved: Boolean = true, existingId: String? = null) {
        viewModelScope.launch {
            try {
                firestoreRepo.saveMemory(currentUserId, title, content, category, approved, existingId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save memory", e)
            }
        }
    }

    fun deleteMemoryItem(memoryId: String) {
        viewModelScope.launch {
            try {
                firestoreRepo.deleteMemory(currentUserId, memoryId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete memory", e)
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            try {
                firestoreRepo.clearConversations(currentUserId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear conversations", e)
            }
        }
    }

    fun clearVoiceHistory() {
        viewModelScope.launch {
            try {
                firestoreRepo.clearVoiceCommands(currentUserId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear voice history", e)
            }
        }
    }

    fun refreshNetwork() {
        networkGuardian.refreshState()
    }

    fun updateManifestUrlInput(url: String) {
        _versionUpdateState.value = _versionUpdateState.value.copy(
            manifestUrl = url,
            checkError = null
        )
    }

    fun checkForAppUpdates(explicitUrl: String? = null) {
        val targetUrl = (explicitUrl ?: _versionUpdateState.value.manifestUrl).trim()
        if (_versionUpdateState.value.isChecking) return
        viewModelScope.launch {
            networkGuardian.saveUpdateManifestUrl(targetUrl)
            _versionUpdateState.value = _versionUpdateState.value.copy(
                manifestUrl = targetUrl,
                isChecking = true,
                checkError = null,
                downgradeBlockedReason = null,
                statusMessage = "Checking remote version JSON..."
            )
            val checkedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val installed = versionUpdateManager.getInstalledVersionInfo()
            when (val outcome = versionUpdateManager.fetchRemoteVersionManifest(targetUrl)) {
                is UpdateCheckOutcome.UpdateAvailable -> {
                    val m = outcome.manifest
                    VersionUpdateManager.showUpdateAvailableNotification(appContext, m)
                    _versionUpdateState.value = _versionUpdateState.value.copy(
                        installed = installed,
                        isChecking = false,
                        lastCheckedLabel = checkedAt,
                        remoteManifest = m,
                        updateAvailable = true,
                        downgradeBlockedReason = null,
                        checkError = null,
                        versionHistory = versionUpdateManager.mergeVersionHistory(m, installed.versionCode),
                        statusMessage = "Update available: v${m.latestVersionName} (Build ${m.latestVersionCode})"
                    )
                }
                is UpdateCheckOutcome.UpToDate -> {
                    val m = outcome.manifest
                    _versionUpdateState.value = _versionUpdateState.value.copy(
                        installed = installed,
                        isChecking = false,
                        lastCheckedLabel = checkedAt,
                        remoteManifest = m,
                        updateAvailable = false,
                        downgradeBlockedReason = null,
                        checkError = null,
                        versionHistory = versionUpdateManager.mergeVersionHistory(m, installed.versionCode),
                        statusMessage = "Voice Guardian AI is up to date (v${installed.versionName})."
                    )
                }
                is UpdateCheckOutcome.DowngradeBlocked -> {
                    val m = outcome.manifest
                    _versionUpdateState.value = _versionUpdateState.value.copy(
                        installed = installed,
                        isChecking = false,
                        lastCheckedLabel = checkedAt,
                        remoteManifest = m,
                        updateAvailable = false,
                        downgradeBlockedReason = outcome.reason,
                        checkError = null,
                        versionHistory = versionUpdateManager.mergeVersionHistory(m, installed.versionCode),
                        statusMessage = outcome.reason
                    )
                }
                is UpdateCheckOutcome.Error -> {
                    _versionUpdateState.value = _versionUpdateState.value.copy(
                        installed = installed,
                        isChecking = false,
                        lastCheckedLabel = checkedAt,
                        updateAvailable = false,
                        checkError = outcome.message,
                        statusMessage = outcome.message
                    )
                }
            }
        }
    }

    fun downloadApkUpdate() {
        val manifest = _versionUpdateState.value.remoteManifest ?: return
        if (_versionUpdateState.value.isDownloading) return
        viewModelScope.launch {
            _versionUpdateState.value = _versionUpdateState.value.copy(
                isDownloading = true,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                totalBytes = 0L,
                verificationStatus = ApkVerificationStatus.NotDownloaded,
                statusMessage = "Downloading v${manifest.latestVersionName} APK..."
            )
            val result = versionUpdateManager.downloadAndVerifyApk(manifest) { progress, downloaded, total ->
                _versionUpdateState.value = _versionUpdateState.value.copy(
                    downloadProgress = progress,
                    downloadedBytes = downloaded,
                    totalBytes = total
                )
            }
            val msg = when (result) {
                is ApkVerificationStatus.Verified ->
                    "SHA-256 verified (${result.sha256.take(12)}...). Ready to install v${result.apkVersionName} without losing data."
                is ApkVerificationStatus.Failed -> result.reason
                ApkVerificationStatus.NotDownloaded -> "APK download did not complete."
            }
            _versionUpdateState.value = _versionUpdateState.value.copy(
                isDownloading = false,
                verificationStatus = result,
                statusMessage = msg
            )
        }
    }

    fun installVerifiedUpdate(context: Context): InstallLaunchResult {
        val manifest = _versionUpdateState.value.remoteManifest
            ?: return InstallLaunchResult.Blocked("Check for updates first.")
        val verified = _versionUpdateState.value.verificationStatus as? ApkVerificationStatus.Verified
            ?: return InstallLaunchResult.Blocked("Download and verify the APK SHA-256 checksum first.")

        val launchResult = versionUpdateManager.launchApkInstaller(
            context = context,
            apkFile = verified.file,
            expectedSha256 = manifest.sha256
        )
        when (launchResult) {
            InstallLaunchResult.LaunchedInstaller -> {
                _versionUpdateState.value = _versionUpdateState.value.copy(
                    statusMessage = "Android Package Installer opened. User data and settings will be preserved."
                )
            }
            is InstallLaunchResult.NeedsUnknownSourcesPermission -> {
                _versionUpdateState.value = _versionUpdateState.value.copy(
                    statusMessage = "Allow 'Install unknown apps' for Voice Guardian AI to continue."
                )
            }
            is InstallLaunchResult.Blocked -> {
                _versionUpdateState.value = _versionUpdateState.value.copy(
                    verificationStatus = ApkVerificationStatus.Failed(launchResult.reason),
                    statusMessage = launchResult.reason
                )
            }
        }
        return launchResult
    }

    private fun unwrapAndLog(op: String, error: Throwable) {
        val fsErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "$op failed", fsErr ?: error)
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
    }

    companion object {
        private const val TAG = "JarvisCoreVM"

        fun provideFactory(
            appContext: Context,
            currentUserId: String,
            displayName: String,
            firestoreRepo: JarvisFirestoreRepository,
            aiRepo: JarvisAiRepository,
            networkGuardian: NetworkGuardian,
            voiceEngine: VoiceGuardianEngine,
            versionUpdateManager: VersionUpdateManager
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                JarvisCoreViewModel(
                    appContext = appContext.applicationContext,
                    currentUserId = currentUserId,
                    displayName = displayName,
                    firestoreRepo = firestoreRepo,
                    aiRepo = aiRepo,
                    networkGuardian = networkGuardian,
                    voiceEngine = voiceEngine,
                    versionUpdateManager = versionUpdateManager
                )
            }
        }
    }
}
