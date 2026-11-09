package dev.stepcron.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Grüne Grundfarben passend zur Uhr; ab Android 12 die Systemfarben (Material You). */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6D35),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA4F4B0),
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF7C5800),
    secondaryContainer = Color(0xFFFFDEA6),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BD88F),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005227),
    onPrimaryContainer = Color(0xFFA4F4B0),
    secondary = Color(0xFFFFC857),
    secondaryContainer = Color(0xFF5E4200),
    error = Color(0xFFFFB4AB),
)

/**
 * Farben der Diagramme. Erledigt ist immer grün und verpasst immer rot – wie auf der Uhr und unabhängig von den
 * Systemfarben, damit die Bedeutung gleich bleibt.
 */
@Immutable
data class ChartColors(
    val done: Color,
    /** Text auf einer voll gefärbten Erledigt-Fläche. */
    val onDone: Color,
    val missed: Color,
    val empty: Color,
    val guide: Color,
    val label: Color,
    val today: Color,
)

private fun chartColors(scheme: ColorScheme, dark: Boolean) = ChartColors(
    done = if (dark) Color(0xFF7BD88F) else Color(0xFF1B6D35),
    onDone = if (dark) Color(0xFF00210B) else Color.White,
    missed = scheme.error,
    empty = scheme.surfaceVariant,
    guide = scheme.onSurfaceVariant.copy(alpha = 0.6f),
    label = scheme.onSurfaceVariant,
    today = scheme.primary,
)

val LocalChartColors = staticCompositionLocalOf { chartColors(LightColors, dark = false) }

@Composable
fun DeskBreakPhoneTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalChartColors provides chartColors(colors, darkTheme)) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
