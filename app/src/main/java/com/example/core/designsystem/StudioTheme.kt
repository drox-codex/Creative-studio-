package com.example.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val ObsidianColorScheme = darkColorScheme(
    primary = StudioColors.SubtleHighlight,
    onPrimary = StudioColors.BackgroundPrimary,
    primaryContainer = StudioColors.SurfaceElevated,
    onPrimaryContainer = StudioColors.TextPrimary,
    secondary = StudioColors.MetallicSilver,
    onSecondary = StudioColors.BackgroundPrimary,
    secondaryContainer = StudioColors.SurfaceInteractive,
    onSecondaryContainer = StudioColors.MetallicSilver,
    tertiary = StudioColors.TextSecondary,
    onTertiary = StudioColors.BackgroundPrimary,
    background = StudioColors.BackgroundPrimary,
    onBackground = StudioColors.TextPrimary,
    surface = StudioColors.BackgroundSecondary,
    onSurface = StudioColors.TextPrimary,
    surfaceVariant = StudioColors.SurfaceElevated,
    onSurfaceVariant = StudioColors.TextSecondary,
    outline = StudioColors.Border,
    outlineVariant = StudioColors.BorderFocused,
    error = StudioColors.Error,
    onError = StudioColors.BackgroundPrimary,
    errorContainer = StudioColors.ErrorSurface,
    onErrorContainer = StudioColors.Error
)

private val SlateColorScheme = ObsidianColorScheme.copy(
    background = StudioColors.SlateBackgroundPrimary,
    surface = StudioColors.SlateBackgroundSecondary,
    surfaceVariant = StudioColors.SlateSurfaceElevated,
    primaryContainer = StudioColors.SlateSurfaceElevated
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(StudioRadii.xs),
    small = RoundedCornerShape(StudioRadii.sm),
    medium = RoundedCornerShape(StudioRadii.md),
    large = RoundedCornerShape(StudioRadii.lg),
    extraLarge = RoundedCornerShape(StudioRadii.xl)
)

val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun CreativeStudioTheme(
    themeVariant: String = "obsidian",
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (themeVariant == "slate") SlateColorScheme else ObsidianColorScheme
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StudioTypography,
            shapes = StudioShapes,
            content = content
        )
    }
}
