package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.example.data.model.JarvisLanguage
import com.example.data.model.UiState
import com.example.ui.components.JarvisHudCard
import com.example.ui.theme.JarvisDeepBlack
import com.example.ui.theme.JarvisElectricCyan
import com.example.ui.theme.JarvisErrorRed
import com.example.ui.theme.JarvisHoloTeal

@Composable
fun ScreenGuardianScreen(
    analysisState: UiState<String>,
    selectedLanguage: JarvisLanguage,
    onAnalyzeScreen: (Bitmap, String) -> Unit
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var question by remember { mutableStateOf("") }

    val screenshotPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedBitmap = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (_: Exception) {
                null
            }
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
            JarvisHudCard(modifier = Modifier.fillMaxWidth(), borderColor = JarvisHoloTeal.copy(alpha = 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = JarvisHoloTeal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SCREEN GUARDIAN • PRIVACY-FIRST", style = MaterialTheme.typography.titleMedium, color = JarvisHoloTeal)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Respects Android screen-sharing privacy: no hidden background MediaProjection or storage permissions. Select a screenshot via Android Photo Picker or inspect the current Jarvis view.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            screenshotPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricCyan),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("screen_pick_button")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Screenshot")
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                selectedBitmap = rootView.drawToBitmap()
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Capture View")
                    }
                }

                if (selectedBitmap != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Image(
                        bitmap = selectedBitmap!!.asImageBitmap(),
                        contentDescription = "Selected screen image for analysis",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Ask about visible UI, errors, or content (${selectedLanguage.displayName})") },
                    placeholder = { Text("e.g., Explain this interface and highlight any warnings") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("screen_question_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { selectedBitmap?.let { onAnalyzeScreen(it, question) } },
                    enabled = selectedBitmap != null && analysisState !is UiState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("screen_analyze_button")
                ) {
                    if (analysisState is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyzing Screen...")
                    } else {
                        Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyze Screen")
                    }
                }
            }
        }

        when (analysisState) {
            is UiState.Error -> {
                item {
                    JarvisHudCard(modifier = Modifier.fillMaxWidth(), borderColor = JarvisErrorRed) {
                        Text(analysisState.message, color = JarvisErrorRed, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is UiState.Success -> {
                item {
                    JarvisHudCard(modifier = Modifier.fillMaxWidth(), borderColor = JarvisElectricCyan) {
                        Text("SCREEN ANALYSIS", style = MaterialTheme.typography.labelMedium, color = JarvisElectricCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(analysisState.data, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            else -> {}
        }
    }
}
