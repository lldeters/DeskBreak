package dev.stepcron.mobile.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.R
import dev.stepcron.mobile.ui.components.IconBadge
import kotlin.math.roundToInt

/** Innenabstand von AppCard; Zeilen ziehen ihre antippbare Fläche bis an den Kartenrand (siehe [bleed]). */
internal val CardPadding = 20.dp

/** Deckkraft gesperrter Inhalte (Material). */
internal const val DISABLED_ALPHA = 0.38f

/**
 * Lässt ein Element um [start]/[end] über den Innenabstand der Karte hinausragen – so reicht die Fläche (und die
 * Berührungsrückmeldung) einer antippbaren Zeile bis an den Kartenrand, der Text bleibt auf der Kartenkante.
 */
internal fun Modifier.bleed(start: Dp = CardPadding, end: Dp = CardPadding): Modifier = layout { measurable, constraints ->
    val left = start.roundToPx()
    val right = end.roundToPx()
    val wider = constraints.copy(
        minWidth = constraints.minWidth + left + right,
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + left + right else constraints.maxWidth,
    )
    val placeable = measurable.measure(wider)
    layout(placeable.width - left - right, placeable.height) { placeable.placeRelative(-left, 0) }
}

/** Häkchen im Schalter, damit „an" nicht nur an der Farbe zu erkennen ist. */
internal fun switchThumb(checked: Boolean): (@Composable () -> Unit)? = if (checked) {
    { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
} else {
    null
}

@Composable
private fun RowIcon(@DrawableRes icon: Int, enabled: Boolean) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
        modifier = Modifier.size(24.dp),
    )
}

@Composable
private fun RowTexts(title: String, subtitle: String?, enabled: Boolean, modifier: Modifier = Modifier) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
}

/** Einstellung mit Schalter. Die ganze Zeile ist antippbar und für TalkBack ein Element (Schalter mit Zustand). */
@Composable
internal fun SwitchSettingRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .bleed()
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = CardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowIcon(icon, enabled)
        RowTexts(title, subtitle, enabled, Modifier.weight(1f))
        // Ohne eigenen Klick: Die Zeile übernimmt das, sonst gäbe es für TalkBack zwei Elemente.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, thumbContent = switchThumb(checked))
    }
}

/**
 * Zahl mit −/+ (Intervall, Tagesziel, Snooze, Schritte): Wert groß mit Einheit, Tasten 48 dp, an den Grenzen
 * gesperrt. Für TalkBack ist die Zeile ein Element: ein Regler ([position] von [positions]) mit dem Wert als
 * Zustand, verstellbar per Wischen nach oben/unten oder über die Aktionen [decreaseLabel]/[increaseLabel].
 */
@Composable
internal fun StepperSettingRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String?,
    value: Int,
    unit: String,
    valueDescription: String,
    position: Int,
    positions: Int,
    decreaseLabel: String,
    increaseLabel: String,
    onStep: (Int) -> Unit,
    enabled: Boolean = true,
) {
    val canDecrease = enabled && position > 0
    val canIncrease = enabled && position < positions - 1
    AdaptiveRow(
        minLeading = 152.dp,
        modifier = Modifier
            .bleed()
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                stateDescription = valueDescription
                if (enabled) {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = position.toFloat(),
                        range = 0f..(positions - 1).toFloat(),
                        steps = (positions - 2).coerceAtLeast(0),
                    )
                    setProgress { target ->
                        val steps = target.roundToInt().coerceIn(0, positions - 1) - position
                        if (steps != 0) onStep(steps)
                        steps != 0
                    }
                    customActions = buildList {
                        if (canDecrease) add(CustomAccessibilityAction(decreaseLabel) { onStep(-1); true })
                        if (canIncrease) add(CustomAccessibilityAction(increaseLabel) { onStep(1); true })
                    }
                } else {
                    disabled()
                }
            }
            .heightIn(min = 56.dp)
            .padding(horizontal = CardPadding, vertical = 12.dp),
        leading = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RowIcon(icon, enabled)
                RowTexts(title, subtitle, enabled)
            }
        },
        trailing = {
            StepperCapsule(value, unit, enabled, canDecrease, canIncrease, onStep)
        },
    )
}

/** Wert zwischen den Tasten auf einer Kapsel – sieht bewusst anders aus als die Auswahl in Segmenten. */
@Composable
private fun StepperCapsule(
    value: Int,
    unit: String,
    enabled: Boolean,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onStep: (Int) -> Unit,
) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(R.drawable.ic_remove, canDecrease) { onStep(-1) }
            Column(
                modifier = Modifier.widthIn(min = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                            (slideOutVertically { if (up) -it else it } + fadeOut())
                    },
                    label = "stepper",
                ) { shown ->
                    Text(
                        text = shown.toString(),
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        maxLines = 1,
                    )
                }
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                )
            }
            StepButton(R.drawable.ic_add, canIncrease) { onStep(1) }
        }
    }
}

