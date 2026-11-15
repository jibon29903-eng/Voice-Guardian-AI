package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisElectricCyan,
    onPrimary = JarvisOnCyan,
    primaryContainer = JarvisCyanContainer,
    onPrimaryContainer = JarvisOnCyanContainer,
    secondary = JarvisCobaltBlue,
    onSecondary = JarvisOnCobalt,
    secondaryContainer = JarvisCobaltContainer,
    onSecondaryContainer = JarvisOnCobaltContainer,
    tertiary = JarvisHoloTeal,
    onTertiary = JarvisOnTeal,
    background = JarvisDeepBlack,
    onBackground = JarvisTextPrimary,
    surface = JarvisMidnightNavy,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisSurfaceElevated,
    onSurfaceVariant = JarvisTextSecondary,
    surfaceContainerLowest = JarvisDeepBlack,
    surfaceContainerLow = JarvisMidnightNavy,
    surfaceContainer = JarvisSurfaceElevated,
    surfaceContainerHigh = JarvisSurfaceHigh,
    outline = JarvisBorderGlow,
    outlineVariant = JarvisBorderGlow.copy(alpha = 0.5f),
    error = JarvisErrorRed,
    onError = JarvisOnError
)

private val JarvisShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JarvisTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
