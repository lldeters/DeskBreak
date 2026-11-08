package dev.stepcron.wear.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.material.PickerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Macht einen Picker mit TalkBack bedienbar: Der Wear-Picker bietet von sich aus keine Aktion, die den Wert
 * ändert. Hier kommen „[previousLabel]" und „[nextLabel]" als Aktionen dazu, und der neue Wert wird angesagt.
 * Die Box fasst ihre Semantik mit der des Pickers zu einem Element zusammen.
 */
@Composable
fun AccessiblePicker(
    state: PickerState,
    previousLabel: String,
    nextLabel: String,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            customActions = listOf(
                CustomAccessibilityAction(previousLabel) { state.stepBy(-1, scope) },
                CustomAccessibilityAction(nextLabel) { state.stepBy(1, scope) },
            )
        },
    ) {
        content()
    }
}

/** Eine Option weiter bzw. zurück; `false` am Rand der Liste. */
private fun PickerState.stepBy(delta: Int, scope: CoroutineScope): Boolean {
    val target = selectedOption + delta
    if (target !in 0 until numberOfOptions) return false
    scope.launch { animateScrollToOption(target) }
    return true
}
