package dev.stepcron.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/** Farben für dunkle OLED-Displays: schwarzer Hintergrund, kräftige Status-Farben. */
object DeskBreakColors {
    val Primary = Color(0xFF7BD88F)
    val PrimaryVariant = Color(0xFF3FA65A)
    val Secondary = Color(0xFFFFC857)
    val Error = Color(0xFFFF6B6B)
    val Surface = Color(0xFF1E2023)
    val OnSurfaceVariant = Color(0xFFB4B9BF)

    // Status-Farben der Graphen
    val Done = Color(0xFF5FD37A)
    val Snoozed = Color(0xFFFFC34D)
    val Missed = Color(0xFFFF6464)
    val Empty = Color(0xFF3A3D41)
    val Future = Color(0xFF26282B)
    val Track = Color(0xFF2B2E32)
}

private val WearColorPalette = Colors(
    primary = DeskBreakColors.Primary,
    primaryVariant = DeskBreakColors.PrimaryVariant,
    secondary = DeskBreakColors.Secondary,
    secondaryVariant = Color(0xFFC79A2E),
    background = Color.Black,
    surface = DeskBreakColors.Surface,
    error = DeskBreakColors.Error,
    onPrimary = Color(0xFF0B2913),
    onSecondary = Color(0xFF2B1F00),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = DeskBreakColors.OnSurfaceVariant,
    onError = Color.Black,
)

@Composable
fun DeskBreakTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = WearColorPalette, content = content)
}

/** Ampelfarbe für eine Regelmäßigkeits-Quote. */
fun scoreColor(percent: Int?): Color = when {
    percent == null -> DeskBreakColors.Empty
    percent >= 80 -> DeskBreakColors.Done
    percent >= 50 -> DeskBreakColors.Snoozed
    else -> DeskBreakColors.Missed
}
