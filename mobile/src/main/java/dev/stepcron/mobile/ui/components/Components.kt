package dev.stepcron.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Kleines Farbfeld für Legenden; [dashedLine] zeichnet stattdessen eine gestrichelte Linie (z. B. Tagesziel). */
@Composable
fun LegendSwatch(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    outline: Color? = null,
    dashedLine: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(modifier = Modifier.size(width = if (dashedLine) 16.dp else 12.dp, height = 12.dp)) {
            if (dashedLine) {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                )
                return@Canvas
            }
            val radius = CornerRadius(3.dp.toPx())
            drawRoundRect(color = color, cornerRadius = radius)
            if (outline != null) {
                drawRoundRect(color = outline, cornerRadius = radius, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
