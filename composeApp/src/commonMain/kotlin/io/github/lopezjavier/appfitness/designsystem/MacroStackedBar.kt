@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.macro_stacked_bar_state
import org.jetbrains.compose.resources.stringResource

/** Geometry of [MacroStackedBar]. */
object MacroStackedBarDefaults {
    val Height: Dp = 12.dp

    /** The 8 dp bar of the nutrition plan (C-105). */
    val SlimHeight: Dp = 8.dp

    /** Hairline gap between the macro segments. */
    val SegmentGap: Dp = 2.dp

    /** Share of kcal per macro in percent (protein x4, carbs x4, fat x9); zeros when there are no kcal. */
    fun kcalShares(proteinG: Double, carbsG: Double, fatG: Double): Triple<Int, Int, Int> {
        val p = proteinG * 4
        val c = carbsG * 4
        val f = fatG * 9
        val total = p + c + f
        if (total <= 0.0) return Triple(0, 0, 0)
        val pp = (p / total * 100).toInt()
        val cp = (c / total * 100).toInt()
        return Triple(pp, cp, 100 - pp - cp)
    }

    fun segmentTag(testTag: String, macro: Macro): String = "${testTag}_${macro.name.lowercase()}"
}

/**
 * One horizontal bar split into the three macro colours, each segment as wide as its percent. Decorative segments;
 * a single `stateDescription` speaks "Proteínas 35 %, carbohidratos 42 %, grasas 23 %". Segments are tagged
 * `<testTag>_protein|carbs|fat`. With a [staggerIndex] the bar is revealed from the start once on first composition
 * (R-97).
 */
@Composable
fun MacroStackedBar(
    proteinPct: Int,
    carbsPct: Int,
    fatPct: Int,
    modifier: Modifier = Modifier,
    testTag: String = "macro_stacked_bar",
    barHeight: Dp = MacroStackedBarDefaults.Height,
    staggerIndex: Int? = null,
) {
    val colors = AppTheme.colors
    val reveal = rememberFillProgress(staggerIndex ?: 0)
    val spoken =
        stringResource(
            Res.string.macro_stacked_bar_state,
            AppNumberFormat.integer(proteinPct),
            AppNumberFormat.integer(carbsPct),
            AppNumberFormat.integer(fatPct),
        )
    Row(
        modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(AppShapes.full)
            .background(colors.surfaceOverlay)
            .testTag(testTag)
            .semantics(mergeDescendants = true) { stateDescription = spoken }
            .drawWithContent {
                if (staggerIndex == null || reveal.value >= 1f) {
                    drawContent()
                } else {
                    clipRect(right = size.width * reveal.value) { this@drawWithContent.drawContent() }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(MacroStackedBarDefaults.SegmentGap),
    ) {
        listOf(Macro.Protein to proteinPct, Macro.Carbs to carbsPct, Macro.Fat to fatPct).forEach { (macro, pct) ->
            if (pct > 0) {
                Box(
                    Modifier
                        .weight(pct.toFloat())
                        .fillMaxHeight()
                        .background(AppPaint.vertical(MacroDefaults.color(macro, colors)))
                        .testTag(MacroStackedBarDefaults.segmentTag(testTag, macro))
                        .clearAndSetSemantics {},
                )
            }
        }
    }
}

@Preview
@Composable
private fun MacroStackedBarPreview() {
    AppTheme { MacroStackedBar(35, 42, 23) }
}
