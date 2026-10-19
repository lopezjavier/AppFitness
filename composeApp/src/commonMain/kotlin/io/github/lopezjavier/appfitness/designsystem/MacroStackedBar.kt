@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    fun segmentTag(testTag: String, macro: Macro): String = "${testTag}_${macro.name.lowercase()}"
}

/**
 * One horizontal bar split into the three macro colours, each segment as wide as its percent. Decorative segments;
 * a single `stateDescription` speaks "Proteínas 35 %, carbohidratos 42 %, grasas 23 %". Segments are tagged
 * `<testTag>_protein|carbs|fat`.
 */
@Composable
fun MacroStackedBar(
    proteinPct: Int,
    carbsPct: Int,
    fatPct: Int,
    modifier: Modifier = Modifier,
    testTag: String = "macro_stacked_bar",
) {
    val colors = AppTheme.colors
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
            .height(MacroStackedBarDefaults.Height)
            .clip(AppShapes.full)
            .background(colors.surfaceOverlay)
            .testTag(testTag)
            .semantics(mergeDescendants = true) { stateDescription = spoken },
    ) {
        listOf(Macro.Protein to proteinPct, Macro.Carbs to carbsPct, Macro.Fat to fatPct).forEach { (macro, pct) ->
            if (pct > 0) {
                Box(
                    Modifier
                        .weight(pct.toFloat())
                        .fillMaxHeight()
                        .background(MacroDefaults.color(macro, colors))
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
