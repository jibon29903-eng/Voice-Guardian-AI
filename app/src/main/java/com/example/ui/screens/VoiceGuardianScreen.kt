package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.JarvisLanguage
import com.example.data.model.VoiceCommandEntry
import com.example.ui.components.AudioWaveformBar
import com.example.ui.components.JarvisHudCard
import com.example.ui.components.JarvisVoiceOrb
import com.example.ui.components.LanguageSelectorRow
import com.example.ui.components.SensitiveConfirmationDialog
import com.example.ui.theme.JarvisAmberAlert
import com.example.ui.theme.JarvisDeepBlack
import com.example.ui.theme.JarvisElectricCyan
import com.example.ui.theme.JarvisErrorRed
import com.example.ui.theme.JarvisHoloTeal
import com.example.ui.viewmodel.ChatUiState
import com.example.voice.VoiceEngineState

@Composable
fun VoiceGuardianScreen(
    voiceState: VoiceEngineState,
    chatState: ChatUiState,
    selectedLanguage: JarvisLanguage,
    wakePhraseEnabled: Boolean,
    confirmSensitiveActions: Boolean,
    commandHistory: List<VoiceCommandEntry>,
    onSelectLanguage: (JarvisLanguage) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onToggleMute: () -> Unit,
    onInterrupt: () -> Unit,
    onUpdateDraft: (String) -> Unit,
    onSubmitTextFallback: (String) -> Unit,
    onClearVoiceHistory: () -> Unit,
    onDismissError: () -> Unit
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedNote by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        permissionDeniedNote = !granted
        if (granted) {
            onStartListening()
        }
    }

    fun triggerVoiceStart() {
        if (hasMicPermission) {
            onStartListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (showClearConfirm) {
        SensitiveConfirmationDialog(
            title = "Clear Voice History?",
            message = "This will permanently delete all saved voice command transcripts from your account.",
            confirmLabel = "Delete History",
            onConfirm = {
                showClearConfirm = false
                onClearVoiceHistory()
            },
            onDismiss = { showClearConfirm = false }
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
            JarvisHudCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = JarvisElectricCyan.copy(alpha = 0.65f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VOICE GUARDIAN",
                        style = MaterialTheme.typography.titleLarge,
                        color = JarvisElectricCyan
                    )
                    Text(
                        text = if (wakePhraseEnabled) "Wake: \"Hello Jarvis\"" else "Push-to-Talk",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisHoloTeal
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                LanguageSelectorRow(
                    selected = selectedLanguage,
                    onSelect = onSelectLanguage
                )

                Spacer(modifier = Modifier.height(18.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    JarvisVoiceOrb(
                        isListening = voiceState.isListening,
                        isSpeaking = voiceState.isSpeaking,
                        audioLevel = voiceState.audioLevel,
                        orbSize = 176.dp,
                        onClick = {
                            if (voiceState.isListening) onStopListening() else triggerVoiceStart()
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AudioWaveformBar(
                        isActive = voiceState.isListening || voiceState.isSpeaking,
                        audioLevel = voiceState.audioLevel
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = voiceState.statusMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisElectricCyan
                    )
                    if (voiceState.partialTranscript.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "\"${voiceState.partialTranscript}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { if (voiceState.isListening) onStopListening() else triggerVoiceStart() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (voiceState.isListening) JarvisErrorRed else JarvisElectricCyan
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voice_start_stop_button")
                    ) {
                        Icon(
                            imageVector = if (voiceState.isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (voiceState.isListening) "Stop" else "Start")
                    }

                    FilledTonalButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voice_mute_button")
                    ) {
                        Icon(
                            imageVector = if (voiceState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (voiceState.isMuted) "Unmute" else "Mute")
                    }

                    OutlinedButton(
                        onClick = onInterrupt,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voice_interrupt_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PanTool,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Interrupt")
                    }
                }

                if (chatState.isGenerating) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = JarvisElectricCyan
                    )
                    if (chatState.streamingReply.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = chatState.streamingReply,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                val err = voiceState.errorMessage ?: chatState.errorMessage
                if (err != null || permissionDeniedNote) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (permissionDeniedNote) {
                                "Microphone permission denied. Grant RECORD_AUDIO in settings or use text fallback below."
                            } else err.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisErrorRed,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            permissionDeniedNote = false
                            onDismissError()
                        }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }

        item {
            JarvisHudCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = JarvisAmberAlert.copy(alpha = 0.55f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = JarvisAmberAlert,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Background Wake-Word Notice",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisAmberAlert
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Android restricts third-party apps from keeping the microphone open continuously in the background for always-on hotword detection. Voice Guardian detects \"Hello Jarvis\" while active in foreground; use the Orb or text fallback anytime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Text Chat Fallback (Draft Preserved)",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = chatState.draftText,
                        onValueChange = onUpdateDraft,
                        placeholder = { Text("Type a command or \"Hello Jarvis...\"") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voice_text_fallback_input"),
                        maxLines = 3
                    )
                    IconButton(
                        onClick = { onSubmitTextFallback(chatState.draftText) },
                        enabled = chatState.draftText.isNotBlank() && !chatState.isGenerating,
                        modifier = Modifier.testTag("voice_text_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send command",
                            tint = JarvisElectricCyan
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VOICE COMMAND HISTORY (${commandHistory.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (commandHistory.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            if (confirmSensitiveActions) showClearConfirm = true else onClearVoiceHistory()
                        }
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
        }

        if (commandHistory.isEmpty()) {
            item {
                JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No spoken commands logged yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(commandHistory, key = { it.id }) { cmd ->
                JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (cmd.wakeTriggered) "WAKE: HELLO JARVIS" else "VOICE COMMAND",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (cmd.wakeTriggered) JarvisHoloTeal else JarvisElectricCyan
                        )
                        Text(
                            text = cmd.language.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cmd.transcript,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (cmd.responseSummary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cmd.responseSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
