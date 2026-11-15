package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.data.model.JarvisLanguage
import com.example.data.model.StudioProject
import com.example.data.model.UiState
import com.example.ui.components.JarvisHudCard
import com.example.ui.components.SensitiveConfirmationDialog
import com.example.ui.theme.JarvisAmberAlert
import com.example.ui.theme.JarvisDeepBlack
import com.example.ui.theme.JarvisElectricCyan
import com.example.ui.theme.JarvisErrorRed
import com.example.ui.theme.JarvisHoloTeal
import com.example.ui.theme.JarvisMidnightNavy
import java.io.File

@Composable
fun AiStudioScreen(
    studioState: UiState<StudioProject>,
    activeProject: StudioProject?,
    savedProjects: List<StudioProject>,
    consoleErrors: List<String>,
    lastCodeChanges: String?,
    selectedLanguage: JarvisLanguage,
    confirmSensitiveActions: Boolean,
    onGenerateWebsite: (description: String, framework: String) -> Unit,
    onSelectProject: (StudioProject) -> Unit,
    onDetectAndFix: () -> Unit,
    onDeleteProject: (String) -> Unit,
    onConsoleError: (String) -> Unit,
    onVoicePromptClick: () -> Unit
) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var framework by remember { mutableStateOf("html_css_js") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showShareConfirm by remember { mutableStateOf(false) }

    fun shareProjectHtml(project: StudioProject) {
        val fullHtml = buildPreviewHtml(project)
        val dir = File(context.cacheDir, "projects").apply { mkdirs() }
        val file = File(dir, "${project.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.html")
        file.writeText(fullHtml)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, project.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Website HTML"))
    }

    if (showShareConfirm && activeProject != null) {
        SensitiveConfirmationDialog(
            title = "Export Website Code?",
            message = "Share \"${activeProject.title}\" HTML bundle to an external app?",
            confirmLabel = "Export",
            onConfirm = {
                showShareConfirm = false
                shareProjectHtml(activeProject)
            },
            onDismiss = { showShareConfirm = false }
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
                    Text(
                        text = "AI STUDIO • WEBSITE & APP BUILDER",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisElectricCyan
                    )
                    IconButton(onClick = onVoicePromptClick) {
                        Icon(Icons.Filled.Mic, contentDescription = "Dictate website prompt", tint = JarvisHoloTeal)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Describe the website or web app to build") },
                    placeholder = { Text("e.g., Responsive dark fintech landing page with live calculator") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("studio_prompt_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = framework == "html_css_js",
                        onClick = { framework = "html_css_js" },
                        label = { Text("HTML / CSS / JS") }
                    )
                    FilterChip(
                        selected = framework == "react_cdn",
                        onClick = { framework = "react_cdn" },
                        label = { Text("React 18 App") }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onGenerateWebsite(prompt, framework) },
                    enabled = prompt.isNotBlank() && studioState !is UiState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("studio_generate_button")
                ) {
                    if (studioState is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Synthesizing Project (${selectedLanguage.displayName})...")
                    } else {
                        Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate & Preview")
                    }
                }
                if (studioState is UiState.Error) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = studioState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisErrorRed
                    )
                }
            }
        }

        if (savedProjects.isNotEmpty()) {
            item {
                Text(
                    text = "SAVED PROJECTS (${savedProjects.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(savedProjects, key = { it.id }) { proj ->
                        val isSelected = activeProject?.id == proj.id
                        Surface(
                            modifier = Modifier.clickable { onSelectProject(proj) },
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) JarvisElectricCyan.copy(alpha = 0.2f) else JarvisMidnightNavy,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) JarvisElectricCyan else JarvisMidnightNavy
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(proj.title, style = MaterialTheme.typography.labelLarge)
                                    Text(proj.framework, style = MaterialTheme.typography.labelSmall, color = JarvisHoloTeal)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { onDeleteProject(proj.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete project", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeProject != null) {
            item {
                JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(activeProject.title, style = MaterialTheme.typography.titleLarge)
                            Text(activeProject.framework, style = MaterialTheme.typography.labelSmall, color = JarvisHoloTeal)
                        }
                        Row {
                            OutlinedButton(
                                onClick = onDetectAndFix,
                                enabled = studioState !is UiState.Loading,
                                modifier = Modifier.testTag("studio_fix_button")
                            ) {
                                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Fix Errors")
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    if (confirmSensitiveActions) showShareConfirm = true else shareProjectHtml(activeProject)
                                }
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Export HTML", tint = JarvisElectricCyan)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    val tabs = listOf("Preview", "HTML", "CSS", "JS", "Logs (${consoleErrors.size})")
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = JarvisDeepBlack,
                        edgePadding = 0.dp
                    ) {
                        tabs.forEachIndexed { idx, label ->
                            Tab(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                text = { Text(label, style = MaterialTheme.typography.labelLarge) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    when (selectedTab) {
                        0 -> StudioWebPreview(
                            html = buildPreviewHtml(activeProject),
                            onConsoleError = onConsoleError
                        )
                        1 -> CodeBlockBox(activeProject.htmlCode)
                        2 -> CodeBlockBox(activeProject.cssCode)
                        3 -> CodeBlockBox(activeProject.jsCode)
                        4 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Diagnostic Summary:",
                                style = MaterialTheme.typography.labelLarge,
                                color = JarvisElectricCyan
                            )
                            Text(activeProject.lastDiagnostic, style = MaterialTheme.typography.bodyMedium)
                            if (!lastCodeChanges.isNullOrBlank()) {
                                Text(
                                    text = "Applied Fixes / Code Changes:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = JarvisHoloTeal
                                )
                                Text(lastCodeChanges, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                text = "Runtime WebView Error Log:",
                                style = MaterialTheme.typography.labelLarge,
                                color = JarvisAmberAlert
                            )
                            if (consoleErrors.isEmpty()) {
                                Text("No runtime JS errors captured.", style = MaterialTheme.typography.bodyMedium, color = JarvisHoloTeal)
                            } else {
                                consoleErrors.forEach { err ->
                                    Text("• $err", style = MaterialTheme.typography.labelMedium, color = JarvisErrorRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun StudioWebPreview(
    html: String,
    onConsoleError: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(JarvisDeepBlack)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            if (consoleMessage != null && consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                onConsoleError("Line ${consoleMessage.lineNumber()}: ${consoleMessage.message()}")
                            }
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }
                    loadDataWithBaseURL("https://jarvis.local/", html, "text/html", "UTF-8", null)
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL("https://jarvis.local/", html, "text/html", "UTF-8", null)
            }
        )
    }
}

@Composable
private fun CodeBlockBox(code: String) {
    Surface(
        color = JarvisDeepBlack,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = code.ifBlank { "// Empty" },
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = JarvisHoloTeal,
            modifier = Modifier.padding(12.dp)
        )
    }
}

private fun buildPreviewHtml(project: StudioProject): String {
    val reactScripts = if (project.framework == "react_cdn") {
        """
        <script crossorigin src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
        <script crossorigin src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
        """.trimIndent()
    } else ""

    val bodyContent = if (project.htmlCode.contains("<html", ignoreCase = true)) {
        project.htmlCode
    } else {
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <style>${project.cssCode}</style>
          $reactScripts
        </head>
        <body>
          ${project.htmlCode.ifBlank { "<div id='root'></div>" }}
          <script>
            window.onerror = function(msg, url, line) {
              console.error(msg + " (line " + line + ")");
            };
            ${project.jsCode}
          </script>
        </body>
        </html>
        """.trimIndent()
    }
    return bodyContent
}
