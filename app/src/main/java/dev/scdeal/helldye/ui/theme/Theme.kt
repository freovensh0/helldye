package dev.scdeal.helldye.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HelldyeColorScheme = darkColorScheme(
    primary = HelldyeColors.White,
    onPrimary = HelldyeColors.Black,
    secondary = HelldyeColors.TextSecondary,
    onSecondary = HelldyeColors.Black,
    background = HelldyeColors.Black,
    onBackground = HelldyeColors.Text,
    surface = HelldyeColors.Black,
    onSurface = HelldyeColors.Text,
    surfaceVariant = HelldyeColors.Surface,
    onSurfaceVariant = HelldyeColors.TextSecondary,
    inverseSurface = HelldyeColors.SurfaceRaised,
    inverseOnSurface = HelldyeColors.Text,
    inversePrimary = HelldyeColors.TextSecondary,
    scrim = HelldyeColors.Black,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = HelldyeColors.Black,
    surfaceContainerLow = HelldyeColors.Surface,
    surfaceContainer = HelldyeColors.Surface,
    surfaceContainerHigh = HelldyeColors.SurfaceRaised,
    surfaceContainerHighest = HelldyeColors.SurfaceRaised,
    outline = HelldyeColors.Line,
    outlineVariant = HelldyeColors.LineSoft,
    error = HelldyeColors.Signal,
    onError = HelldyeColors.Black,
    errorContainer = HelldyeColors.SignalDim,
    onErrorContainer = HelldyeColors.Signal,
)

@Composable
fun HelldyeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HelldyeColorScheme,
        typography = HelldyeTypography,
        shapes = HelldyeShapes,
        content = content,
    )
}
