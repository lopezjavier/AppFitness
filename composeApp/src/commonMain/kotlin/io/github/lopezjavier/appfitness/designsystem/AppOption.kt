package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Immutable

/**
 * One choice of a single-select control ([FilterChipRow], [SegmentedSelector]). [testTag] goes on
 * the selectable node. [contentDescription], when set, is what a screen reader speaks instead of
 * [label] (for example "1 mes" for "1M").
 */
@Immutable
data class AppOption(val label: String, val testTag: String, val contentDescription: String? = null)

/** Selection rules shared by the single-select controls. */
internal object SingleSelection {
    /** Fails fast on a programming error: no options, or a selected index outside them. */
    fun requireValid(selectedIndex: Int, size: Int) {
        require(size > 0) { "A single-select control needs at least one option" }
        require(selectedIndex in 0 until size) { "selectedIndex $selectedIndex is outside 0 until $size" }
    }

    /** Index to report for a tap, or null when the tapped option is already active (no callback). */
    fun onTap(selectedIndex: Int, tappedIndex: Int): Int? = tappedIndex.takeIf { it != selectedIndex }
}
