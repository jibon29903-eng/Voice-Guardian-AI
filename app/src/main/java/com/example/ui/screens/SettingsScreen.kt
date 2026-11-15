package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.core.NetworkGuardianState
import com.example.data.model.JarvisLanguage
import com.example.data.model.UserMemoryItem
import com.example.data.model.UserProfile
import com.example.ui.components.JarvisHudCard
import com.example.ui.components.LanguageSelectorRow
import com.example.ui.components.NetworkStatusPill
import com.example.ui.components.SensitiveConfirmationDialog
import com.example.ui.theme.JarvisDeepBlack
import com.example.ui.theme.JarvisElectricCyan
import com.example.ui.theme.JarvisErrorRed
import com.example.ui.theme.JarvisHoloTeal
import com.example.ui.theme.JarvisMidnightNavy
import com.example.ui.theme.JarvisAmberAlert
import com.example.update.ApkVerificationStatus
import com.example.update.InstallLaunchResult
import com.example.update.VersionUpdateManager
import com.example.update.VersionUpdateUiState
import java.util.Locale

@Composable
fun SettingsScreen(
    userEmail: String?,
    profile: UserProfile?,
    selectedLanguage: JarvisLanguage,
    networkState: NetworkGuardianState,
    memories: List<UserMemoryItem>,
    versionState: VersionUpdateUiState,
    onSelectLanguage: (JarvisLanguage) -> Unit,
    onUpdateSettings: (
        voiceResponseEnabled: Boolean?,
        speechRate: Double?,
        confirmSensitiveActions: Boolean?,
        memoryOptIn: Boolean?,
        wakePhraseEnabled: Boolean?
    ) -> Unit,
    onSaveMemory: (title: String, content: String, category: String) -> Unit,
    onToggleMemoryApproval: (UserMemoryItem) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onRefreshNetwork: () -> Unit,
    onUpdateManifestUrl: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: (Context) -> InstallLaunchResult,
    onClearConversations: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var showAddMemoryDialog by remember { mutableStateOf(false) }
    var memTitle by remember { mutableStateOf("") }
    var memContent by remember { mutableStateOf("") }
    var memCategory by remember { mutableStateOf("preference") }
    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showJsonSchema by remember { mutableStateOf(false) }
    var showUnknownSourcesDialog by remember { mutableStateOf<Intent?>(null) }
    var notificationsGranted by remember {
        mutableStateOf(VersionUpdateManager.hasNotificationPermission(context))
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        if (granted && versionState.updateAvailable && versionState.remoteManifest != null) {
            VersionUpdateManager.showUpdateAvailableNotification(context, versionState.remoteManifest)
        }
    }

    if (showUnknownSourcesDialog != null) {
        AlertDialog(
            onDismissRequest = { showUnknownSourcesDialog = null },
            containerColor = JarvisMidnightNavy,
            title = { Text("Allow App Updates From This Source") },
            text = {
                Text(
                    "Android requires permission to install verified APK updates from ${versionState.installed.appName}. " +
                        "Your personal memories, preferences, and signed-in account data will be preserved during the update."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = showUnknownSourcesDialog
                        showUnknownSourcesDialog = null
                        if (intent != null) context.startActivity(intent)
                    }
                ) { Text("Open Permission Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showUnknownSourcesDialog = null }) { Text("Cancel") }
            }
        )
    }

    if (showAddMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddMemoryDialog = false },
            containerColor = JarvisMidnightNavy,
            title = { Text("Add Approved Memory / Shortcut") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = memTitle,
                        onValueChange = { memTitle = it },
                        label = { Text("Title / Shortcut Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = memContent,
                        onValueChange = { memContent = it },
                        label = { Text("Preference or Instruction") },
                        minLines = 2
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("preference", "shortcut", "command").forEach { cat ->
                            FilterChip(
                                selected = memCategory == cat,
                                onClick = { memCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (memTitle.isNotBlank() && memContent.isNotBlank()) {
                            onSaveMemory(memTitle.trim(), memContent.trim(), memCategory)
                        }
                        memTitle = ""
                        memContent = ""
                        showAddMemoryDialog = false
                    }
                ) { Text("Approve & Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearDataConfirm) {
        SensitiveConfirmationDialog(
            title = "Erase Conversation History?",
            message = "This permanently deletes all saved conversation logs in your Firestore account.",
            confirmLabel = "Erase All",
            onConfirm = {
                showClearDataConfirm = false
                onClearConversations()
            },
            onDismiss = { showClearDataConfirm = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SMART PERSONALIZATION & PRIVACY", style = MaterialTheme.typography.titleMedium, color = JarvisElectricCyan)
                        Text(userEmail ?: "Signed-in Google Account", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onSignOut, modifier = Modifier.testTag("sign_out_button")) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        // --- VERSION MANAGEMENT & SOFTWARE UPDATES ---
        item {
            JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = JarvisElectricCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("VERSION MANAGEMENT & UPDATES", style = MaterialTheme.typography.labelMedium, color = JarvisElectricCyan)
                    }
                    if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        TextButton(
                            onClick = { notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.testTag("enable_update_alerts_button")
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Alerts")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Current vs Latest Version comparison row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = JarvisDeepBlack,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("CURRENT VERSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "v${versionState.installed.versionName} (${versionState.installed.versionCode})",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisElectricCyan,
                                modifier = Modifier.testTag("current_version_text")
                            )
                        }
                    }
                    Surface(
                        color = JarvisDeepBlack,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        val latestLabel = versionState.remoteManifest?.let {
                            "v${it.latestVersionName} (${it.latestVersionCode})"
                        } ?: "Not checked"
                        val latestColor = when {
                            versionState.updateAvailable -> JarvisHoloTeal
                            versionState.downgradeBlockedReason != null -> JarvisAmberAlert
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("LATEST VERSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = latestLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = latestColor,
                                modifier = Modifier.testTag("latest_version_text")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Last checked: ${versionState.lastCheckedLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Update Available Banner
                AnimatedVisibility(visible = versionState.updateAvailable && versionState.remoteManifest != null) {
                    val manifest = versionState.remoteManifest
                    if (manifest != null) {
                        Surface(
                            color = JarvisHoloTeal.copy(alpha = 0.14f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .testTag("update_available_banner")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = JarvisHoloTeal, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "UPDATE AVAILABLE: v${manifest.latestVersionName} (Build ${manifest.latestVersionCode})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = JarvisHoloTeal
                                    )
                                }
                                if (manifest.releaseNotes.isNotBlank()) {
                                    Text(manifest.releaseNotes, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    text = "SHA-256: ${manifest.sha256.take(16)}... • Anti-downgrade & data preservation active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = versionState.manifestUrl,
                    onValueChange = onUpdateManifestUrl,
                    label = { Text("Remote Version JSON URL (HTTPS)") },
                    placeholder = { Text("https://your-domain.com/voice-guardian/version.json") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_manifest_url_input")
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onCheckForUpdates,
                        enabled = !versionState.isChecking && !versionState.isDownloading,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("check_updates_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (versionState.isChecking) "Checking..." else "Check Updates")
                    }
                    TextButton(onClick = { showJsonSchema = !showJsonSchema }) {
                        Text(if (showJsonSchema) "Hide Schema" else "JSON Schema")
                    }
                }

                AnimatedVisibility(visible = showJsonSchema) {
                    Surface(
                        color = JarvisDeepBlack,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = """
                                {
                                  "packageName": "com.voiceguardian.jarvis",
                                  "latestVersionName": "1.1.0",
                                  "latestVersionCode": 2,
                                  "minSdk": 24,
                                  "apkUrl": "https://.../VoiceGuardianAI-1.1.0.apk",
                                  "sha256": "<64-char hex checksum>",
                                  "releaseDate": "2026-12-01",
                                  "releaseNotes": "Highlights...",
                                  "history": [ ... ]
                                }
                            """.trimIndent(),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = JarvisElectricCyan,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (versionState.isDownloading) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { versionState.downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val pct = (versionState.downloadProgress * 100).toInt()
                    Text(
                        text = "Downloading APK: $pct% (${versionState.downloadedBytes / 1024} KB)",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisElectricCyan
                    )
                }

                if (versionState.statusMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val statusColor = when {
                        versionState.checkError != null ||
                            versionState.verificationStatus is ApkVerificationStatus.Failed -> JarvisErrorRed
                        versionState.downgradeBlockedReason != null -> JarvisAmberAlert
                        versionState.verificationStatus is ApkVerificationStatus.Verified -> JarvisHoloTeal
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = versionState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                val isVerified = versionState.verificationStatus is ApkVerificationStatus.Verified
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDownloadUpdate,
                        enabled = versionState.updateAvailable && !versionState.isDownloading && !versionState.isChecking,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("download_apk_button")
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download APK")
                    }
                    Button(
                        onClick = {
                            val result = onInstallUpdate(context)
                            if (result is InstallLaunchResult.NeedsUnknownSourcesPermission) {
                                showUnknownSourcesDialog = result.settingsIntent
                            }
                        },
                        enabled = isVerified && !versionState.isDownloading,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("install_update_button")
                    ) {
                        Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install Update")
                    }
                }

                if (isVerified) {
                    val verified = versionState.verificationStatus as ApkVerificationStatus.Verified
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = JarvisHoloTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SHA-256 Verified • v${verified.apkVersionName} (${verified.apkVersionCode}) • ${verified.byteSize / 1024} KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisHoloTeal
                        )
                    }
                }
            }
        }

        // --- VERSION HISTORY & RELEASE NOTES ---
        item {
            JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                Text("VERSION HISTORY & RELEASE NOTES", style = MaterialTheme.typography.labelMedium, color = JarvisHoloTeal)
                Spacer(modifier = Modifier.height(8.dp))
                versionState.versionHistory.forEachIndexed { idx, entry ->
                    if (idx > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "v${entry.versionName} (Build ${entry.versionCode})",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (entry.isCurrent) JarvisElectricCyan else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (entry.isCurrent) "${entry.releaseDate} • INSTALLED" else entry.releaseDate,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.isCurrent) JarvisHoloTeal else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (entry.notes.isNotBlank()) {
                            Text(
                                text = entry.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // --- ABOUT APP SECTION ---
        item {
            JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = JarvisElectricCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ABOUT APP", style = MaterialTheme.typography.labelMedium, color = JarvisElectricCyan)
                }
                Spacer(modifier = Modifier.height(8.dp))
                AboutRow("Application Name", versionState.installed.appName)
                AboutRow("Package Name", versionState.installed.packageName)
                AboutRow("Version Name", versionState.installed.versionName)
                AboutRow("Version Code", versionState.installed.versionCode.toString())
                AboutRow("Android Min SDK", "API ${versionState.installed.minSdk} (Android 7.0+)")
                AboutRow("Android Target SDK", "API ${versionState.installed.targetSdk}")
                AboutRow("Build Variant", versionState.installed.buildType)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = JarvisHoloTeal, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "In-place updates verify SHA-256 integrity, enforce anti-downgrade build checks, and preserve DataStore settings and Cloud Firestore history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- VOICE & LANGUAGE SETTINGS ---
        item {
            JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                Text("VOICE & LANGUAGE SETTINGS", style = MaterialTheme.typography.labelMedium, color = JarvisHoloTeal)
                Spacer(modifier = Modifier.height(8.dp))
                LanguageSelectorRow(selected = selectedLanguage, onSelect = onSelectLanguage)
                Spacer(modifier = Modifier.height(12.dp))

                val speechRate = (profile?.speechRate ?: 1.0).toFloat()
                Text(
                    text = "Speech Rate: ${String.format(Locale.US, "%.2fx", speechRate)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = speechRate,
                    onValueChange = { onUpdateSettings(null, it.toDouble(), null, null, null) },
                    valueRange = 0.6f..1.8f
                )

                SettingSwitchRow(
                    label = "Spoken Voice Responses (TTS)",
                    checked = profile?.voiceResponseEnabled ?: true,
                    onCheckedChange = { onUpdateSettings(it, null, null, null, null) }
                )
                SettingSwitchRow(
                    label = "Wake Phrase (\"Hello Jarvis\")",
                    checked = profile?.wakePhraseEnabled ?: true,
                    onCheckedChange = { onUpdateSettings(null, null, null, null, it) }
                )
                SettingSwitchRow(
                    label = "Confirm Before Sensitive Actions",
                    checked = profile?.confirmSensitiveActions ?: true,
                    onCheckedChange = { onUpdateSettings(null, null, it, null, null) }
                )
                SettingSwitchRow(
                    label = "User-Controlled AI Memory Opt-In",
                    checked = profile?.memoryOptIn ?: true,
                    onCheckedChange = { onUpdateSettings(null, null, null, it, null) }
                )
            }
        }

        item {
            JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("NETWORK GUARDIAN TELEMETRY", style = MaterialTheme.typography.labelMedium, color = JarvisElectricCyan)
                    IconButton(onClick = onRefreshNetwork) {
                        Icon(Icons.Filled.NetworkCheck, contentDescription = "Recheck network", tint = JarvisElectricCyan)
                    }
                }
                NetworkStatusPill(networkState = networkState, onClick = onRefreshNetwork)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Downstream Est: ${networkState.downstreamKbps} kbps • Active Requests: ${networkState.activeRequests} • Retries Recovered: ${networkState.retryCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (networkState.lastError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(networkState.lastError, style = MaterialTheme.typography.bodyMedium, color = JarvisErrorRed)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("USER-APPROVED MEMORY & SHORTCUTS (${memories.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { showAddMemoryDialog = true }, modifier = Modifier.testTag("add_memory_button")) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        if (memories.isEmpty()) {
            item {
                JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No personalized memories or shortcuts saved yet. You control everything Jarvis remembers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(memories, key = { it.id }) { item ->
                JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.category.uppercase()} • ${if (item.approved) "ACTIVE" else "PAUSED"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.approved) JarvisHoloTeal else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(item.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = item.approved, onCheckedChange = { onToggleMemoryApproval(item) })
                            IconButton(onClick = { onDeleteMemory(item.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete memory", tint = JarvisErrorRed)
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (profile?.confirmSensitiveActions != false) showClearDataConfirm = true else onClearConversations()
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisErrorRed.copy(alpha = 0.2f), contentColor = JarvisErrorRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Erase Saved Conversations")
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
