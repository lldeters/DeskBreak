package dev.stepcron.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * Feste Markenfarben statt Material-You-Farben: Grün steht – wie auf der Uhr – für Bewegung, und Uhr und Handy
 * sollen als eine App erkennbar sein. Vollständige Farbschemata (Tonpaletten zur Markenfarbe #1B6D35), damit keine
 * Standardfarbe (Lila) in Flächen oder Schaltern auftaucht.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6D35),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA4F4B0),
    onPrimaryContainer = Color(0xFF00210B),
    inversePrimary = Color(0xFF88D997),
    secondary = Color(0xFF506352),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E8D3),
    onSecondaryContainer = Color(0xFF0E1F12),
    tertiary = Color(0xFF3A656F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEEAF6),
    onTertiaryContainer = Color(0xFF001F25),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FBF3),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFF6FBF3),
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    surfaceTint = Color(0xFF1B6D35),
    inverseSurface = Color(0xFF2C322C),
    inverseOnSurface = Color(0xFFEDF2EA),
    outline = Color(0xFF717970),
    outlineVariant = Color(0xFFC1C9BF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6FBF3),
    surfaceDim = Color(0xFFD6DBD4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5ED),
    surfaceContainer = Color(0xFFEAEFE7),
    surfaceContainerHigh = Color(0xFFE5EAE2),
    surfaceContainerHighest = Color(0xFFDFE4DC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF88D997),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005227),
    onPrimaryContainer = Color(0xFFA4F4B0),
    inversePrimary = Color(0xFF1B6D35),
    secondary = Color(0xFFB7CCB7),
    onSecondary = Color(0xFF233426),
    secondaryContainer = Color(0xFF394B3B),
    onSecondaryContainer = Color(0xFFD3E8D3),
    tertiary = Color(0xFFA2CED9),
    onTertiary = Color(0xFF01363F),
    tertiaryContainer = Color(0xFF204D56),
    onTertiaryContainer = Color(0xFFBEEAF6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101510),
    onBackground = Color(0xFFDFE4DC),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFDFE4DC),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BF),
    surfaceTint = Color(0xFF88D997),
    inverseSurface = Color(0xFFDFE4DC),
    inverseOnSurface = Color(0xFF2C322C),
    outline = Color(0xFF8B9389),
    outlineVariant = Color(0xFF414941),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353A35),
    surfaceDim = Color(0xFF101510),
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181D18),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF262B26),
    surfaceContainerHighest = Color(0xFF313631),
)

/**
 * Bedeutungsfarben der Diagramme und Hinweise – unabhängig vom Farbschema gleich, wie auf der Uhr: Pausen grün,
 * verpasst rot-orange (deutlich heller/dunkler als das Grün, damit es auch bei Rot-Grün-Schwäche unterscheidbar
 * bleibt; Diagramme beschriften die Werte zusätzlich), Snooze bernstein.
 */
@Immutable
data class ChartColors(
    val done: Color,
    /** Text auf einer voll gefärbten Pausen-Fläche. */
    val onDone: Color,
    /** Schwächere Pausen-Fläche, z. B. Tage unter dem Ziel. */
    val doneSoft: Color,
    val missed: Color,
    val snoozed: Color,
    /** Fläche ohne Pausen. */
    val empty: Color,
    /** Freier Tag laut Wochenplan. */
    val free: Color,
    val guide: Color,
    val label: Color,
    val today: Color,
    /** Hintergrund des Fortschrittsrings. */
    val track: Color,
)

private fun chartColors(scheme: ColorScheme, dark: Boolean) = ChartColors(
    done = if (dark) Color(0xFF6FD487) else Color(0xFF1E7A3C),
    onDone = if (dark) Color(0xFF00210B) else Color.White,
    doneSoft = if (dark) Color(0xFF2E5C3A) else Color(0xFF9BD9A9),
    missed = if (dark) Color(0xFFFF8A65) else Color(0xFFC4361C),
    snoozed = if (dark) Color(0xFFFFC857) else Color(0xFFB07800),
    empty = scheme.surfaceContainerHighest,
    free = scheme.surfaceContainer,
    guide = scheme.onSurfaceVariant.copy(alpha = 0.55f),
    label = scheme.onSurfaceVariant,
    today = scheme.primary,
    track = if (dark) Color(0xFF2B3A2D) else Color(0xFFD5EAD8),
)

val LocalChartColors = staticCompositionLocalOf { chartColors(LightColors, dark = false) }

/** Großzügig gerundete Karten wie auf der Uhr. */
private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

/** Ziffern gleich breit („tabular"), damit Zahlen beim Aktualisieren nicht springen. */
private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.tabular(),
        displayMedium = base.displayMedium.tabular(),
        displaySmall = base.displaySmall.tabular(),
        headlineLarge = base.headlineLarge.tabular(),
        headlineMedium = base.headlineMedium.tabular(),
        headlineSmall = base.headlineSmall.tabular(),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun DeskBreakPhoneTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalChartColors provides chartColors(colors, darkTheme)) {
        MaterialTheme(colorScheme = colors, shapes = AppShapes, typography = AppTypography, content = content)
    }
}
