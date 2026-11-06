package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_header
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_missing_food
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import io.github.lopezjavier.appfitness.feature.exercises.label
import org.jetbrains.compose.resources.stringResource

/**
 * "¿Por qué no progreso?" (US-105): one read-only card with a help "?" and, per stalled main lift, its name, the first
 * cause found and one suggested action. A lift waits for its exercise name (R-54); with none left nothing is drawn.
 */
@Composable
internal fun StallCard(
    stalls: List<StallDiagnosis>,
    exerciseNames: Map<String, String>,
    testTagPrefix: String = "week_summary",
) {
    val items = stalls.mapNotNull { stall ->
        exerciseNames[stall.exerciseId]?.let { name -> Triple(stall.exerciseId, name, stall.cause) }
    }
    if (items.isEmpty()) return
    val header = stringResource(Res.string.week_summary_stall_header)
    AppCard(Modifier.fillMaxWidth().testTag("${testTagPrefix}_section_stalls")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ListSectionHeader(header, Modifier.weight(1f))
                HelpButton(
                    contentDescription = stringResource(Res.string.week_summary_stall_help_cd),
                    title = header,
                    body = stringResource(Res.string.week_summary_stall_help_body),
                    testTag = "${testTagPrefix}_stall_help",
                )
            }
            items.forEach { (id, name, cause) -> StallItem(id, name, WeekSummaryText.stallLine(cause), testTagPrefix) }
        }
    }
}

// SpreadOperator: stringResource takes varargs and the argument lists hold at most two short strings.
@Suppress("SpreadOperator")
@Composable
private fun StallItem(exerciseId: String, name: String, line: WeekSummaryText.StallLine, testTagPrefix: String) {
    val groupLabel = line.group?.let {
        stringResource(it.label)
    }?.let { if (line.groupLowercase) it.lowercase() else it }
    val causeArgs = line.causeArgs + listOfNotNull(groupLabel)
    val tag = "${testTagPrefix}_stall_$exerciseId"
    Column(
        Modifier.fillMaxWidth().testTag(tag).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = name,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.testTag("${tag}_name"),
        )
        Text(
            text = stringResource(line.cause, *causeArgs.toTypedArray()),
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.testTag("${tag}_cause"),
        )
        Text(
            text = stringResource(line.action, *line.actionArgs.toTypedArray()),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("${tag}_action"),
        )
        if (line.missingFood) {
            Text(
                text = stringResource(Res.string.week_summary_stall_missing_food),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("${tag}_missing_food"),
            )
        }
    }
}
