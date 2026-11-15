package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.signOutFromJarvis
import com.example.core.NetworkGuardian
import com.example.data.repository.ForexRepository
import com.example.data.repository.JarvisAiRepository
import com.example.data.repository.JarvisFirestoreRepository
import com.example.data.repository.ResearchRepository
import com.example.ui.components.NetworkStatusPill
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DeepResearchScreen
import com.example.ui.screens.ForexWatchlistScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageCreatorScreen
import com.example.ui.screens.ScreenGuardianScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VoiceGuardianScreen
import com.example.ui.screens.WhiteboardScreen
import com.example.ui.theme.JarvisDeepBlack
import com.example.ui.theme.JarvisElectricCyan
import com.example.ui.theme.JarvisHoloTeal
import com.example.ui.theme.JarvisMidnightNavy
import com.example.ui.viewmodel.JarvisCoreViewModel
import com.example.ui.viewmodel.JarvisWorkspaceViewModel
import com.example.update.VersionUpdateManager
import com.example.voice.VoiceGuardianEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object JarvisRoutes {
    const val HOME = "home"
    const val VOICE_GUARDIAN = "voice_guardian"
    const val AI_CHAT = "ai_chat"
    const val AI_STUDIO = "ai_studio"
    const val DEEP_RESEARCH = "deep_research"
    const val IMAGE_CREATOR = "image_creator"
    const val FOREX_WATCHLIST = "forex_watchlist"
    const val SCREEN_GUARDIAN = "screen_guardian"
    const val WHITEBOARD = "whiteboard"
    const val SETTINGS = "settings"
}

