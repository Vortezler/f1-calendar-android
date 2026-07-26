package com.praval.f1calendar.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/*
 * A fixed racing-red scheme rather than dynamic colour: the palette is part of the app's identity
 * here, and team liveries are already doing the job of per-row colour.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFFB4001C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD7),
    onPrimaryContainer = Color(0xFF400008),
    secondary = Color(0xFF775654),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD7),
    onSecondaryContainer = Color(0xFF2C1513),
    tertiary = Color(0xFF725B2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF271904),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF231919),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF231919),
    surfaceVariant = Color(0xFFF5DDDB),
    onSurfaceVariant = Color(0xFF534342),
    surfaceContainer = Color(0xFFFCEAE8),
    surfaceContainerHigh = Color(0xFFF7E4E2),
    outline = Color(0xFF857371),
    outlineVariant = Color(0xFFD8C2BF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB3AD),
    onPrimary = Color(0xFF680010),
    primaryContainer = Color(0xFF910019),
    onPrimaryContainer = Color(0xFFFFDAD7),
    secondary = Color(0xFFE7BDB9),
    onSecondary = Color(0xFF442927),
    secondaryContainer = Color(0xFF5D3F3D),
    onSecondaryContainer = Color(0xFFFFDAD7),
    tertiary = Color(0xFFE1C38C),
    onTertiary = Color(0xFF402D05),
    tertiaryContainer = Color(0xFF594319),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF1A1111),
    onBackground = Color(0xFFF1DEDD),
    surface = Color(0xFF1A1111),
    onSurface = Color(0xFFF1DEDD),
    surfaceVariant = Color(0xFF534342),
    onSurfaceVariant = Color(0xFFD8C2BF),
    surfaceContainer = Color(0xFF271D1D),
    surfaceContainerHigh = Color(0xFF322827),
    outline = Color(0xFFA08C8A),
    outlineVariant = Color(0xFF534342),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun F1CalendarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Status bar is transparent, so its icons must contrast with the app background.
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = colorScheme.background.luminance() > 0.5f
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
