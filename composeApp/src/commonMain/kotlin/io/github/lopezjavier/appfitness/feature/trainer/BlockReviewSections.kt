@file:Suppress("SpreadOperator") // stringResource takes varargs; the argument lists hold at most four short strings.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_outcome_deadline
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcome_not_reached
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcome_reached
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcomes_header
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcomes_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcomes_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.ic_monitor_weight
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.SectionCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Objetivos" (`07` §3 (A6)): one row per objective with its target and "ahora", the deadline as supporting text and a
 * chip that is icon plus words (R-41): "Conseguido" once reached, "No conseguido" only after the deadline, none before.
 * The row's badge turns into a check when reached. Informational: nothing here triggers anything.
 */
@Composable
internal fun OutcomesCard(outcomes: List<OutcomeUi>, names: Map<String, String>) {
    val rows = outcomes.mapNotNull { outcome ->
        val name = if (outcome.kind == OutcomeKind.E1rm) names[outcome.key] else null
        BlockReviewText.outcomeLine(outcome, name)?.let { outcome to it }
    }
    if (rows.isEmpty()) return
    val header = stringResource(Res.string.block_review_outcomes_header)
    SectionCard(
        header,
        testTag = "block_review_section_outcomes",
        help = {
            HelpButton(
                contentDescription = stringResource(Res.string.block_review_outcomes_help_cd),
                title = header,
                body = stringResource(Res.string.block_review_outcomes_help_body),
                testTag = "block_review_help_outcomes",
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            rows.forEachIndexed { index, (outcome, line) -> OutcomeRow(index, outcome, line) }
        }
    }
}

private const val NBSP = ' '

@Composable
internal fun OutcomeRow(index: Int, outcome: OutcomeUi, line: BlockReviewText.Line) {
    val chip: Pair<StringResource, StatusTone>? = when (outcome.chip) {
        OutcomeChip.None -> null
        OutcomeChip.Reached -> Res.string.block_review_outcome_reached to StatusTone.Good
        OutcomeChip.NotReached -> Res.string.block_review_outcome_not_reached to StatusTone.Caution
    }
    AppListRow(
        headline = stringResource(line.res, *line.args.toTypedArray()),
        modifier = Modifier.testTag("block_review_outcome_$index"),
        // R-93: the date never splits ("15 nov"), and the headline wraps instead of ending in an ellipsis.
        supporting = outcome.deadline?.let {
            stringResource(Res.string.block_report_outcome_deadline, AppDateFormat.shortDate(it).replace(' ', NBSP))
        },
        // The badge always shows the kind; the state lives only in the chip (R-41, C-197).
        leading = {
            IconBadge(
                if (outcome.kind ==
                    OutcomeKind.Weight
                ) {
                    Res.drawable.ic_monitor_weight
                } else {
                    Res.drawable.ic_fitness_center
                },
                AppTheme.colors.athleticCyan,
            )
        },
        headlineMaxLines = Int.MAX_VALUE,
        supportingContent = chip?.let { (text, tone) ->
            {
                StatusChip(stringResource(text), tone, Modifier.testTag("block_review_outcome_chip_$index"))
            }
        },
    )
}