@Composable
private fun StepButton(@DrawableRes icon: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        // Bedienung für TalkBack über die Zeile (Regler bzw. Aktionen), sonst gäbe es drei Elemente je Zeile.
        modifier = Modifier
            .size(48.dp)
            .clearAndSetSemantics {},
    ) {
        Icon(painterResource(icon), contentDescription = null)
    }
}

/**
 * Auswahl aus wenigen benannten Stufen (Vibration) als verbundene Tasten über die ganze Breite; die gewählte ist
 * gefüllt und fett. Passen die Beschriftungen nicht nebeneinander (schmale Handys, große Schrift), stehen sie in
 * zwei Reihen. Für TalkBack eine Gruppe von Optionsfeldern.
 */
@Composable
internal fun <T> ChoiceSettingRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String?,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val labels = options.map { label(it) }
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widest = remember(labels, style, density) {
        with(density) { labels.maxOf { measurer.measure(it, style).size.width }.toDp() }
    }
    Column(modifier = Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RowIcon(icon, enabled = true)
            RowTexts(title, subtitle, enabled = true, Modifier.weight(1f))
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gap = 2.dp
            val fitsInOneRow = (maxWidth - gap * (options.size - 1)) / options.size >= widest + SegmentPadding * 2
            val columns = if (fitsInOneRow) options.size else (options.size + 1) / 2
            val rows = options.chunked(columns)
            val outer = if (rows.size == 1) 24.dp else 20.dp
            val inner = 6.dp
            Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(gap)) {
                rows.forEachIndexed { r, rowOptions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        rowOptions.forEachIndexed { c, option ->
                            val top = r == 0
                            val bottom = r == rows.lastIndex
                            val first = c == 0
                            val last = c == columns - 1
                            val shape = RoundedCornerShape(
                                topStart = if (top && first) outer else inner,
                                topEnd = if (top && last) outer else inner,
                                bottomStart = if (bottom && first) outer else inner,
                                bottomEnd = if (bottom && last) outer else inner,
                            )
                            Segment(
                                text = labels[options.indexOf(option)],
                                selected = option == selected,
                                shape = shape,
                                onClick = { onSelect(option) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Innenabstand einer Auswahltaste links und rechts. */
private val SegmentPadding = 4.dp

@Composable
private fun Segment(text: String, selected: Boolean, shape: Shape, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) colors.primary else colors.surfaceContainerHighest)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = SegmentPadding, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Aktion als Listenzeile (Daten): Symbol auf getönter Fläche, Titel, Erklärung; während der Arbeit ein Kreisel. */
@Composable
internal fun ActionSettingRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled || busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    Row(
        modifier = Modifier
            .bleed()
            .fillMaxWidth()
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = CardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconBadge(icon, tint = tint, size = 40.dp)
        RowTexts(title, subtitle, enabled = enabled || busy, Modifier.weight(1f))
        if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
    }
}

/**
 * Inhalt links, Bedienelement rechts – oder darunter (am Ende ausgerichtet), wenn für den Inhalt weniger als
 * [minLeading] bliebe (schmale Handys, große Schrift).
 */
@Composable
private fun AdaptiveRow(
    minLeading: Dp,
    modifier: Modifier,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Layout(contents = listOf(leading, trailing), modifier = modifier) { (leadingMeasurables, trailingMeasurables), constraints ->
        val gap = 12.dp.roundToPx()
        val end = trailingMeasurables.first().measure(Constraints(maxWidth = constraints.maxWidth))
        val width = constraints.maxWidth
        val sideBySide = width - end.width - gap >= minLeading.roundToPx()
        val start = leadingMeasurables.first().measure(
            Constraints(maxWidth = if (sideBySide) width - end.width - gap else width),
        )
        if (sideBySide) {
            val height = maxOf(start.height, end.height, constraints.minHeight)
            layout(width, height) {
                start.placeRelative(0, (height - start.height) / 2)
                end.placeRelative(width - end.width, (height - end.height) / 2)
            }
        } else {
            val height = maxOf(start.height + gap + end.height, constraints.minHeight)
            layout(width, height) {
                start.placeRelative(0, 0)
                end.placeRelative(width - end.width, start.height + gap)
            }
        }
    }
}

/** Kleine Statuszeile (Symbol oder Kreisel, Text) für Abläufe in einer Karte, z. B. die Wiederherstellung. */
@Composable
internal fun InlineStatus(
    text: String,
    @DrawableRes icon: Int?,
    tint: Color,
    modifier: Modifier = Modifier,
    progress: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = if (action != null) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (progress) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, tint = tint)
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}