data class JarvisNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val ALL_JARVIS_DESTINATIONS = listOf(
    JarvisNavDestination(JarvisRoutes.HOME, "Home", Icons.Filled.Home),
    JarvisNavDestination(JarvisRoutes.VOICE_GUARDIAN, "Voice", Icons.Filled.Mic),
    JarvisNavDestination(JarvisRoutes.AI_CHAT, "AI Chat", Icons.AutoMirrored.Filled.Chat),
    JarvisNavDestination(JarvisRoutes.AI_STUDIO, "AI Studio", Icons.Filled.Code),
    JarvisNavDestination(JarvisRoutes.DEEP_RESEARCH, "Research", Icons.Filled.Public),
    JarvisNavDestination(JarvisRoutes.IMAGE_CREATOR, "Images", Icons.Filled.Image),
    JarvisNavDestination(JarvisRoutes.FOREX_WATCHLIST, "Forex", Icons.AutoMirrored.Filled.TrendingUp),
    JarvisNavDestination(JarvisRoutes.SCREEN_GUARDIAN, "Screen", Icons.Filled.Screenshot),
    JarvisNavDestination(JarvisRoutes.WHITEBOARD, "Board", Icons.Filled.Brush),
    JarvisNavDestination(JarvisRoutes.SETTINGS, "Settings", Icons.Filled.Settings)
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun JarvisAppRoot(
    networkGuardian: NetworkGuardian,
    initialRouteExtra: String? = null
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        androidx.compose.runtime.key(user.uid) {
            JarvisAuthenticatedShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: "Commander",
                userEmail = user.email,
                networkGuardian = networkGuardian,
                initialRouteExtra = initialRouteExtra
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JarvisAuthenticatedShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String?,
    networkGuardian: NetworkGuardian,
    initialRouteExtra: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val databaseId = remember { context.applicationContext.getString(R.string.firestore_database_id) }
    val firestore = remember(databaseId) { FirebaseFirestore.getInstance(databaseId) }
    val firestoreRepo = remember(firestore) { JarvisFirestoreRepository(firestore) }
    val aiRepo = remember { JarvisAiRepository(context.applicationContext) }
    val forexRepo = remember { ForexRepository(networkGuardian) }
    val researchRepo = remember { ResearchRepository(networkGuardian) }
    val versionUpdateManager = remember { VersionUpdateManager(context.applicationContext, networkGuardian) }

    // Late-bound callback holder so VoiceGuardianEngine routes transcripts straight into CoreViewModel
    val transcriptRouter = remember { mutableStateOf<(String, Boolean) -> Unit>({ _, _ -> }) }
    val voiceEngine = remember {
        VoiceGuardianEngine(context.applicationContext) { transcript, wake ->
            transcriptRouter.value(transcript, wake)
        }
    }

    val coreVm: JarvisCoreViewModel = viewModel(
        key = "core_$currentUserId",
        factory = JarvisCoreViewModel.provideFactory(
            appContext = context.applicationContext,
            currentUserId = currentUserId,
            displayName = userDisplayName,
            firestoreRepo = firestoreRepo,
            aiRepo = aiRepo,
            networkGuardian = networkGuardian,
            voiceEngine = voiceEngine,
            versionUpdateManager = versionUpdateManager
        )
    )
    transcriptRouter.value = { t, w -> coreVm.handleSpokenTranscript(t, w) }

    val workspaceVm: JarvisWorkspaceViewModel = viewModel(
        key = "workspace_$currentUserId",
        factory = JarvisWorkspaceViewModel.provideFactory(
            currentUserId = currentUserId,
            firestoreRepo = firestoreRepo,
            aiRepo = aiRepo,
            forexRepo = forexRepo,
            researchRepo = researchRepo,
            networkGuardian = networkGuardian
        )
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: JarvisRoutes.HOME
    var showModulesSheet by remember { mutableStateOf(false) }

    fun navigateTo(route: String) {
        if (route == currentRoute) return
        navController.navigate(route) {
            popUpTo(JarvisRoutes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(initialRouteExtra) {
        if (initialRouteExtra == JarvisRoutes.SETTINGS) {
            navigateTo(JarvisRoutes.SETTINGS)
        }
    }

    if (currentRoute != JarvisRoutes.HOME) {
        BackHandler {
            navController.popBackStack(JarvisRoutes.HOME, inclusive = false)
        }
    }

    val networkState by coreVm.networkState.collectAsStateWithLifecycle()
    val voiceState by coreVm.voiceState.collectAsStateWithLifecycle()
    val userProfile by coreVm.userProfile.collectAsStateWithLifecycle()
    val conversations by coreVm.conversations.collectAsStateWithLifecycle()
    val voiceCommands by coreVm.voiceCommands.collectAsStateWithLifecycle()
    val memories by coreVm.memories.collectAsStateWithLifecycle()
    val chatState by coreVm.chatState.collectAsStateWithLifecycle()
    val selectedLanguage by coreVm.selectedLanguage.collectAsStateWithLifecycle()
    val versionUpdateState by coreVm.versionUpdateState.collectAsStateWithLifecycle()

    val studioProjects by workspaceVm.studioProjects.collectAsStateWithLifecycle()
    val activeProject by workspaceVm.activeProject.collectAsStateWithLifecycle()
    val studioState by workspaceVm.studioState.collectAsStateWithLifecycle()
    val consoleErrors by workspaceVm.consoleErrors.collectAsStateWithLifecycle()
    val lastCodeChanges by workspaceVm.lastCodeChanges.collectAsStateWithLifecycle()

    val researchState by workspaceVm.researchState.collectAsStateWithLifecycle()
    val researchBriefs by workspaceVm.researchBriefs.collectAsStateWithLifecycle()
    val imageGenState by workspaceVm.imageGenState.collectAsStateWithLifecycle()
    val imageGallery by workspaceVm.imageGallery.collectAsStateWithLifecycle()
    val forexWatchlist by workspaceVm.forexWatchlist.collectAsStateWithLifecycle()
    val forexQuotes by workspaceVm.forexQuotes.collectAsStateWithLifecycle()
    val forexLoading by workspaceVm.forexLoading.collectAsStateWithLifecycle()
    val screenAnalysisState by workspaceVm.screenAnalysisState.collectAsStateWithLifecycle()
    val whiteboardState by workspaceVm.whiteboardState.collectAsStateWithLifecycle()

    val confirmSensitive = userProfile?.confirmSensitiveActions ?: true

    if (showModulesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModulesSheet = false },
            containerColor = JarvisMidnightNavy
        ) {
            Text(
                text = "ALL JARVIS MODULES",
                style = MaterialTheme.typography.titleMedium,
                color = JarvisElectricCyan,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(320.dp)
            ) {
                items(ALL_JARVIS_DESTINATIONS, key = { it.route }) { dest ->
                    val selected = dest.route == currentRoute
                    Surface(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                showModulesSheet = false
                                navigateTo(dest.route)
                            }
                            .testTag("sheet_nav_${dest.route}"),
                        color = if (selected) JarvisElectricCyan.copy(alpha = 0.2f) else JarvisDeepBlack,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.label,
                                tint = if (selected) JarvisElectricCyan else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = dest.label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBlack)
    ) {
        val isWideScreen = maxWidth >= 600.dp

        val navHostContent: @Composable (Modifier) -> Unit = { mod ->
            NavHost(
                navController = navController,
                startDestination = JarvisRoutes.HOME,
                modifier = mod
            ) {
                composable(JarvisRoutes.HOME) {
                    HomeScreen(
                        displayName = userProfile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName,
                        language = selectedLanguage,
                        networkState = networkState,
                        voiceState = voiceState,
                        recentMessages = conversations,
                        forexQuotes = forexQuotes,
                        onNavigate = { navigateTo(it) },
                        onRefreshNetwork = { coreVm.refreshNetwork() },
                        onActivateVoice = { navigateTo(JarvisRoutes.VOICE_GUARDIAN) }
                    )
                }
                composable(JarvisRoutes.VOICE_GUARDIAN) {
                    VoiceGuardianScreen(
                        voiceState = voiceState,
                        chatState = chatState,
                        selectedLanguage = selectedLanguage,
                        wakePhraseEnabled = userProfile?.wakePhraseEnabled ?: true,
                        confirmSensitiveActions = confirmSensitive,
                        commandHistory = voiceCommands,
                        onSelectLanguage = { coreVm.setLanguage(it) },
                        onStartListening = { coreVm.startVoiceListening() },
                        onStopListening = { coreVm.stopVoiceListening() },
                        onToggleMute = { coreVm.toggleMute() },
                        onInterrupt = { coreVm.interruptJarvis() },
                        onUpdateDraft = { coreVm.updateDraft(it) },
                        onSubmitTextFallback = {
                            coreVm.sendAssistantMessage(it, source = "voice", speakReply = true)
                        },
                        onClearVoiceHistory = { coreVm.clearVoiceHistory() },
                        onDismissError = { coreVm.clearChatError() }
                    )
                }
                composable(JarvisRoutes.AI_CHAT) {
                    AiChatScreen(
                        messages = conversations,
                        chatState = chatState,
                        selectedLanguage = selectedLanguage,
                        confirmSensitiveActions = confirmSensitive,
                        onSelectLanguage = { coreVm.setLanguage(it) },
                        onUpdateDraft = { coreVm.updateDraft(it) },
                        onSendMessage = { coreVm.sendAssistantMessage(it, source = "chat") },
                        onInterrupt = { coreVm.interruptJarvis() },
                        onSaveMemory = { title, content ->
                            coreVm.saveMemoryItem(title, content, "preference", approved = true)
                        },
                        onClearConversations = { coreVm.clearAllConversations() },
                        onDismissError = { coreVm.clearChatError() }
                    )
                }
                composable(JarvisRoutes.AI_STUDIO) {
                    AiStudioScreen(
                        studioState = studioState,
                        activeProject = activeProject,
                        savedProjects = studioProjects,
                        consoleErrors = consoleErrors,
                        lastCodeChanges = lastCodeChanges,
                        selectedLanguage = selectedLanguage,
                        confirmSensitiveActions = confirmSensitive,
                        onGenerateWebsite = { desc, fw ->
                            workspaceVm.generateWebsite(desc, fw, selectedLanguage)
                        },
                        onSelectProject = { workspaceVm.selectProject(it) },
                        onDetectAndFix = { workspaceVm.detectAndFixActiveProject() },
                        onDeleteProject = { workspaceVm.deleteStudioProject(it) },
                        onConsoleError = { workspaceVm.addWebViewConsoleError(it) },
                        onVoicePromptClick = { navigateTo(JarvisRoutes.VOICE_GUARDIAN) }
                    )
                }
                composable(JarvisRoutes.DEEP_RESEARCH) {
                    DeepResearchScreen(
                        researchState = researchState,
                        savedBriefs = researchBriefs,
                        selectedLanguage = selectedLanguage,
                        onRunResearch = { workspaceVm.runDeepResearch(it, selectedLanguage) }
                    )
                }
                composable(JarvisRoutes.IMAGE_CREATOR) {
                    ImageCreatorScreen(
                        imageGenState = imageGenState,
                        gallery = imageGallery,
                        confirmSensitiveActions = confirmSensitive,
                        loadBitmap = { workspaceVm.loadBitmap(it) },
                        onGenerateOrEdit = { prompt, bmp -> workspaceVm.generateOrEditImage(prompt, bmp) }
                    )
                }
                composable(JarvisRoutes.FOREX_WATCHLIST) {
                    ForexWatchlistScreen(
                        watchlist = forexWatchlist,
                        quotes = forexQuotes,
                        isLoading = forexLoading,
                        onRefresh = { workspaceVm.refreshForexRates() },
                        onAddOrUpdatePair = { b, q, rate, dir ->
                            workspaceVm.addOrUpdateWatchPair(b, q, rate, dir)
                        },
                        onRemovePair = { workspaceVm.removeWatchPair(it) }
                    )
                }
                composable(JarvisRoutes.SCREEN_GUARDIAN) {
                    ScreenGuardianScreen(
                        analysisState = screenAnalysisState,
                        selectedLanguage = selectedLanguage,
                        onAnalyzeScreen = { bmp, q -> workspaceVm.analyzeScreenImage(bmp, q, selectedLanguage) }
                    )
                }
                composable(JarvisRoutes.WHITEBOARD) {
                    WhiteboardScreen(
                        whiteboardState = whiteboardState,
                        selectedLanguage = selectedLanguage,
                        onSelectLanguage = { coreVm.setLanguage(it) },
                        onExplainWhiteboard = { bmp, p -> workspaceVm.analyzeWhiteboardCanvas(bmp, p, selectedLanguage) }
                    )
                }
                composable(JarvisRoutes.SETTINGS) {
                    SettingsScreen(
                        userEmail = userEmail,
                        profile = userProfile,
                        selectedLanguage = selectedLanguage,
                        networkState = networkState,
                        memories = memories,
                        versionState = versionUpdateState,
                        onSelectLanguage = { coreVm.setLanguage(it) },
                        onUpdateSettings = { v, r, c, m, w -> coreVm.updateSettings(v, r, c, m, w) },
                        onSaveMemory = { t, c, cat -> coreVm.saveMemoryItem(t, c, cat, approved = true) },
                        onToggleMemoryApproval = { item ->
                            coreVm.saveMemoryItem(item.title, item.content, item.category, !item.approved, item.id)
                        },
                        onDeleteMemory = { coreVm.deleteMemoryItem(it) },
                        onRefreshNetwork = { coreVm.refreshNetwork() },
                        onUpdateManifestUrl = { coreVm.updateManifestUrlInput(it) },
                        onCheckForUpdates = { coreVm.checkForAppUpdates() },
                        onDownloadUpdate = { coreVm.downloadApkUpdate() },
                        onInstallUpdate = { ctx -> coreVm.installVerifiedUpdate(ctx) },
                        onClearConversations = { coreVm.clearAllConversations() },
                        onSignOut = { signOutFromJarvis(credentialManager, scope) }
                    )
                }
            }
        }

        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = JarvisMidnightNavy,
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    ALL_JARVIS_DESTINATIONS.forEach { dest ->
                        NavigationRailItem(
                            selected = currentRoute == dest.route,
                            onClick = { navigateTo(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label, maxLines = 1) }
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    JarvisTopStatusBar(
                        currentTitle = ALL_JARVIS_DESTINATIONS.firstOrNull { it.route == currentRoute }?.label ?: "Voice Guardian",
                        networkState = networkState,
                        updateAvailable = versionUpdateState.updateAvailable,
                        onOpenUpdates = { navigateTo(JarvisRoutes.SETTINGS) },
                        onRefreshNetwork = { coreVm.refreshNetwork() },
                        onOpenAllModules = { showModulesSheet = true }
                    )
                    navHostContent(Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            Scaffold(
                containerColor = JarvisDeepBlack,
                contentWindowInsets = WindowInsets(0),
                topBar = {
                    JarvisTopStatusBar(
                        currentTitle = ALL_JARVIS_DESTINATIONS.firstOrNull { it.route == currentRoute }?.label ?: "Voice Guardian",
                        networkState = networkState,
                        updateAvailable = versionUpdateState.updateAvailable,
                        onOpenUpdates = { navigateTo(JarvisRoutes.SETTINGS) },
                        onRefreshNetwork = { coreVm.refreshNetwork() },
                        onOpenAllModules = { showModulesSheet = true }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = JarvisMidnightNavy,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        val primaryFour = listOf(
                            ALL_JARVIS_DESTINATIONS[0], // Home
                            ALL_JARVIS_DESTINATIONS[1], // Voice
                            ALL_JARVIS_DESTINATIONS[2], // AI Chat
                            ALL_JARVIS_DESTINATIONS[6]  // Forex
                        )
                        primaryFour.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = { navigateTo(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("bottom_nav_${dest.route}")
                            )
                        }
                        NavigationBarItem(
                            selected = currentRoute !in primaryFour.map { it.route },
                            onClick = { showModulesSheet = true },
                            icon = { Icon(Icons.Filled.Apps, contentDescription = "All Modules") },
                            label = { Text("Modules") },
                            modifier = Modifier.testTag("bottom_nav_modules")
                        )
                    }
                }
            ) { innerPadding ->
                navHostContent(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun JarvisTopStatusBar(
    currentTitle: String,
    networkState: com.example.core.NetworkGuardianState,
    updateAvailable: Boolean,
    onOpenUpdates: () -> Unit,
    onRefreshNetwork: () -> Unit,
    onOpenAllModules: () -> Unit
) {
    Surface(
        color = JarvisMidnightNavy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "JARVIS • ${currentTitle.uppercase()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisElectricCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (updateAvailable) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = JarvisHoloTeal.copy(alpha = 0.2f),
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onOpenUpdates)
                            .testTag("top_update_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = "Update available",
                                tint = JarvisHoloTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Update", style = MaterialTheme.typography.labelSmall, color = JarvisHoloTeal)
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                NetworkStatusPill(
                    networkState = networkState,
                    onClick = onRefreshNetwork
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = JarvisDeepBlack,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onOpenAllModules)
                        .testTag("top_modules_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Apps,
                        contentDescription = "Open all 10 modules",
                        tint = JarvisElectricCyan,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }
            }
        }
    }
}
