package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit

/** Progress to the target: the bar fill, the visible "48 %" and the spoken state ("48 %, faltan 4,2 kg"). */
@Immutable
data class GoalCardProgress(val fraction: Float, val percentText: String, val stateText: String)

object GoalCardDefaults {
    val BarHeight: Dp = Spacing.sm

    fun trackColor(appColors: AppColors) = appColors.surfaceOverlay

    /** Fill in the neutral accent: reaching a weight is not a macro, so the macro colours stay out. */
    fun fillColor(appColors: AppColors) = appColors.athleticCyan
}

/**
 * The weight goal: [title] and, once a target exists, a compact edit icon in the header ([changeLabel] is its spoken
 * description); the [targetText] with the [distanceText] under it and an optional [progress] bar. Without a target
 * ([targetText] null) it shows [emptyHint] and the [setLabel] button. Tags: `<prefix>_card`, `_set_button`,
 * `_change_button`, `_distance`, `_progress`.
 */
// Every slot of the card is a plain value or event; grouping them would only add a holder type.
@Suppress("LongParameterList")
@Composable
fun GoalCard(
    title: String,
    targetText: String?,
    distanceText: String?,
    progress: GoalCardProgress?,
    emptyHint: String,
    setLabel: String,
    changeLabel: String,
    onSet: () -> Unit,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "goal",
) {
    AppCard(modifier.fillMaxWidth().testTag("${testTagPrefix}_card")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(),
                style = AppTheme.typography.labelTechnical,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (targetText != null) {
                AppIconButton(
                    contentDescription = changeLabel,
                    icon = Res.drawable.ic_edit,
                    testTag = "${testTagPrefix}_change_button",
                    onClick = onChange,
                    tint = AppTheme.colors.textSecondary,
                )
            }
        }
        if (targetText == null) {
            EmptyGoal(emptyHint, setLabel, onSet, testTagPrefix)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = targetText, style = AppTheme.typography.headlineMetric, color = AppTheme.colors.textPrimary)
                distanceText?.let {
                    Text(
                        text = it,
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.testTag("${testTagPrefix}_distance"),
                    )
                }
            }
            progress?.let {
                GoalProgressBar(it, Modifier.padding(top = Spacing.md).testTag("${testTagPrefix}_progress"))
            }
        }
    }
}

@Composable
private fun EmptyGoal(hint: String, setLabel: String, onSet: () -> Unit, prefix: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(text = hint, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        SecondaryButton(
            text = setLabel,
            onClick = onSet,
            modifier = Modifier.fillMaxWidth().testTag("${prefix}_set_button"),
        )
    }
}

@Composable
private fun GoalProgressBar(progress: GoalCardProgress, modifier: Modifier = Modifier) {
    Column(
        modifier.semantics(mergeDescendants = true) {
            stateDescription = progress.stateText
            progressBarRangeInfo = ProgressBarRangeInfo(progress.fraction, 0f..1f)
        },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = progress.percentText,
            style = AppTheme.typography.labelNumeric,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(GoalCardDefaults.BarHeight)
                .clip(AppShapes.full)
                .background(GoalCardDefaults.trackColor(AppTheme.colors))
                .clearAndSetSemantics {},
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.fraction)
                    .fillMaxHeight()
                    .clip(AppShapes.full)
                    .background(GoalCardDefaults.fillColor(AppTheme.colors)),
            )
        }
    }
}

@Preview
@Composable
private fun GoalCardPreview() {
    AppTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            GoalCard(
                title = "Objetivo",
                targetText = "70,0 kg",
                distanceText = "faltan 4,2 kg",
                progress = GoalCardProgress(0.48f, "48 %", "48 %, faltan 4,2 kg"),
                emptyHint = "",
                setLabel = "Fijar objetivo",
                changeLabel = "Cambiar objetivo",
                onSet = {},
                onChange = {},
            )
            GoalCard("Objetivo", null, null, null, "Fija un peso objetivo.", "Fijar objetivo", "Cambiar objetivo", {
            }, {})
        }
    }
}
