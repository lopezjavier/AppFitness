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
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_apply
import io.github.lopezjavier.appfitness.composeapp.resources.action_not_now
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_data
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_data_short
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_payload
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_title
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_proposal_cut_lower
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_proposal_cut_raise
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_proposal_gain_lower
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_proposal_gain_raise
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_proposal_hold_lower
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_proposal_hold_raise
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_proposal_offline
import io.github.lopezjavier.appfitness.composeapp.resources.phase_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_pending_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_pending_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_replace
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CoachProposalCard
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SummaryCard
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import io.github.lopezjavier.appfitness.domain.usecase.WeightPace
import io.github.lopezjavier.appfitness.feature.weight.signedTenths
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private const val HALF = 2

/**
 * "Tu plan" on the Trainer root: "Crear plan" without a plan, the pending proposal, or (accepted) the [PlanCard] with
 * the weight pace and the Calorías row; the accepted card opens nothing, its actions are in its ⋮ [menu] (R-105).
 */
@Suppress("LongParameterList") // The plan, the figures of the accepted card and the root's events.
@Composable
internal fun PlanEntrySection(
    plan: PlanSummary,
    online: Boolean,
    onOpenProposal: () -> Unit,
    onOpenBeforeStart: (BeforeStartMode, String?, String?) -> Unit,
    weightPace: WeightPace? = null,
    weeksInPhase: PhaseWeeks? = null,
    diaryCalories: DiaryCalories? = null,
    modifier: Modifier = Modifier,
    menu: (@Composable () -> Unit)? = null,
) {
    when (plan) {
        // Without a plan the root shows the centred empty state with "Crear plan" (C-111).
        PlanSummary.None -> Unit

        is PlanSummary.Proposed -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SummaryCard(
                title = stringResource(Res.string.trainer_plan_pending_title),
                lines = listOf(stringResource(Res.string.trainer_plan_pending_supporting)),
                onClick = onOpenProposal,
                testTag = "trainer_plan_card",
                highlighted = true,
            )
            ReplacePlanEntry(plan.plan.id, online, onOpenBeforeStart)
        }

        is PlanSummary.Accepted -> PlanCard(
            plan = plan.plan,
            pace = weightPace,
            weeksInPhase = weeksInPhase,
            diaryCalories = diaryCalories,
            modifier = modifier,
            menu = menu,
        )
    }
}

/** "Crear plan nuevo" under the current plan: opens "Antes de empezar", which says what it replaces. */
@Composable
private fun ReplacePlanEntry(
    planId: String,
    online: Boolean,
    onOpenBeforeStart: (BeforeStartMode, String?, String?) -> Unit,
) {
    GhostButton(
        text = stringResource(Res.string.trainer_plan_replace),
        onClick = { onOpenBeforeStart(BeforeStartMode.Plan, null, planId) },
        modifier = Modifier.fillMaxWidth().testTag("trainer_create_plan_new"),
        enabled = online,
    )
    if (!online) OfflineHint(stringResource(Res.string.trainer_plan_create_action))
}

/**
 * The phase's reason on the root "Tu plan" card (R-105): the one-sentence reason ("Quieres perder grasa…") with the
 * "?" that explains the phase. The phase name is already the card's goal heading, so it is not repeated here.
 */
@Composable
internal fun PhaseReasonLine(plan: CoachPlan) {
    val phase = plan.phase
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(PhaseText.reason(phase, plan.nutrition.phaseReason)),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.weight(1f).testTag("trainer_box_plan_reason"),
        )
        HelpButton(
            contentDescription = stringResource(Res.string.phase_help_cd),
            title = stringResource(PhaseText.label(phase)),
            body = stringResource(PhaseText.help(phase)),
            testTag = "trainer_box_plan_reason_help",
        )
    }
}

/** Copy variant of a calorie proposal, chosen by the direction of the change and the sign of the plan's rate. */
internal fun calorieProposalReason(proposal: CalorieProposal): StringResource {
    val raise = proposal.toKcal > proposal.fromKcal
    val rate = proposal.rateKgWeek ?: 0.0
    return when {
        rate < 0.0 -> if (raise) Res.string.calorie_proposal_cut_raise else Res.string.calorie_proposal_cut_lower
        rate > 0.0 -> if (raise) Res.string.calorie_proposal_gain_raise else Res.string.calorie_proposal_gain_lower
        else -> if (raise) Res.string.calorie_proposal_hold_raise else Res.string.calorie_proposal_hold_lower
    }
}

/** The in-block calorie proposal waiting for "Aplicar" or "Ahora no" (US-99), on the shared [CoachProposalCard]. */
@Composable
internal fun CalorieProposalCard(
    proposal: CalorieProposal,
    deciding: Boolean,
    online: Boolean,
    onEvent: (TrainerEvent) -> Unit,
) = CalorieProposalCard(
    proposal = proposal,
    deciding = deciding,
    online = online,
    onApply = { onEvent(TrainerEvent.ApplyCalorieProposal) },
    onNotNow = { onEvent(TrainerEvent.DeclineCalorieProposal) },
)

/**
 * The same card with its own actions and tag prefix (the end-of-block review reuses it with `block_review_calorie`);
 * [failure] is drawn under the buttons, after the offline hint.
 */
@Composable
@Suppress("LongParameterList") // Proposal, state, two actions and the host's tag prefix and failure slot.
internal fun CalorieProposalCard(
    proposal: CalorieProposal,
    deciding: Boolean,
    online: Boolean,
    onApply: () -> Unit,
    onNotNow: () -> Unit,
    testTagPrefix: String = "trainer_calorie_card",
    failure: (@Composable () -> Unit)? = null,
) {
    val slope = proposal.slopeKgWeek
    val low = proposal.intervalLow
    val high = proposal.intervalHigh
    val reason = calorieProposalReason(proposal)
    val data = when {
        slope != null && low != null && high != null ->
            stringResource(
                Res.string.calorie_change_data,
                signedTenths(slope),
                signedTenths(abs(high - low) / HALF).removePrefix("+"),
            )

        slope != null -> stringResource(Res.string.calorie_change_data_short, signedTenths(slope))

        else -> ""
    }
    CoachProposalCard(
        title = stringResource(Res.string.calorie_change_title),
        payload = stringResource(
            Res.string.calorie_change_payload,
            AppNumberFormat.integer(proposal.fromKcal),
            AppNumberFormat.integer(proposal.toKcal),
        ),
        reason = stringResource(reason, AppNumberFormat.integer(abs(proposal.toKcal - proposal.fromKcal))),
        dataUsed = data,
        acceptText = stringResource(Res.string.action_apply),
        discardText = stringResource(Res.string.action_not_now),
        onAccept = onApply,
        onDiscard = onNotNow,
        testTagPrefix = testTagPrefix,
        busy = deciding,
        acceptEnabled = online,
        footer = if (online && failure == null) {
            null
        } else {
            {
                if (!online) OfflineHint(stringResource(Res.string.calorie_proposal_offline))
                failure?.invoke()
            }
        },
    )
}
