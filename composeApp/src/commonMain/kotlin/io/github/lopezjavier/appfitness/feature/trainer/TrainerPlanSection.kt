package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_apply
import io.github.lopezjavier.appfitness.composeapp.resources.action_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.action_not_now
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_card_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_title
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
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_create
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_proposed_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_proposed_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_message
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_saved_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_active_line
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_active_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_activity
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_cardio_edit_label
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_outcomes
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_pending_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_pending_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_phase_line
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_replace
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_section
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_edited
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
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

/** The block entry under an open plan: "Crear bloque", the proposed block to review, or the saved one. */
@Composable
internal fun BlockSection(
    plan: PlanSummary,
    block: BlockSummary,
    online: Boolean,
    onOpenBlock: (String) -> Unit,
    onOpenBeforeStart: (BeforeStartMode, String?, String?) -> Unit,
    removeBlockConfirm: Boolean = false,
    onEvent: (TrainerEvent) -> Unit = {},
    reviewDue: Boolean = false,
) {
    val planId = when (plan) {
        is PlanSummary.Proposed -> plan.plan.id
        is PlanSummary.Accepted -> plan.plan.id
        PlanSummary.None -> return
    }
    when (block) {
        // The review footer ("Crear el siguiente bloque") replaces the button while the review is due.
        BlockSummary.None -> if (!reviewDue) {
            PrimaryButton(
                text = stringResource(Res.string.trainer_block_create),
                onClick = { onOpenBeforeStart(BeforeStartMode.Block, planId, null) },
                modifier = Modifier.fillMaxWidth().testTag("trainer_create_block"),
                enabled = online,
            )
            if (!online) OfflineHint(stringResource(Res.string.trainer_block_create_action))
        }

        is BlockSummary.Proposed -> AppCard(
            Modifier.fillMaxWidth()
                .testTag("trainer_block_card")
                .clickable(role = Role.Button) { onOpenBlock(planId) },
        ) {
            Text(
                text = stringResource(Res.string.trainer_block_proposed_title),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.trainer_block_proposed_supporting),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }

        is BlockSummary.Saved -> {
            AppCard(Modifier.fillMaxWidth().testTag("trainer_block_card")) {
                Text(
                    text = stringResource(Res.string.trainer_block_saved_title),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                )
            }
            SavedBlockActions(online, removeBlockConfirm, onEvent)
        }
    }
}

