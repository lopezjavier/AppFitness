@file:Suppress("MatchingDeclarationName", "ktlint:standard:property-naming") // Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry of [BarListRow] (C-102). */
object BarListRowDefaults {
    val BarHeight: Dp = 8.dp
    val MinBarWidth: Dp = 4.dp
    val ValueSlotWidth: Dp = 40.dp
    val MinRowHeight: Dp = 32.dp
    val Gap: Dp = Spacing.sm
    const val LabelWeight: Float = 0.45f

    /** Bar length as a share of the largest value; never below 0 and never above 1. */
    fun fraction(value: Number, largest: Number): Float {
        val max = largest.toFloat()
        return if (max <= 0f) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
    }
}

/**
 * One row of a horizontal bar list ("Series por grupo"): the [label] (at most 45 % of the width), an 8 dp
 * `athleticCyan` bar whose length is [fraction] of the slot (at least 4 dp) and the [value] in a 40 dp slot
 * (`titleMedium` W600). The row is at least 32 dp; rows sit 8 dp apart (caller). The bar is decorative: the row reads
 * "label, value". [staggerIndex] delays the one-time fill by 40 ms per row (R-97).
 */
@Composable
fun BarListRow(
    label: String,
    value: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    staggerIndex: Int = 0,
    testTag: String? = null,
) {
    val fill = rememberFillProgress(staggerIndex)
    val target = fraction.coerceIn(0f, 1f)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = BarListRowDefaults.MinRowHeight)
            .let { if (testTag != null) it.testTag(testTag) else it }
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(BarListRowDefaults.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(BarListRowDefaults.LabelWeight),
        )
        Box(Modifier.weight(1f - BarListRowDefaults.LabelWeight).height(BarListRowDefaults.BarHeight)) {
            Box(
                Modifier
                    .fillMaxWidth(target * fill.value)
                    .widthIn(min = BarListRowDefaults.MinBarWidth)
                    .height(BarListRowDefaults.BarHeight)
                    .clip(AppShapes.full)
                    .background(AppTheme.colors.athleticCyan)
                    .clearAndSetSemantics {},
            )
        }
        Text(
            text = value,
            style = AppTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(BarListRowDefaults.ValueSlotWidth),
        )
    }
}

@Preview
@Composable
private fun BarListRowPreview() {
    AppTheme {
        BarListRow("Pecho", "12", BarListRowDefaults.fraction(12, 16))
    }
}
