package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.JarvisLanguage
import com.example.data.model.ResearchBrief
import com.example.data.model.UiState
import com.example.ui.components.JarvisHudCard
import com.example.ui.theme.JarvisDeepBlack
import com.example.ui.theme.JarvisElectricCyan
import com.example.ui.theme.JarvisErrorRed
import com.example.ui.theme.JarvisHoloTeal
import com.example.ui.viewmodel.ResearchResultView

@Composable
fun DeepResearchScreen(
    researchState: UiState<ResearchResultView>,
    savedBriefs: List<ResearchBrief>,
    selectedLanguage: JarvisLanguage,
    onRunResearch: (String) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {
        }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Public, contentDescription = null, tint = JarvisElectricCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DEEP RESEARCH & VERIFIER",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisElectricCyan
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Queries live Wikipedia & DuckDuckGo sources first, then synthesizes verified citations in ${selectedLanguage.displayName}. Zero fabricated links.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Research topic or claim to verify") },
                    placeholder = { Text("e.g., James Webb Space Telescope latest findings") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("research_query_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onRunResearch(query) },
                    enabled = query.isNotBlank() && researchState !is UiState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("research_run_button")
                ) {
                    if (researchState is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Searching & Verifying Sources...")
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run Verified Research")
                    }
                }
            }
        }

        when (researchState) {
            is UiState.Error -> {
                item {
                    JarvisHudCard(modifier = Modifier.fillMaxWidth(), borderColor = JarvisErrorRed) {
                        Text(researchState.message, color = JarvisErrorRed, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is UiState.Success -> {
                val res = researchState.data
                item {
                    JarvisHudCard(modifier = Modifier.fillMaxWidth(), borderColor = JarvisElectricCyan) {
                        Text("SYNTHESIS: ${res.query}", style = MaterialTheme.typography.titleMedium, color = JarvisElectricCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(res.summary, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = JarvisHoloTeal, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Claim Verification Notes", style = MaterialTheme.typography.labelLarge, color = JarvisHoloTeal)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(res.verificationNotes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                item {
                    Text(
                        text = "RETRIEVED SOURCES (${res.hits.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(res.hits, key = { it.url }) { hit ->
                    JarvisHudCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openUrl(hit.url) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${hit.provider} • ${hit.timestamp}",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisHoloTeal
                            )
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open source URL", tint = JarvisElectricCyan, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(hit.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(hit.snippet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(hit.url, style = MaterialTheme.typography.labelSmall, color = JarvisElectricCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            else -> {}
        }

        if (savedBriefs.isNotEmpty()) {
            item {
                Text(
                    text = "SAVED RESEARCH ARCHIVE (${savedBriefs.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(savedBriefs, key = { it.id }) { brief ->
                JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text(brief.query, style = MaterialTheme.typography.titleMedium, color = JarvisElectricCyan)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(brief.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    if (brief.sourceUrls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("${brief.sourceUrls.size} verified source links stored", style = MaterialTheme.typography.labelSmall, color = JarvisHoloTeal)
                    }
                }
            }
        }
    }
}
