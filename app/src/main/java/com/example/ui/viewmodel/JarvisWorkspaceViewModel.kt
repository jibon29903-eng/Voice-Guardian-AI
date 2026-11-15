package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.NetworkGuardian
import com.example.data.model.ForexQuote
import com.example.data.model.ForexWatchItem
import com.example.data.model.GeneratedImageItem
import com.example.data.model.JarvisLanguage
import com.example.data.model.ResearchBrief
import com.example.data.model.StudioProject
import com.example.data.model.UiState
import com.example.data.repository.ForexRepository
import com.example.data.repository.JarvisAiRepository
import com.example.data.repository.JarvisFirestoreRepository
import com.example.data.repository.ResearchRepository
import com.example.data.repository.WebSearchHit
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ResearchResultView(
    val query: String,
    val summary: String,
    val verificationNotes: String,
    val hits: List<WebSearchHit>
)

class JarvisWorkspaceViewModel(
    private val currentUserId: String,
    private val firestoreRepo: JarvisFirestoreRepository,
    private val aiRepo: JarvisAiRepository,
    private val forexRepo: ForexRepository,
    private val researchRepo: ResearchRepository,
    private val networkGuardian: NetworkGuardian
) : ViewModel() {

    val studioProjects: StateFlow<List<StudioProject>> = firestoreRepo.observeStudioProjects(currentUserId)
        .catch { e -> unwrapAndLog("observeStudioProjects", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val researchBriefs: StateFlow<List<ResearchBrief>> = firestoreRepo.observeResearchBriefs(currentUserId)
        .catch { e -> unwrapAndLog("observeResearchBriefs", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val forexWatchlist: StateFlow<List<ForexWatchItem>> = firestoreRepo.observeForexWatchlist(currentUserId)
        .catch { e -> unwrapAndLog("observeForexWatchlist", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _activeProject = MutableStateFlow<StudioProject?>(null)
    val activeProject: StateFlow<StudioProject?> = _activeProject.asStateFlow()

    private val _studioState = MutableStateFlow<UiState<StudioProject>>(UiState.Idle)
    val studioState: StateFlow<UiState<StudioProject>> = _studioState.asStateFlow()

    private val _consoleErrors = MutableStateFlow<List<String>>(emptyList())
    val consoleErrors: StateFlow<List<String>> = _consoleErrors.asStateFlow()

    private val _lastCodeChanges = MutableStateFlow<String?>(null)
    val lastCodeChanges: StateFlow<String?> = _lastCodeChanges.asStateFlow()

    private val _researchState = MutableStateFlow<UiState<ResearchResultView>>(UiState.Idle)
    val researchState: StateFlow<UiState<ResearchResultView>> = _researchState.asStateFlow()

    private val _imageGenState = MutableStateFlow<UiState<GeneratedImageItem>>(UiState.Idle)
    val imageGenState: StateFlow<UiState<GeneratedImageItem>> = _imageGenState.asStateFlow()

    private val _imageGallery = MutableStateFlow<List<GeneratedImageItem>>(emptyList())
    val imageGallery: StateFlow<List<GeneratedImageItem>> = _imageGallery.asStateFlow()

    private val _forexQuotes = MutableStateFlow<List<ForexQuote>>(emptyList())
    val forexQuotes: StateFlow<List<ForexQuote>> = _forexQuotes.asStateFlow()

    private val _forexLoading = MutableStateFlow(false)
    val forexLoading: StateFlow<Boolean> = _forexLoading.asStateFlow()

    private val _screenAnalysisState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val screenAnalysisState: StateFlow<UiState<String>> = _screenAnalysisState.asStateFlow()

    private val _whiteboardState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val whiteboardState: StateFlow<UiState<String>> = _whiteboardState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                firestoreRepo.seedDefaultForexWatchlistIfEmpty(currentUserId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed seeding default forex pairs", e)
            }
        }

        viewModelScope.launch {
            forexWatchlist.collect { items ->
                if (items.isNotEmpty()) {
                    refreshForexRates(items)
                }
            }
        }

        viewModelScope.launch {
            networkGuardian.imageGalleryJsonFlow.collect { json ->
                if (json.isNotBlank() && _imageGallery.value.isEmpty()) {
                    _imageGallery.value = aiRepo.deserializeGalleryItems(json)
                }
            }
        }
    }

    // --- AI STUDIO ---
    fun selectProject(project: StudioProject) {
        _activeProject.value = project
        _consoleErrors.value = emptyList()
        _lastCodeChanges.value = null
        _studioState.value = UiState.Success(project)
    }

    fun updateActiveProjectCode(html: String, css: String, js: String) {
        val current = _activeProject.value ?: return
        _activeProject.value = current.copy(htmlCode = html, cssCode = css, jsCode = js)
    }

    fun addWebViewConsoleError(msg: String) {
        if (msg.isBlank()) return
        val existing = _consoleErrors.value
        if (!existing.contains(msg)) {
            _consoleErrors.value = (existing + msg).takeLast(12)
        }
    }

    fun clearConsoleErrors() {
        _consoleErrors.value = emptyList()
    }

    fun generateWebsite(description: String, framework: String, language: JarvisLanguage) {
        if (description.isBlank()) return
        viewModelScope.launch {
            _studioState.value = UiState.Loading
            _consoleErrors.value = emptyList()
            _lastCodeChanges.value = null
            try {
                val generated = aiRepo.generateWebsiteProject(description, framework, language)
                val draft = StudioProject(
                    userId = currentUserId,
                    title = generated.title,
                    prompt = description,
                    framework = generated.framework,
                    htmlCode = generated.htmlCode,
                    cssCode = generated.cssCode,
                    jsCode = generated.jsCode,
                    lastDiagnostic = generated.diagnostic
                )
                val id = firestoreRepo.saveStudioProject(currentUserId, draft)
                val saved = draft.copy(id = id)
                _activeProject.value = saved
                _studioState.value = UiState.Success(saved)
            } catch (e: TimeoutCancellationException) {
                _studioState.value = UiState.Error("Website generation timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Studio generation failed", e)
                _studioState.value = UiState.Error(e.localizedMessage ?: "Failed to generate website.")
            }
        }
    }

    fun detectAndFixActiveProject() {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            _studioState.value = UiState.Loading
            try {
                val errorLog = if (_consoleErrors.value.isEmpty()) {
                    "No runtime exceptions logged yet. Perform static HTML/CSS/JS audit, accessibility check, and mobile layout check."
                } else {
                    _consoleErrors.value.joinToString("\n")
                }
                val fix = aiRepo.diagnoseAndFixWebsite(
                    htmlCode = proj.htmlCode,
                    cssCode = proj.cssCode,
                    jsCode = proj.jsCode,
                    errorLog = errorLog
                )
                val updated = proj.copy(
                    htmlCode = fix.fixedHtml,
                    cssCode = fix.fixedCss,
                    jsCode = fix.fixedJs,
                    lastDiagnostic = fix.issueSummary
                )
                val savedId = firestoreRepo.saveStudioProject(currentUserId, updated)
                val finalProj = updated.copy(id = savedId)
                _activeProject.value = finalProj
                _lastCodeChanges.value = fix.changeNotes
                _consoleErrors.value = emptyList()
                _studioState.value = UiState.Success(finalProj)
            } catch (e: TimeoutCancellationException) {
                _studioState.value = UiState.Error("Code diagnostic timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fix project failed", e)
                _studioState.value = UiState.Error(e.localizedMessage ?: "Code fix failed.")
            }
        }
    }

    fun deleteStudioProject(projectId: String) {
        viewModelScope.launch {
            try {
                firestoreRepo.deleteStudioProject(currentUserId, projectId)
                if (_activeProject.value?.id == projectId) {
                    _activeProject.value = null
                    _studioState.value = UiState.Idle
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Delete project failed", e)
            }
        }
    }

    // --- DEEP RESEARCH ---
    fun runDeepResearch(query: String, language: JarvisLanguage) {
        val clean = query.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _researchState.value = UiState.Loading
            try {
                val hits = researchRepo.searchWebSources(clean, language)
                val synthesized = aiRepo.synthesizeResearch(clean, hits, language)
                firestoreRepo.saveResearchBrief(
                    userId = currentUserId,
                    query = clean,
                    summary = synthesized.summary,
                    verificationNotes = synthesized.verificationNotes,
                    sourceTitles = hits.map { "${it.title} (${it.timestamp})" },
                    sourceUrls = hits.map { it.url }
                )
                _researchState.value = UiState.Success(
                    ResearchResultView(
                        query = clean,
                        summary = synthesized.summary,
                        verificationNotes = synthesized.verificationNotes,
                        hits = hits
                    )
                )
            } catch (e: TimeoutCancellationException) {
                _researchState.value = UiState.Error("Deep research timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Deep research failed", e)
                _researchState.value = UiState.Error(e.localizedMessage ?: "Deep research failed.")
            }
        }
    }

    // --- IMAGE CREATOR ---
    fun generateOrEditImage(prompt: String, sourceBitmap: Bitmap? = null) {
        val clean = prompt.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _imageGenState.value = UiState.Loading
            try {
                val item = aiRepo.generateOrEditImage(clean, sourceBitmap)
                val updatedGallery = listOf(item) + _imageGallery.value
                _imageGallery.value = updatedGallery
                networkGuardian.saveImageGalleryJson(aiRepo.serializeGalleryItems(updatedGallery))
                _imageGenState.value = UiState.Success(item)
            } catch (e: TimeoutCancellationException) {
                _imageGenState.value = UiState.Error("Image generation timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _imageGenState.value = UiState.Error(e.localizedMessage ?: "Image generation failed.")
            }
        }
    }

    fun loadBitmap(filePath: String): Bitmap? = aiRepo.loadBitmapFromFile(filePath)

    // --- FOREX WATCHLIST ---
    fun refreshForexRates(items: List<ForexWatchItem> = forexWatchlist.value) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _forexLoading.value = true
            try {
                val quotes = forexRepo.fetchQuotesForWatchlist(items)
                _forexQuotes.value = quotes
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Forex refresh failed", e)
            } finally {
                _forexLoading.value = false
            }
        }
    }

    fun addOrUpdateWatchPair(base: String, quote: String, alertRate: Double, direction: String) {
        viewModelScope.launch {
            try {
                firestoreRepo.upsertForexWatchItem(currentUserId, base, quote, alertRate, direction)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Add forex pair failed", e)
            }
        }
    }

    fun removeWatchPair(pairId: String) {
        viewModelScope.launch {
            try {
                firestoreRepo.deleteForexWatchItem(currentUserId, pairId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Remove forex pair failed", e)
            }
        }
    }

    // --- SCREEN GUARDIAN ---
    fun analyzeScreenImage(bitmap: Bitmap, question: String, language: JarvisLanguage) {
        viewModelScope.launch {
            _screenAnalysisState.value = UiState.Loading
            try {
                val answer = aiRepo.analyzeImageWithPrompt(
                    bitmap = bitmap,
                    question = question.ifBlank { "Explain this screen and its main interactive controls." },
                    language = language,
                    contextType = "screen"
                )
                _screenAnalysisState.value = UiState.Success(answer)
            } catch (e: TimeoutCancellationException) {
                _screenAnalysisState.value = UiState.Error("Screen analysis timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Screen analysis failed", e)
                _screenAnalysisState.value = UiState.Error(e.localizedMessage ?: "Screen analysis failed.")
            }
        }
    }

    // --- WHITEBOARD ---
    fun analyzeWhiteboardCanvas(bitmap: Bitmap, prompt: String, language: JarvisLanguage) {
        viewModelScope.launch {
            _whiteboardState.value = UiState.Loading
            try {
                val answer = aiRepo.analyzeImageWithPrompt(
                    bitmap = bitmap,
                    question = prompt.ifBlank {
                        "Explain this diagram or sketch step by step and turn it into a clear educational lesson."
                    },
                    language = language,
                    contextType = "whiteboard"
                )
                _whiteboardState.value = UiState.Success(answer)
            } catch (e: TimeoutCancellationException) {
                _whiteboardState.value = UiState.Error("Whiteboard AI explanation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Whiteboard analysis failed", e)
                _whiteboardState.value = UiState.Error(e.localizedMessage ?: "Whiteboard analysis failed.")
            }
        }
    }

    private fun unwrapAndLog(op: String, error: Throwable) {
        val fsErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "$op failed", fsErr ?: error)
    }

    companion object {
        private const val TAG = "JarvisWorkspaceVM"

        fun provideFactory(
            currentUserId: String,
            firestoreRepo: JarvisFirestoreRepository,
            aiRepo: JarvisAiRepository,
            forexRepo: ForexRepository,
            researchRepo: ResearchRepository,
            networkGuardian: NetworkGuardian
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                JarvisWorkspaceViewModel(
                    currentUserId = currentUserId,
                    firestoreRepo = firestoreRepo,
                    aiRepo = aiRepo,
                    forexRepo = forexRepo,
                    researchRepo = researchRepo,
                    networkGuardian = networkGuardian
                )
            }
        }
    }
}
