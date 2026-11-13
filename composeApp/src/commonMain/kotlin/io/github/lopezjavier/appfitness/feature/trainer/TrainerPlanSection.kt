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
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_box_plan_line
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_active_line
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_active_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_activity
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_diet_break
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_outcomes
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_pending_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_pending_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_phase_line
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_replace
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_section
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_edited
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CoachProposalCard
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.designsystem.SummaryCard
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import io.github.lopezjavier.appfitness.domain.usecase.GoalChecklist
import io.github.lopezjavier.appfitness.feature.weight.signedTenths
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private const val HALF = 2

/**
 * "Tu plan" on the Trainer root: "Crear plan" without a plan, the pending proposal, or (accepted) the box whose lines
 * carry the key figures and that opens "Tu plan".
 */
@Composable
internal fun PlanEntrySection(
    plan: PlanSummary,
    online: Boolean,
    onOpenProposal: () -> Unit,
    onOpenBeforeStart: (BeforeStartMode, String?, String?) -> Unit,
    onOpenPlan: () -> Unit = {},
) {
    when (plan) {
        PlanSummary.None -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ListSectionHeader(stringResource(Res.string.trainer_plan_section))
            PrimaryButton(
                text = stringResource(Res.string.trainer_plan_create),
                onClick = { onOpenBeforeStart(BeforeStartMode.Plan, null, null) },
                modifier = Modifier.fillMaxWidth().testTag("trainer_create_plan"),
                enabled = online,
            )
            if (!online) OfflineHint(stringResource(Res.string.trainer_plan_create_action))
        }

        is PlanSummary.Proposed -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SummaryCard(
                title = stringResource(Res.string.trainer_plan_pending_title),
                lines = listOf(stringResource(Res.string.trainer_plan_pending_supporting)),
                onClick = onOpenProposal,
                testTag = "trainer_plan_card",
            )
            ReplacePlanEntry(plan.plan.id, online, onOpenBeforeStart)
        }

        is PlanSummary.Accepted -> PlanSummaryBox(plan.plan, onOpenPlan)
    }
}

/** The accepted plan's box: phase and calories, then the cardio minutes and the diet-break week when there are any. */
@Composable
private fun PlanSummaryBox(plan: CoachPlan, onOpen: () -> Unit) {
    val nutrition = plan.nutrition
    val headline = stringResource(
        Res.string.trainer_box_plan_line,
        stringResource(PhaseText.label(plan.phase)),
        AppNumberFormat.integer(nutrition.targetKcal),
    )
    val cardio = nutrition.activity?.let {
        stringResource(
            Res.string.trainer_plan_activity,
            AppNumberFormat.integer(it.cardioMinWeek),
            AppNumberFormat.integer(it.stepsDay),
        )
    }
    val dietBreak = nutrition.dietBreak?.let {
        stringResource(Res.string.trainer_plan_diet_break, AppNumberFormat.integer(it.targetKcal))
    }
    SummaryCard(
        title = stringResource(Res.string.trainer_plan_section),
        lines = listOfNotNull(headline, cardio, dietBreak),
        onClick = onOpen,
        testTag = "trainer_box_plan",
    )
}

/** "Tu plan" screen: the active plan with everything around it. */
@Composable
internal fun PlanDetailsSection(
    plan: PlanSummary,
    online: Boolean,
    onOpenBeforeStart: (BeforeStartMode, String?, String?) -> Unit,
    cardioEdit: CardioEdit? = null,
    onEvent: (TrainerEvent) -> Unit = {},
    goalsChecklist: GoalChecklist? = null,
    canStartNewBlock: Boolean = false,
    weeksInPhase: PhaseWeeks? = null,
    reviewDue: Boolean = false,
    dietBreakSaving: Boolean = false,
) {
    if (plan is PlanSummary.None) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ListSectionHeader(stringResource(Res.string.trainer_plan_section))
        when (plan) {
            PlanSummary.None -> Unit

            is PlanSummary.Proposed -> Unit

            is PlanSummary.Accepted -> {
                ActivePlanCard(plan.plan, weeksInPhase, onEditCardio = { onEvent(TrainerEvent.OpenCardioEdit) }) {
                    DietBreakToggle(plan.plan, online, dietBreakSaving) { onEvent(TrainerEvent.ToggleDietBreak(it)) }
                }
                if (plan.plan.nutrition.editedByUser && goalsChecklist != null) GoalsChecklistSection(goalsChecklist)
                cardioEdit?.let {
                    CardioMinutesSheet(
                        edit = it,
                        online = online,
                        onChange = { minutes -> onEvent(TrainerEvent.CardioMinutesChanged(minutes)) },
                        onSave = { onEvent(TrainerEvent.SaveCardio) },
                        onDismiss = { onEvent(TrainerEvent.DismissCardioEdit) },
                    )
                }
                // While the review is due its footer is the only way on: no "Bloque nuevo" entry here.
                if (canStartNewBlock) {
                    if (!reviewDue) NewBlockEntries(online, onEvent)
                } else {
                    ReplacePlanEntry(plan.plan.id, online, onOpenBeforeStart)
                }
            }
        }
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

@Composable
private fun ActivePlanCard(
    plan: CoachPlan,
    weeksInPhase: PhaseWeeks?,
    onEditCardio: () -> Unit,
    dietBreakRow: @Composable () -> Unit,
) {
    val nutrition = plan.nutrition
    AppCard(Modifier.fillMaxWidth().testTag("trainer_plan_card")) {
        Text(
            text = stringResource(Res.string.trainer_plan_active_title),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(
                Res.string.trainer_plan_active_line,
                AppNumberFormat.integer(nutrition.targetKcal),
                signedTenths(nutrition.rateKgWeek),
            ),
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
        )
        PhaseLine(plan)
        nutrition.dietBreak?.let { week ->
            Text(
                text = stringResource(Res.string.trainer_plan_diet_break, AppNumberFormat.integer(week.targetKcal)),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("trainer_plan_diet_break"),
            )
        }
        weeksInPhase?.let { WeeksInPhaseLine(it) }
        dietBreakRow()
        nutrition.activity?.let { PlanCardioRow(it, onEditCardio) }
        if (plan.outcomes.isNotEmpty()) {
            Text(
                text = pluralStringResource(Res.plurals.trainer_plan_outcomes, plan.outcomes.size, plan.outcomes.size),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
        if (nutrition.editedByUser) {
            StatusChip(stringResource(Res.string.trainer_source_edited), StatusTone.Caution)
        }
    }
}

/** One line with the phase and, when it is short, its reason; the "?" explains the phase. */
@Composable
private fun PhaseLine(plan: CoachPlan) {
    val phase = plan.phase
    val reason = plan.nutrition.phaseReason
    val label = stringResource(PhaseText.label(phase))
    val text =
        stringResource(Res.string.trainer_plan_phase_line, label, stringResource(PhaseText.reason(phase, reason)))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.weight(1f).testTag("trainer_plan_phase"),
        )
        HelpButton(
            contentDescription = stringResource(Res.string.phase_help_cd),
            title = label,
            body = stringResource(PhaseText.help(phase)),
            testTag = "trainer_plan_phase_help",
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
