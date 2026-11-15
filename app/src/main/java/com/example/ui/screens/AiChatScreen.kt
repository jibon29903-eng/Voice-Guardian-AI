package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ConversationMessage
import com.example.data.model.JarvisLanguage
import com.example.ui.components.JarvisHudCard
import com.example.ui.components.LanguageSelectorRow
import com.example.ui.components.SensitiveConfirmationDialog
import com.example.ui.theme.JarvisCobaltBlue
import com.example.ui.theme.JarvisDeepBlack
import com.example.ui.theme.JarvisElectricCyan
import com.example.ui.theme.JarvisErrorRed
import com.example.ui.theme.JarvisHoloTeal
import com.example.ui.theme.JarvisMidnightNavy
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.viewmodel.ChatUiState

@Composable
fun AiChatScreen(
    messages: List<ConversationMessage>,
    chatState: ChatUiState,
    selectedLanguage: JarvisLanguage,
    confirmSensitiveActions: Boolean,
    onSelectLanguage: (JarvisLanguage) -> Unit,
    onUpdateDraft: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onInterrupt: () -> Unit,
    onSaveMemory: (title: String, content: String) -> Unit,
    onClearConversations: () -> Unit,
    onDismissError: () -> Unit
) {
    val listState = rememberLazyListState()
    var showClearConfirm by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showSummarizeDialog by remember { mutableStateOf(false) }
    var toolInputText by remember { mutableStateOf("") }
    var targetTranslateLang by remember { mutableStateOf(JarvisLanguage.BANGLA) }
    var memorySavedBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(messages.size, chatState.streamingReply) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    if (showClearConfirm) {
        SensitiveConfirmationDialog(
            title = "Clear All Chat History?",
            message = "Sensitive action confirmation: permanently delete all saved AI conversations from Firestore?",
            confirmLabel = "Delete Chats",
            onConfirm = {
                showClearConfirm = false
                onClearConversations()
            },
            onDismiss = { showClearConfirm = false }
        )
    }

    if (showTranslateDialog) {
        AlertDialog(
            onDismissRequest = { showTranslateDialog = false },
            containerColor = JarvisMidnightNavy,
            title = { Text("Translate with Jarvis") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Target language:", style = MaterialTheme.typography.labelMedium)
                    LanguageSelectorRow(selected = targetTranslateLang, onSelect = { targetTranslateLang = it })
                    OutlinedTextField(
                        value = toolInputText,
                        onValueChange = { toolInputText = it },
                        label = { Text("Text to translate") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val txt = toolInputText.trim()
                        if (txt.isNotBlank()) {
                            onSendMessage("Translate the following accurately into ${targetTranslateLang.displayName} (${targetTranslateLang.nativeLabel}):\n\n$txt")
                        }
                        toolInputText = ""
                        showTranslateDialog = false
                    }
                ) { Text("Translate") }
            },
            dismissButton = {
                TextButton(onClick = { showTranslateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSummarizeDialog) {
        AlertDialog(
            onDismissRequest = { showSummarizeDialog = false },
            containerColor = JarvisMidnightNavy,
            title = { Text("Summarize Document / Notes") },
            text = {
                OutlinedTextField(
                    value = toolInputText,
                    onValueChange = { toolInputText = it },
                    label = { Text("Paste article, email, or document text") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val txt = toolInputText.trim()
                        if (txt.isNotBlank()) {
                            onSendMessage("Summarize the following document clearly with key takeaways and action items:\n\n$txt")
                        }
                        toolInputText = ""
                        showSummarizeDialog = false
                    }
                ) { Text("Summarize") }
            },
            dismissButton = {
                TextButton(onClick = { showSummarizeDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBlack)
    ) {
        Surface(
            color = JarvisMidnightNavy,
            border = BorderStroke(1.dp, JarvisElectricCyan.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PERSONAL AI ASSISTANT",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisElectricCyan
                    )
                    IconButton(
                        onClick = {
                            if (confirmSensitiveActions) showClearConfirm = true else onClearConversations()
                        },
                        enabled = messages.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Clear conversation history"
                        )
                    }
                }
                LanguageSelectorRow(selected = selectedLanguage, onSelect = onSelectLanguage)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { showTranslateDialog = true },
                        label = { Text("Translate") },
                        leadingIcon = { Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { showSummarizeDialog = true },
                        label = { Text("Summarize Doc") },
                        leadingIcon = { Icon(Icons.Filled.Summarize, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
                if (memorySavedBanner != null) {
                    Text(
                        text = memorySavedBanner!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisHoloTeal,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && !chatState.isGenerating) {
                item {
                    JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Jarvis AI Ready",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisElectricCyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ask questions, translate between English, Bangla, and Arabic, summarize documents, or manage daily tasks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isUser) JarvisCobaltBlue.copy(alpha = 0.22f) else JarvisSurfaceElevated,
                    border = BorderStroke(
                        1.dp,
                        if (isUser) JarvisCobaltBlue.copy(alpha = 0.55f) else JarvisElectricCyan.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isUser) "YOU" else "JARVIS • ${msg.language.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) JarvisCobaltBlue else JarvisElectricCyan
                            )
                            if (!isUser) {
                                IconButton(
                                    onClick = {
                                        onSaveMemory("Saved Insight", msg.content.take(350))
                                        memorySavedBanner = "Approved & saved to Jarvis Memory."
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.BookmarkAdd,
                                        contentDescription = "Save to user-approved memory",
                                        tint = JarvisHoloTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = msg.content,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            if (chatState.isGenerating) {
                item {
                    JarvisHudCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = JarvisElectricCyan
                    ) {
                        Text(
                            text = "JARVIS • STREAMING",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisElectricCyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (chatState.streamingReply.isBlank()) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = JarvisElectricCyan)
                        } else {
                            Text(
                                text = chatState.streamingReply,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }

        if (chatState.errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JarvisErrorRed.copy(alpha = 0.14f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chatState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisErrorRed,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismissError) { Text("Dismiss") }
            }
        }

        Surface(color = JarvisMidnightNavy) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = chatState.draftText,
                    onValueChange = onUpdateDraft,
                    placeholder = { Text("Ask Jarvis anything...") },
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_chat_input")
                )
                if (chatState.isGenerating) {
                    IconButton(
                        onClick = onInterrupt,
                        modifier = Modifier.testTag("ai_chat_stop_button")
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop generating", tint = JarvisErrorRed)
                    }
                } else {
                    IconButton(
                        onClick = { onSendMessage(chatState.draftText) },
                        enabled = chatState.draftText.isNotBlank(),
                        modifier = Modifier.testTag("ai_chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = JarvisElectricCyan
                        )
                    }
                }
            }
        }
    }
}
