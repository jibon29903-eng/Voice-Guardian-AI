package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.ConnectionQuality
import com.example.core.NetworkGuardianState
import com.example.data.model.JarvisLanguage
import com.example.ui.theme.JarvisAmberAlert
import com.example.ui.theme.JarvisBorderGlow
import com.example.ui.theme.JarvisCobaltBlue
import com.example.ui.theme.JarvisElectricCyan
import com.example.ui.theme.JarvisErrorRed
import com.example.ui.theme.JarvisHoloTeal
import com.example.ui.theme.JarvisMidnightNavy
import com.example.ui.theme.JarvisSurfaceElevated

@Composable
fun JarvisHudCard(
    modifier: Modifier = Modifier,
    borderColor: Color = JarvisBorderGlow,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = JarvisMidnightNavy.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.7f)),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun NetworkStatusPill(
    networkState: NetworkGuardianState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (color, icon) = when (networkState.quality) {
        ConnectionQuality.STRONG -> JarvisHoloTeal to Icons.Filled.Wifi
        ConnectionQuality.WEAK -> JarvisAmberAlert to Icons.Rounded.WarningAmber
        ConnectionQuality.OFFLINE -> JarvisErrorRed to Icons.Filled.WifiOff
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .testTag("network_status_pill"),
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Network status: ${networkState.quality.label}",
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${networkState.transportLabel} • ${networkState.quality.label}",
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

@Composable
fun JarvisVoiceOrb(
    isListening: Boolean,
    isSpeaking: Boolean,
    audioLevel: Float,
    orbSize: Dp = 164.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "jarvis_orb")
    val outerAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening) 3200 else 9000, easing = LinearEasing)
        ),
        label = "outer_angle"
    )
    val innerAngle by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isSpeaking) 2600 else 6500, easing = LinearEasing)
        ),
        label = "inner_angle"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val activeColor = when {
        isListening -> JarvisElectricCyan
        isSpeaking -> JarvisHoloTeal
        else -> JarvisCobaltBlue
    }
    val levelBoost = 1f + (audioLevel * 0.22f)

    Box(
        modifier = modifier
            .size(orbSize)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (isListening) "Stop Jarvis Voice Guardian" else "Activate Jarvis Voice Guardian"
            }
            .testTag("jarvis_voice_orb"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.78f * pulse * levelBoost

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeColor.copy(alpha = 0.35f),
                        activeColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension / 2f
                ),
                radius = size.minDimension / 2f,
                center = center
            )

            rotate(outerAngle, pivot = center) {
                drawArc(
                    color = JarvisElectricCyan.copy(alpha = 0.85f),
                    startAngle = 15f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                    size = Size(baseRadius * 2f, baseRadius * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = JarvisCobaltBlue.copy(alpha = 0.75f),
                    startAngle = 190f,
                    sweepAngle = 125f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                    size = Size(baseRadius * 2f, baseRadius * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            val midRadius = baseRadius * 0.78f
            rotate(innerAngle, pivot = center) {
                drawArc(
                    color = JarvisHoloTeal.copy(alpha = 0.9f),
                    startAngle = 0f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(center.x - midRadius, center.y - midRadius),
                    size = Size(midRadius * 2f, midRadius * 2f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = JarvisElectricCyan.copy(alpha = 0.65f),
                    startAngle = 140f,
                    sweepAngle = 95f,
                    useCenter = false,
                    topLeft = Offset(center.x - midRadius, center.y - midRadius),
                    size = Size(midRadius * 2f, midRadius * 2f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            drawCircle(
                color = JarvisSurfaceElevated,
                radius = baseRadius * 0.56f,
                center = center
            )
            drawCircle(
                color = activeColor.copy(alpha = 0.65f),
                radius = baseRadius * 0.56f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        Icon(
            imageVector = when {
                isListening -> Icons.Filled.Mic
                isSpeaking -> Icons.Filled.GraphicEq
                else -> Icons.Filled.MicOff
            },
            contentDescription = null,
            tint = activeColor,
            modifier = Modifier.size(orbSize * 0.24f)
        )
    }
}

@Composable
fun AudioWaveformBar(
    isActive: Boolean,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "wave_phase"
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        val barCount = 28
        val spacing = size.width / barCount
        val barWidth = spacing * 0.52f
        val maxH = size.height * 0.9f

        for (i in 0 until barCount) {
            val normPos = i.toFloat() / barCount
            val envelope = kotlin.math.sin(normPos * Math.PI).toFloat()
            val dynamicAmp = if (isActive) {
                val wave = 0.45f + 0.55f * kotlin.math.abs(kotlin.math.sin(phase + i * 0.48f))
                (0.22f + audioLevel.coerceAtLeast(0.32f) * wave) * envelope
            } else {
                0.14f * envelope + 0.06f
            }
            val barH = (maxH * dynamicAmp).coerceAtLeast(6.dp.toPx())
            val x = i * spacing + (spacing - barWidth) / 2f
            val y = (size.height - barH) / 2f
            drawRoundRect(
                color = if (isActive) JarvisElectricCyan else JarvisCobaltBlue.copy(alpha = 0.45f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun LanguageSelectorRow(
    selected: JarvisLanguage,
    onSelect: (JarvisLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        JarvisLanguage.entries.forEach { lang ->
            val isSelected = lang == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(lang) },
                label = {
                    Text(
                        text = lang.nativeLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = JarvisElectricCyan.copy(alpha = 0.2f),
                    selectedLabelColor = JarvisElectricCyan
                ),
                modifier = Modifier.testTag("lang_chip_${lang.code}")
            )
        }
    }
}

@Composable
fun SensitiveConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisMidnightNavy,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricCyan)
            ) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
