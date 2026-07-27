package com.praval.f1calendar.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * A fixed, user-chosen accent rather than Android's dynamic colour: the palette is part of the
 * app's identity here, and team liveries are already doing the job of per-row colour.
 */
@Composable
fun F1CalendarTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Deriving a scheme walks every colour role through an HSV conversion, so it is cached rather
    // than recomputed on each recomposition.
    val colorScheme = remember(appTheme, darkTheme) {
        if (darkTheme) appTheme.darkScheme() else appTheme.lightScheme()
    }

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
