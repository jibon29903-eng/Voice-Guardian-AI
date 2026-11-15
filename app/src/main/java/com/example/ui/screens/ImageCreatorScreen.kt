package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.model.GeneratedImageItem
import com.example.data.model.UiState
import com.example.ui.components.JarvisHudCard
import com.example.ui.components.SensitiveConfirmationDialog
import com.example.ui.theme.JarvisDeepBlack
import com.example.ui.theme.JarvisElectricCyan
import com.example.ui.theme.JarvisErrorRed
import com.example.ui.theme.JarvisHoloTeal
import java.io.File
import java.io.FileOutputStream

@Composable
fun ImageCreatorScreen(
    imageGenState: UiState<GeneratedImageItem>,
    gallery: List<GeneratedImageItem>,
    confirmSensitiveActions: Boolean,
    loadBitmap: (String) -> Bitmap?,
    onGenerateOrEdit: (prompt: String, sourceBitmap: Bitmap?) -> Unit
) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var referenceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedItem by remember { mutableStateOf<GeneratedImageItem?>(null) }
    var pendingShareItem by remember { mutableStateOf<GeneratedImageItem?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            referenceBitmap = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun shareImageExternally(item: GeneratedImageItem) {
        val bmp = loadBitmap(item.filePath) ?: return
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val shareFile = File(dir, "${item.id}.png")
        FileOutputStream(shareFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, item.prompt)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share AI Image"))
    }

    if (pendingShareItem != null) {
        SensitiveConfirmationDialog(
            title = "Confirm External Share",
            message = "Do you want to share or post this AI-generated image to an external application?",
            confirmLabel = "Share Image",
            onConfirm = {
                val target = pendingShareItem
                pendingShareItem = null
                if (target != null) shareImageExternally(target)
            },
            onDismiss = { pendingShareItem = null }
        )
    }

    val activeDisplayItem = selectedItem ?: (imageGenState as? UiState.Success)?.data ?: gallery.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "AI IMAGE CREATOR • NANO BANANA 2.1",
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisElectricCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = {
                        Text(if (referenceBitmap == null) "Describe an image to create" else "Describe edits to apply to reference image")
                    },
                    placeholder = { Text("e.g., Futuristic holographic command deck overlooking Dhaka skyline at dusk") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_prompt_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (referenceBitmap == null) "Pick Image to Edit" else "Change Ref")
                    }

                    Button(
                        onClick = { onGenerateOrEdit(prompt, referenceBitmap) },
                        enabled = prompt.isNotBlank() && imageGenState !is UiState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricCyan),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("image_generate_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (referenceBitmap == null) "Generate" else "Edit Image")
                    }
                }

                if (referenceBitmap != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            bitmap = referenceBitmap!!.asImageBitmap(),
                            contentDescription = "Selected reference image for editing",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Text(
                            text = "Image-to-Image editing active",
                            style = MaterialTheme.typography.labelMedium,
                            color = JarvisHoloTeal,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { referenceBitmap = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear reference image")
                        }
                    }
                }
            }
        }

        if (imageGenState is UiState.Loading) {
            item {
                JarvisHudCard(modifier = Modifier.fillMaxWidth(), borderColor = JarvisElectricCyan) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = JarvisElectricCyan)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Synthesizing image with Nano Banana 2.1 (may take 30–60s)...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else if (imageGenState is UiState.Error) {
            item {
                JarvisHudCard(modifier = Modifier.fillMaxWidth(), borderColor = JarvisErrorRed) {
                    Text(imageGenState.message, color = JarvisErrorRed, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (activeDisplayItem != null) {
            val bmp = loadBitmap(activeDisplayItem.filePath)
            if (bmp != null) {
                item {
                    JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = activeDisplayItem.prompt,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .clip(MaterialTheme.shapes.medium)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(activeDisplayItem.prompt, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { referenceBitmap = bmp },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit This")
                            }
                            Button(
                                onClick = {
                                    if (confirmSensitiveActions) {
                                        pendingShareItem = activeDisplayItem
                                    } else {
                                        shareImageExternally(activeDisplayItem)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricCyan),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share / Save")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "GENERATED GALLERY (${gallery.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (gallery.isEmpty()) {
                JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text("No images generated yet. Enter a prompt above.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(gallery, key = { it.id }) { item ->
                        val thumb = loadBitmap(item.filePath)
                        if (thumb != null) {
                            Box(
                                modifier = Modifier
                                    .size(116.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { selectedItem = item }
                            ) {
                                Image(
                                    bitmap = thumb.asImageBitmap(),
                                    contentDescription = item.prompt,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Text(
                                    text = if (item.isEdited) "EDITED" else "AI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisElectricCyan,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .background(JarvisDeepBlack.copy(alpha = 0.75f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
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
}
