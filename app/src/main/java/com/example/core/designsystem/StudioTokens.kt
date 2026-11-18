package com.example.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Centralized Design Tokens for Creative Studio.
 * Brand direction: Minimal, technical, obsidian & brushed metallic silver.
 */
object StudioColors {
    val BackgroundPrimary = Color(0xFF090A0C)
    val BackgroundSecondary = Color(0xFF111317)
    val SurfaceElevated = Color(0xFF191C21)
    val SurfaceInteractive = Color(0xFF21252C)

    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFF9298A1)
    val TextTertiary = Color(0xFF636973)

    val MetallicSilver = Color(0xFFC4C8CE)
    val SubtleHighlight = Color(0xFFE1E4E8)
    val PureWhite = Color(0xFFFFFFFF)
    val Border = Color(0xFF292D33)
    val BorderFocused = Color(0xFF454B54)

    // Semantic status colors (desaturated, refined to match dark monochrome brand)
    val Success = Color(0xFFA3C9A8)
    val Warning = Color(0xFFD6B985)
    val Error = Color(0xFFD98888)
    val ErrorSurface = Color(0xFF2A191B)

    // Slate theme alternative background tokens
    val SlateBackgroundPrimary = Color(0xFF0E1116)
    val SlateBackgroundSecondary = Color(0xFF161A22)
    val SlateSurfaceElevated = Color(0xFF1E232D)
}

object StudioSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object StudioRadii {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 24.dp
    val full = 999.dp
}

object StudioIconSize {
    val sm = 16.dp
    val md = 20.dp
    val lg = 24.dp
    val xl = 32.dp
}

object StudioMotion {
    const val FastMillis = 180
    const val MediumMillis = 260

    fun <T> snappySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> quickTween() = tween<T>(durationMillis = FastMillis)
}
