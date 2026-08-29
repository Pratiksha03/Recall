package com.recall.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary = Violet40,
    onSecondary = Color.White,
    secondaryContainer = Violet80,
    onSecondaryContainer = Indigo10,
    tertiary = Teal40,
    onTertiary = Color.White,
    background = Sand95,
    onBackground = Ink10,
    surface = Sand99,
    onSurface = Ink10,
    surfaceVariant = Color(0xFFEDE9F6),
    onSurfaceVariant = Color(0xFF4A4458),
    outline = Color(0xFFCFC8DC),
    outlineVariant = Color(0xFFE4DFEE),
    error = Rose40
)

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo20,
    primaryContainer = Color(0xFF433397),
    onPrimaryContainer = Indigo90,
    secondary = Violet80,
    onSecondary = Indigo20,
    secondaryContainer = Color(0xFF553C99),
    onSecondaryContainer = Color(0xFFEDDCFF),
    tertiary = Teal80,
    onTertiary = Color(0xFF00382F),
    background = Ink10,
    onBackground = Ink90,
    surface = Ink20,
    onSurface = Ink90,
    surfaceVariant = Color(0xFF2B2735),
    onSurfaceVariant = Color(0xFFC9C3D6),
    outline = Color(0xFF4A4458),
    outlineVariant = Color(0xFF322E3D),
    error = Color(0xFFFF9BA6)
)

/**
 * Wraps the whole app. Every `MaterialTheme.colorScheme.x` you see in the screens
 * resolves against whichever of the two schemes above is active.
 */
@Composable
fun RecallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = RecallTypography,
        content = content
    )
}
