package com.praval.f1calendar.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * The accent the whole app is built around. Each entry carries a single seed colour; the full
 * light and dark schemes are derived from it, so adding a theme is one line rather than forty
 * hand-picked hex values.
 */
enum class AppTheme(val displayName: String, val seed: Color) {
    RACING_RED("Racing red", Color(0xFFE10600)),
    PAPAYA("Papaya", Color(0xFFFF8000)),
    MIDNIGHT("Midnight blue", Color(0xFF3671C6)),
    TEAL("Pit lane teal", Color(0xFF00A3A3)),
    BRITISH_GREEN("British green", Color(0xFF00A551)),
    MONACO_PURPLE("Monaco purple", Color(0xFF7C4DFF)),
    PODIUM_GOLD("Podium gold", Color(0xFFC9A227));

    companion object {
        val DEFAULT = RACING_RED

        fun fromName(value: String?): AppTheme =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * Re-saturates and re-lightens the seed while keeping its hue, optionally rotating the hue for a
 * companion accent. Fixing saturation and value per role is what keeps contrast predictable across
 * every theme: dark text always lands near value 0.15, light surfaces near 1.0.
 */
private fun Color.tone(saturation: Float, value: Float, hueShift: Float = 0f): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(toArgb(), hsv)
    val hue = ((hsv[0] + hueShift) % 360f + 360f) % 360f
    return Color(
        AndroidColor.HSVToColor(
            floatArrayOf(hue, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)),
        ),
    )
}

/** Errors stay red in every theme — an error that matches the accent stops reading as one. */
private val ErrorRed = Color(0xFFBA1A1A)
private val ErrorRedDark = Color(0xFFFFB4AB)

fun AppTheme.lightScheme(): ColorScheme = lightColorScheme(
    primary = seed.tone(0.92f, 0.68f),
    onPrimary = Color.White,
    primaryContainer = seed.tone(0.14f, 1.00f),
    onPrimaryContainer = seed.tone(1.00f, 0.26f),
    secondary = seed.tone(0.28f, 0.46f),
    onSecondary = Color.White,
    secondaryContainer = seed.tone(0.12f, 0.97f),
    onSecondaryContainer = seed.tone(0.65f, 0.20f),
    tertiary = seed.tone(0.55f, 0.48f, hueShift = 62f),
    onTertiary = Color.White,
    tertiaryContainer = seed.tone(0.18f, 0.99f, hueShift = 62f),
    onTertiaryContainer = seed.tone(0.85f, 0.22f, hueShift = 62f),
    background = seed.tone(0.03f, 1.00f),
    onBackground = seed.tone(0.24f, 0.14f),
    surface = seed.tone(0.03f, 1.00f),
    onSurface = seed.tone(0.24f, 0.14f),
    surfaceVariant = seed.tone(0.10f, 0.96f),
    onSurfaceVariant = seed.tone(0.22f, 0.34f),
    surfaceContainer = seed.tone(0.06f, 0.98f),
    surfaceContainerHigh = seed.tone(0.09f, 0.95f),
    outline = seed.tone(0.14f, 0.55f),
    outlineVariant = seed.tone(0.12f, 0.85f),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

fun AppTheme.darkScheme(): ColorScheme = darkColorScheme(
    primary = seed.tone(0.42f, 1.00f),
    onPrimary = seed.tone(1.00f, 0.32f),
    primaryContainer = seed.tone(1.00f, 0.46f),
    onPrimaryContainer = seed.tone(0.16f, 1.00f),
    secondary = seed.tone(0.24f, 0.90f),
    onSecondary = seed.tone(0.60f, 0.26f),
    secondaryContainer = seed.tone(0.35f, 0.36f),
    onSecondaryContainer = seed.tone(0.14f, 0.97f),
    tertiary = seed.tone(0.38f, 0.88f, hueShift = 62f),
    onTertiary = seed.tone(0.90f, 0.26f, hueShift = 62f),
    tertiaryContainer = seed.tone(0.70f, 0.36f, hueShift = 62f),
    onTertiaryContainer = seed.tone(0.18f, 0.98f, hueShift = 62f),
    background = seed.tone(0.22f, 0.09f),
    onBackground = seed.tone(0.09f, 0.95f),
    surface = seed.tone(0.22f, 0.09f),
    onSurface = seed.tone(0.09f, 0.95f),
    surfaceVariant = seed.tone(0.20f, 0.30f),
    onSurfaceVariant = seed.tone(0.14f, 0.85f),
    surfaceContainer = seed.tone(0.24f, 0.14f),
    surfaceContainerHigh = seed.tone(0.24f, 0.19f),
    outline = seed.tone(0.14f, 0.62f),
    outlineVariant = seed.tone(0.20f, 0.32f),
    error = ErrorRedDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)
