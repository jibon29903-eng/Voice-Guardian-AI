package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.NetworkGuardianState
import com.example.data.model.ConversationMessage
import com.example.data.model.DataFreshness
import com.example.data.model.ForexQuote
import com.example.data.model.JarvisLanguage
import com.example.ui.components.JarvisHudCard
import com.example.ui.components.JarvisVoiceOrb
import com.example.ui.components.NetworkStatusPill
import com.example.ui.navigation.JarvisRoutes
import com.example.ui.theme.JarvisAmberAlert
import com.example.ui.theme.JarvisCobaltBlue
import com.example.ui.theme.JarvisDeepBlack
import com.example.ui.theme.JarvisElectricCyan
import com.example.ui.theme.JarvisErrorRed
import com.example.ui.theme.JarvisHoloTeal
import com.example.ui.theme.JarvisMidnightNavy
import com.example.voice.VoiceEngineState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class QuickModuleAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val accent: Color
)

@Composable
fun HomeScreen(
    displayName: String,
    language: JarvisLanguage,
    networkState: NetworkGuardianState,
    voiceState: VoiceEngineState,
    recentMessages: List<ConversationMessage>,
    forexQuotes: List<ForexQuote>,
    onNavigate: (String) -> Unit,
    onRefreshNetwork: () -> Unit,
    onActivateVoice: () -> Unit
) {
    var currentDateTime by remember { mutableStateOf(formatNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentDateTime = formatNow()
            delay(1000L)
        }
    }

    val quickActions = remember {
        listOf(
            QuickModuleAction("Voice Guardian", "Hello Jarvis", Icons.Filled.Mic, JarvisRoutes.VOICE_GUARDIAN, JarvisElectricCyan),
            QuickModuleAction("AI Chat", "Stream & Translate", Icons.AutoMirrored.Filled.Chat, JarvisRoutes.AI_CHAT, JarvisCobaltBlue),
            QuickModuleAction("AI Studio", "Web Builder", Icons.Filled.Code, JarvisRoutes.AI_STUDIO, JarvisHoloTeal),
            QuickModuleAction("Deep Research", "Verified Sources", Icons.Filled.Public, JarvisRoutes.DEEP_RESEARCH, JarvisElectricCyan),
            QuickModuleAction("Image Creator", "Nano Banana 2.1", Icons.Filled.Image, JarvisRoutes.IMAGE_CREATOR, JarvisCobaltBlue),
            QuickModuleAction("Forex Watch", "Live Rates", Icons.AutoMirrored.Filled.TrendingUp, JarvisRoutes.FOREX_WATCHLIST, JarvisHoloTeal),
            QuickModuleAction("Screen Guard", "Analyze UI", Icons.Filled.Screenshot, JarvisRoutes.SCREEN_GUARDIAN, JarvisElectricCyan),
            QuickModuleAction("Whiteboard", "AI Lessons", Icons.Filled.Brush, JarvisRoutes.WHITEBOARD, JarvisCobaltBlue),
            QuickModuleAction("Settings", "Memory & Net", Icons.Filled.Settings, JarvisRoutes.SETTINGS, JarvisHoloTeal)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            JarvisHudCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = JarvisElectricCyan.copy(alpha = 0.6f)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_jarvis_hud_backdrop),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.24f)
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Welcome back, $displayName",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentDateTime,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JarvisElectricCyan
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = JarvisMidnightNavy
                            ) {
                                Text(
                                    text = language.nativeLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JarvisHoloTeal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        NetworkStatusPill(
                            networkState = networkState,
                            onClick = onRefreshNetwork,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        JarvisVoiceOrb(
                            isListening = voiceState.isListening,
                            isSpeaking = voiceState.isSpeaking,
                            audioLevel = voiceState.audioLevel,
                            orbSize = 152.dp,
                            onClick = onActivateVoice
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (voiceState.isListening) {
                                "Listening for \"Hello Jarvis\"..."
                            } else {
                                "Tap Orb to launch Voice Guardian"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = JarvisElectricCyan
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "COMMAND MODULES",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                quickActions.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { action ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onNavigate(action.route) }
                                    .testTag("home_quick_${action.route}"),
                                shape = MaterialTheme.shapes.medium,
                                color = JarvisMidnightNavy,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    action.accent.copy(alpha = 0.38f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = action.title,
                                        tint = action.accent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = action.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = action.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
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
                    text = "FOREX WATCHLIST PREVIEW",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onNavigate(JarvisRoutes.FOREX_WATCHLIST) }) {
                    Text("Open Market", color = JarvisElectricCyan)
                }
            }
            if (forexQuotes.isEmpty()) {
                JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Connecting to live ECB / Open ER-API market feeds...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(forexQuotes.take(4), key = { it.pair }) { quote ->
                        val badgeColor = when (quote.freshness) {
                            DataFreshness.REALTIME_API -> JarvisHoloTeal
                            DataFreshness.CACHED_DELAYED -> JarvisAmberAlert
                            DataFreshness.UNAVAILABLE -> JarvisErrorRed
                        }
                        JarvisHudCard(
                            modifier = Modifier
                                .width(178.dp)
                                .clickable { onNavigate(JarvisRoutes.FOREX_WATCHLIST) },
                            borderColor = badgeColor.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = quote.pair,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = quote.rate?.let { String.format(Locale.US, "%.4f", it) } ?: "N/A",
                                style = MaterialTheme.typography.headlineSmall,
                                color = JarvisElectricCyan
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (quote.freshness) {
                                    DataFreshness.REALTIME_API -> "LIVE • ${quote.providerName}"
                                    DataFreshness.CACHED_DELAYED -> "CACHED / DELAYED"
                                    DataFreshness.UNAVAILABLE -> "UNAVAILABLE"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor
                            )
                            Text(
                                text = quote.timestampLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                    text = "RECENT CONVERSATIONS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onNavigate(JarvisRoutes.AI_CHAT) }) {
                    Text("View All", color = JarvisElectricCyan)
                }
            }
            if (recentMessages.isEmpty()) {
                JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No conversations yet. Say \"Hello Jarvis\" or start an AI Chat session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentMessages.takeLast(4).reversed().forEach { msg ->
                        JarvisHudCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(JarvisRoutes.AI_CHAT) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (msg.role == "user") "YOU • ${msg.source.uppercase()}" else "JARVIS • ${msg.language.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (msg.role == "user") JarvisCobaltBlue else JarvisElectricCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg.content,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatNow(): String {
    val sdf = SimpleDateFormat("EEE, MMM d, yyyy • HH:mm:ss", Locale.getDefault())
    return sdf.format(Date())
}
