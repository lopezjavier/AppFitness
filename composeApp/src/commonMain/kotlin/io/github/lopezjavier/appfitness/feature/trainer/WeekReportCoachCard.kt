package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_headline
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calculate
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fact_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_insights
import io.github.lopezjavier.appfitness.composeapp.resources.ic_pause
import io.github.lopezjavier.appfitness.composeapp.resources.ic_remove
import io.github.lopezjavier.appfitness.composeapp.resources.ic_trending_flat
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_deleted_sessions
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_deleted_weigh_ins
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_missing_food
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_missing_food_past
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_missing_weigh_ins
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_missing_weigh_ins_past
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_on_plan
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_open_card
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_proposal
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_proposal_applied
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_proposal_declined
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_proposal_pending
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_proposal_unanswered
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_sentence
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_sentence_base
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_sentence_empty
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_sentence_join
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_sentence_pace_off
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_sentence_pace_on
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_sentence_up
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_sentence_up_none
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_sentence_weight
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_sentence_weight_same
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_header
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_header
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.semiBold
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The coach card on top of the weekly report (`07` §3 (0)): a lit hero card with the progress sentence and, below it,
 * the first action that applies as one row. The row is tappable only when [onOpen] is given (a diagnosis or stall
 * action of the live report whose card is on the same screen); otherwise it is plain text, such as a decided
 * proposal of a past report reading its outcome ("Propuesta −150 kcal · aplicada").
 */
@Composable
internal fun WeekReportCoachCard(coach: CoachCardUi, onOpen: (() -> Unit)?) {
    AppCard(
        Modifier.fillMaxWidth().testTag("week_summary_coach_card"),
        glow = AppTheme.colors.athleticCyan,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            coach.sentence?.let { sentence ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(Res.drawable.ic_insights, AppTheme.colors.athleticCyan)
                    Text(
                        text = sentenceText(sentence),
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f).testTag("week_summary_coach_sentence"),
                    )
                }
            }
            CoachActionRow(coach.action, onOpen)
        }
    }
}

@Composable
private fun sentenceText(sentence: CoachSentenceUi): String {
    if (sentence.base) return stringResource(Res.string.week_report_sentence_base)
    val body = sentenceBody(sentence)
    return if (body.isEmpty()) {
        stringResource(Res.string.week_report_sentence_empty)
    } else {
        stringResource(Res.string.week_report_sentence, body)
    }
}

@Composable
private fun sentenceBody(sentence: CoachSentenceUi): String {
    val parts = buildList {
        sentence.strengthUp?.let { up ->
            add(
                if (up == 0) {
                    stringResource(Res.string.week_report_sentence_up_none)
                } else {
                    pluralStringResource(Res.plurals.week_report_sentence_up, up, up)
                },
            )
        }
        sentence.weightChangeKg?.let { change ->
            val text = WeekSummaryText.signedKg(change)
            add(
                if (text == "0") {
                    stringResource(Res.string.week_report_sentence_weight_same)
                } else {
                    stringResource(Res.string.week_report_sentence_weight, text)
                },
            )
        }
    }
    val pace = sentence.onPace?.let {
        stringResource(if (it) Res.string.week_report_sentence_pace_on else Res.string.week_report_sentence_pace_off)
    }
    val joined = parts.joinToString(", ")
    return when {
        pace == null -> joined
        joined.isEmpty() -> pace
        else -> stringResource(Res.string.week_report_sentence_join, joined, pace)
    }
}

private class ActionLook(val icon: DrawableResource, val text: String)

@Composable
private fun actionLook(action: WeekReportAction): ActionLook = when (action) {
    is WeekReportAction.PendingProposal ->
        ActionLook(Res.drawable.ic_calculate, proposalText(action.proposal, ProposalStatus.Pending, unanswered = false))

    is WeekReportAction.DecidedProposal -> ActionLook(
        if (action.status == ProposalStatus.Applied) Res.drawable.ic_check else Res.drawable.ic_remove,
        // A stored report whose proposal had no answer reads "sin respuesta", never "pendiente".
        proposalText(action.proposal, action.status, unanswered = action.status == ProposalStatus.Pending),
    )

    WeekReportAction.WeightDiagnosis ->
        ActionLook(Res.drawable.ic_insights, stringResource(Res.string.week_summary_diagnosis_header))

    WeekReportAction.Stall ->
        ActionLook(Res.drawable.ic_trending_flat, stringResource(Res.string.week_summary_stall_header))

    is WeekReportAction.MissingData -> ActionLook(Res.drawable.ic_fact_check, stringResource(missingRes(action)))

    WeekReportAction.Deload -> ActionLook(Res.drawable.ic_pause, stringResource(Res.string.coach_deload_headline))

    WeekReportAction.OnPlan ->
        ActionLook(Res.drawable.ic_check, stringResource(Res.string.week_report_action_on_plan))
}

private fun missingRes(action: WeekReportAction.MissingData): StringResource = when (action.kind) {
    MissingKind.WeighIns -> if (action.past) {
        Res.string.week_report_action_missing_weigh_ins_past
    } else {
        Res.string.week_report_action_missing_weigh_ins
    }

    MissingKind.Food -> if (action.past) {
        Res.string.week_report_action_missing_food_past
    } else {
        Res.string.week_report_action_missing_food
    }

    MissingKind.DeletedSessions -> Res.string.week_report_action_deleted_sessions

    MissingKind.DeletedWeighIns -> Res.string.week_report_action_deleted_weigh_ins
}

@Composable
private fun proposalText(proposal: CalorieProposal, status: ProposalStatus, unanswered: Boolean): String =
    stringResource(
        Res.string.week_report_action_proposal,
        WeekSummaryText.kcalDelta(proposal),
        stringResource(
            when {
                unanswered -> Res.string.week_report_action_proposal_unanswered
                status == ProposalStatus.Applied -> Res.string.week_report_action_proposal_applied
                status == ProposalStatus.Declined -> Res.string.week_report_action_proposal_declined
                else -> Res.string.week_report_action_proposal_pending
            },
        ),
    )

@Composable
private fun CoachActionRow(action: WeekReportAction, onOpen: (() -> Unit)?) {
    val look = actionLook(action)
    val colors = AppTheme.colors
    val openLabel = stringResource(Res.string.week_report_action_open_card)
    val tappable = onOpen != null
    val clickable = if (onOpen != null) {
        Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
    } else {
        Modifier
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .background(colors.surfaceOverlay, AppShapes.md)
            .then(clickable)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {}
            .testTag("week_summary_coach_action"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(look.icon),
            contentDescription = null,
            tint = if (tappable) colors.athleticCyan else colors.textSecondary,
            modifier = Modifier.size(Spacing.xl),
        )
        Text(
            text = look.text,
            style = AppTheme.typography.bodyMedium.semiBold(),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (tappable) {
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(Spacing.xl),
            )
        }
    }
}
