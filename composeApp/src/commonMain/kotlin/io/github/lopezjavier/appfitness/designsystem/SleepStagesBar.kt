@file:Suppress("MatchingDeclarationName") // The file holds the component, its segment model and its defaults.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The phases a sleep session is split into; colours come from the theme tokens. */
enum class SleepStageKind { Awake, Light, Deep, Rem, Sleeping, Unknown }

/** One phase of the bar: [minutes] set its width; [label] and [durationText] make its legend line. */
data class SleepStageSegment(val kind: SleepStageKind, val minutes: Int, val label: String, val durationText: String)

/** Geometry of [SleepStagesBar]. */
object SleepStagesBarDefaults {
    val Height: Dp = 12.dp

    fun segmentTag(testTag: String, kind: SleepStageKind): String = "${testTag}_${kind.name.lowercase()}"

    @Composable
    fun color(kind: SleepStageKind): Color = when (kind) {
        SleepStageKind.Awake -> AppTheme.colors.energyAmber
        SleepStageKind.Light -> AppTheme.colors.athleticCyan
        SleepStageKind.Deep -> AppTheme.colors.neonLime
        SleepStageKind.Rem -> AppTheme.colors.electricGreen
        SleepStageKind.Sleeping -> AppTheme.colors.athleticCyan
        SleepStageKind.Unknown -> AppTheme.colors.ghostData
    }
}

/**
 * The distribution of a night's sleep: one horizontal bar split by stage (width proportional to minutes) and a text
 * legend with a colour swatch per stage, so colour is never the only cue. The bar is decorative; the whole component
 * is one node whose state description is [description] ("Profundo 1 h 20 min, Ligero 3 h, ..."). Segments are tagged
 * `<testTag>_<kind>`. Stages with no minutes are skipped.
 */
@Composable
fun SleepStagesBar(
    segments: List<SleepStageSegment>,
    description: String,
    modifier: Modifier = Modifier,
    testTag: String = "sleep_stages_bar",
) {
    val shown = segments.filter { it.minutes > 0 }
    Column(
        modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics(mergeDescendants = true) { stateDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(SleepStagesBarDefaults.Height)
                .clip(AppShapes.full)
                .background(AppTheme.colors.surfaceOverlay)
                .clearAndSetSemantics {},
        ) {
            shown.forEach { segment ->
                Box(
                    Modifier
                        .weight(segment.minutes.toFloat())
                        .fillMaxHeight()
                        .background(SleepStagesBarDefaults.color(segment.kind))
                        .testTag(SleepStagesBarDefaults.segmentTag(testTag, segment.kind)),
                )
            }
        }
        shown.forEach { segment ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(Spacing.sm)
                        .clip(AppShapes.full)
                        .background(SleepStagesBarDefaults.color(segment.kind))
                        .clearAndSetSemantics {},
                )
                Text(
                    text = segment.label,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = segment.durationText,
                    style = AppTheme.typography.labelNumeric,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SleepStagesBarPreview() {
    AppTheme {
        SleepStagesBar(
            segments = listOf(
                SleepStageSegment(SleepStageKind.Deep, 80, "Profundo", "1 h 20 min"),
                SleepStageSegment(SleepStageKind.Light, 240, "Ligero", "4 h"),
                SleepStageSegment(SleepStageKind.Rem, 90, "REM", "1 h 30 min"),
                SleepStageSegment(SleepStageKind.Awake, 20, "Despierto", "20 min"),
            ),
            description = "Profundo 1 h 20 min, Ligero 4 h, REM 1 h 30 min, Despierto 20 min",
        )
    }
}
