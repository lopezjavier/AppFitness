package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_accept
import io.github.lopezjavier.appfitness.composeapp.resources.action_discard
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_accept_offline
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_data
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_data_short
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_payload
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_reason_faster
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_reason_slower
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_title
import io.github.lopezjavier.appfitness.composeapp.resources.deload_accept_offline
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_create
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_proposed_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_proposed_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_saved_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_deload_data
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_deload_payload
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_deload_reason
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_deload_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_active_line
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_active_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_outcomes
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_pending_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_pending_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_section
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_edited
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CoachProposalCard
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.domain.model.CalorieChange
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.DeloadProposal
import io.github.lopezjavier.appfitness.feature.weight.signedTenths
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private const val HALF = 2

/** The block entry under an open plan: "Crear bloque", the proposed block to review, or the saved one. */
@Composable
internal fun BlockSection(plan: PlanSummary, block: BlockSummary, online: Boolean, onOpenBlock: (String) -> Unit) {
    val planId = when (plan) {
        is PlanSummary.Proposed -> plan.plan.id
        is PlanSummary.Accepted -> plan.plan.id
        PlanSummary.None -> return
    }
    when (block) {
        BlockSummary.None -> {
            PrimaryButton(
                text = stringResource(Res.string.trainer_block_create),
                onClick = { onOpenBlock(planId) },
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

        is BlockSummary.Saved -> AppCard(Modifier.fillMaxWidth().testTag("trainer_block_card")) {
            Text(
                text = stringResource(Res.string.trainer_block_saved_title),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
        }
    }
}

/** "Tu plan" on the Trainer tab: create, review the pending proposal or see the active plan. */
@Composable
internal fun PlanSection(plan: PlanSummary, online: Boolean, onOpenPlan: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ListSectionHeader(stringResource(Res.string.trainer_plan_section))
        when (plan) {
            PlanSummary.None -> {
                PrimaryButton(
                    text = stringResource(Res.string.trainer_plan_create),
                    onClick = onOpenPlan,
                    modifier = Modifier.fillMaxWidth().testTag("trainer_create_plan"),
                    enabled = online,
                )
                if (!online) OfflineHint(stringResource(Res.string.trainer_plan_create_action))
            }

            is PlanSummary.Proposed -> AppCard(
                Modifier.fillMaxWidth()
                    .testTag("trainer_plan_card")
                    .clickable(role = Role.Button, onClick = onOpenPlan),
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

            is PlanSummary.Accepted -> ActivePlanCard(plan.plan)
        }
    }
}

@Composable
private fun ActivePlanCard(plan: CoachPlan) {
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

/** The easier-week suggestion waiting for a decision (US-75), on the shared [CoachProposalCard]. */
@Composable
internal fun DeloadCard(deload: DeloadProposal, deciding: Boolean, online: Boolean, onEvent: (TrainerEvent) -> Unit) {
    CoachProposalCard(
        title = stringResource(Res.string.trainer_deload_title),
        payload = stringResource(Res.string.trainer_deload_payload, deload.week),
        reason = stringResource(Res.string.trainer_deload_reason),
        dataUsed = stringResource(Res.string.trainer_deload_data),
        acceptText = stringResource(Res.string.action_accept),
        discardText = stringResource(Res.string.action_discard),
        onAccept = { onEvent(TrainerEvent.DecideDeload(accept = true)) },
        onDiscard = { onEvent(TrainerEvent.DecideDeload(accept = false)) },
        testTagPrefix = "trainer_deload_card",
        busy = deciding,
        acceptEnabled = online,
        footer = if (online) null else ({ OfflineHint(stringResource(Res.string.deload_accept_offline)) }),
    )
}

/** The in-block calorie change waiting for a decision (US-69), on the shared [CoachProposalCard]. */
@Composable
internal fun CalorieChangeCard(
    change: CalorieChange,
    deciding: Boolean,
    online: Boolean,
    onEvent: (TrainerEvent) -> Unit,
) {
    val slope = change.slopeKgWeek
    val low = change.intervalLow
    val high = change.intervalHigh
    // Calories go up when the weight drops faster than planned, down when it drops slower.
    val reason = if (change.toKcal > change.fromKcal) {
        Res.string.calorie_change_reason_faster
    } else {
        Res.string.calorie_change_reason_slower
    }
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
        reason = stringResource(reason),
        dataUsed = data,
        acceptText = stringResource(Res.string.action_accept),
        discardText = stringResource(Res.string.action_discard),
        onAccept = { onEvent(TrainerEvent.DecideCalorieChange(accept = true)) },
        onDiscard = { onEvent(TrainerEvent.DecideCalorieChange(accept = false)) },
        testTagPrefix = "trainer_calorie_card",
        busy = deciding,
        acceptEnabled = online,
        footer = if (online) null else ({ OfflineHint(stringResource(Res.string.calorie_change_accept_offline)) }),
    )
}