/** The end-of-block card (US-101): opens "Revisión del bloque"; the title equals the screen title (R-04). */
@Composable
internal fun BlockReviewEntry(onOpen: () -> Unit) {
    AppCard(
        Modifier.fillMaxWidth()
            .testTag("trainer_review_card")
            .clickable(role = Role.Button, onClick = onOpen),
    ) {
        Text(
            text = stringResource(Res.string.block_review_title),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(Res.string.block_review_card_supporting),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
    }
}

/** "Eliminar bloque" under the saved block card (R-19), with its confirmation (R-08). */
@Composable
private fun SavedBlockActions(online: Boolean, removeBlockConfirm: Boolean, onEvent: (TrainerEvent) -> Unit) {
    GhostButton(
        text = stringResource(Res.string.trainer_block_remove),
        onClick = { onEvent(TrainerEvent.RequestRemoveBlock) },
        modifier = Modifier.fillMaxWidth().testTag("trainer_remove_block"),
        enabled = online,
        tone = GhostTone.Destructive,
    )
    if (!online) OfflineHint(stringResource(Res.string.trainer_block_remove_action))
    if (removeBlockConfirm) {
        AppConfirmDialog(
            title = stringResource(Res.string.trainer_block_remove_title),
            message = stringResource(Res.string.trainer_block_remove_message),
            confirmLabel = stringResource(Res.string.action_delete),
            cancelLabel = stringResource(Res.string.action_cancel),
            onConfirm = { onEvent(TrainerEvent.ConfirmRemoveBlock) },
            onDismiss = { onEvent(TrainerEvent.DismissRemoveBlock) },
            testTagPrefix = "trainer_remove_block_dialog",
        )
    }
}

/** "Tu plan" on the Trainer tab: create, review the pending proposal or see the active plan. */
@Composable
internal fun PlanSection(
    plan: PlanSummary,
    online: Boolean,
    onOpenProposal: () -> Unit,
    onOpenBeforeStart: (BeforeStartMode, String?, String?) -> Unit,
    cardioEdit: CardioEdit? = null,
    onEvent: (TrainerEvent) -> Unit = {},
    goalsChecklist: GoalChecklist? = null,
    canStartNewBlock: Boolean = false,
    weeksInPhase: PhaseWeeks? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ListSectionHeader(stringResource(Res.string.trainer_plan_section))
        when (plan) {
            PlanSummary.None -> {
                PrimaryButton(
                    text = stringResource(Res.string.trainer_plan_create),
                    onClick = { onOpenBeforeStart(BeforeStartMode.Plan, null, null) },
                    modifier = Modifier.fillMaxWidth().testTag("trainer_create_plan"),
                    enabled = online,
                )
                if (!online) OfflineHint(stringResource(Res.string.trainer_plan_create_action))
            }

            is PlanSummary.Proposed -> {
                AppCard(
                    Modifier.fillMaxWidth()
                        .testTag("trainer_plan_card")
                        .clickable(role = Role.Button, onClick = onOpenProposal),
                ) {
                    Text(
                        text = stringResource(Res.string.trainer_plan_pending_title),
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary,
                    )
                    Text(
                        text = stringResource(Res.string.trainer_plan_pending_supporting),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                    )
                }
                ReplacePlanEntry(plan.plan.id, online, onOpenBeforeStart)
            }

            is PlanSummary.Accepted -> {
                ActivePlanCard(plan.plan, weeksInPhase, onEditCardio = { onEvent(TrainerEvent.OpenCardioEdit) })
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
                if (canStartNewBlock) {
                    NewBlockEntries(online, onEvent)
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
        tone = GhostTone.Neutral,
    )
    if (!online) OfflineHint(stringResource(Res.string.trainer_plan_create_action))
}

@Composable
private fun ActivePlanCard(plan: CoachPlan, weeksInPhase: PhaseWeeks?, onEditCardio: () -> Unit) {
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
        weeksInPhase?.let { WeeksInPhaseLine(it) }
        nutrition.activity?.let { activity ->
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(min = Spacing.minTouchTarget)
                    .testTag("trainer_plan_cardio_row")
                    .clickable(
                        onClickLabel = stringResource(Res.string.trainer_plan_cardio_edit_label),
                        role = Role.Button,
                        onClick = onEditCardio,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(
                        Res.string.trainer_plan_activity,
                        AppNumberFormat.integer(activity.cardioMinWeek),
                        AppNumberFormat.integer(activity.stepsDay),
                    ),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.testTag("trainer_plan_activity"),
                )
            }
        }
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
    change: CalorieProposal,
    deciding: Boolean,
    online: Boolean,
    onEvent: (TrainerEvent) -> Unit,
) = CalorieProposalCard(
    change = change,
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
    change: CalorieProposal,
    deciding: Boolean,
    online: Boolean,
    onApply: () -> Unit,
    onNotNow: () -> Unit,
    testTagPrefix: String = "trainer_calorie_card",
    failure: (@Composable () -> Unit)? = null,
) {
    val slope = change.slopeKgWeek
    val low = change.intervalLow
    val high = change.intervalHigh
    val reason = calorieProposalReason(change)
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
            AppNumberFormat.integer(change.fromKcal),
            AppNumberFormat.integer(change.toKcal),
        ),
        reason = stringResource(reason, AppNumberFormat.integer(abs(change.toKcal - change.fromKcal))),
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
