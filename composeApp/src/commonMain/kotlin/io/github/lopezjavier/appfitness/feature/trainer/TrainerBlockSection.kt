package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_card_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_title
import io.github.lopezjavier.appfitness.composeapp.resources.block_week_paused
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_create
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_proposed_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_proposed_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_changes_applied
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_message
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_saved_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_status_ended
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_status_upcoming
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_status_week
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_box_block_deload
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_progress
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SegmentedProgress
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SummaryCard
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The block entry on the Trainer root: "Crear bloque" or the proposed block to review. */
@Composable
internal fun BlockEntrySection(
    plan: PlanSummary,
    block: BlockSummary,
    online: Boolean,
    onOpenBlock: (String) -> Unit,
    onOpenBeforeStart: (BeforeStartMode, String?, String?) -> Unit,
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

        is BlockSummary.Proposed -> SummaryCard(
            title = stringResource(Res.string.trainer_block_proposed_title),
            lines = listOf(stringResource(Res.string.trainer_block_proposed_supporting)),
            onClick = { onOpenBlock(planId) },
            testTag = "trainer_block_card",
            highlighted = true,
        )

        is BlockSummary.Saved -> Unit
    }
}

/**
 * The saved block on "Tu bloque": the header card with the week segments and "Semana 2 de 4" (C-113) and the
 * "Eliminar bloque" confirmation; the action itself lives in the top bar's ⋮ sheet (V-54).
 */
@Composable
internal fun BlockDetailsSection(
    block: BlockSummary,
    status: BlockStatus,
    removeBlockConfirm: Boolean,
    onEvent: (TrainerEvent) -> Unit,
) {
    if (block !is BlockSummary.Saved) return
    AppCard(Modifier.fillMaxWidth().testTag("trainer_block_card")) {
        Text(
            text = stringResource(Res.string.trainer_block_saved_title),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
        )
        (status as? BlockStatus.Saved)?.takeIf { it.total > 0 }?.let { saved ->
            val line = saved.weekLine()
            Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = line,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
                SegmentedProgress(
                    total = saved.total,
                    done = ((saved.week ?: 1) - 1).coerceAtLeast(0),
                    stateDescription = line,
                    current = saved.week,
                    testTag = "trainer_block_weeks_progress",
                )
            }
        }
    }
    if (removeBlockConfirm) RemoveBlockDialog(onEvent)
}

/** "Semana 2 de 4" for the current week, or the status line while there is none (paused, ended, not started). */
@Composable
private fun BlockStatus.Saved.weekLine(): String = when {
    reviewDue -> stringResource(Res.string.trainer_block_status_ended)
    paused -> stringResource(Res.string.block_week_paused)
    week != null -> stringResource(Res.string.trainer_block_status_week, week, total)
    else -> stringResource(Res.string.trainer_block_status_upcoming)
}

/**
 * "Tu bloque" box on the Trainer root (US-120): facts only, derived from [status]; opens the block screen. [currentWeek]
 * only supplies the numbers of the adherence line. Without a saved block it only shows while applied
 * changes exist (the proposal has its own card).
 */
@Composable
internal fun BlockSummaryBox(
    status: BlockStatus,
    currentWeek: PlanWeek?,
    deloadPlanned: Boolean,
    appliedChangesCount: Int,
    onOpen: () -> Unit,
) {
    if (status !is BlockStatus.Saved) {
        // Applied coach changes live on "Tu bloque": without a saved block the box still reaches them (V-50).
        if (appliedChangesCount > 0) {
            SummaryCard(
                title = stringResource(Res.string.trainer_block_title),
                lines = listOf(
                    pluralStringResource(Res.plurals.trainer_block_changes_applied, appliedChangesCount, appliedChangesCount),
                ),
                onClick = onOpen,
                testTag = "trainer_box_block",
            )
        }
        return
    }
    val line = status.weekLine()
    val adherence = currentWeek?.takeIf { status.adherence != null }?.let {
        stringResource(Res.string.trainer_week_progress, it.completed, it.planned, it.adherencePct)
    }
    val deload = if (deloadPlanned) stringResource(Res.string.trainer_box_block_deload) else null
    SummaryCard(
        title = stringResource(Res.string.trainer_block_title),
        lines = listOfNotNull(line, adherence, deload),
        onClick = onOpen,
        testTag = "trainer_box_block",
        progress = status.total.takeIf { it > 0 }?.let { total ->
            {
                SegmentedProgress(
                    total = total,
                    done = ((status.week ?: 1) - 1).coerceAtLeast(0),
                    stateDescription = line,
                    current = status.week,
                    testTag = "trainer_box_block_progress",
                )
            }
        },
    )
}

/** The end-of-block card (US-101): opens "Revisión del bloque"; the title equals the screen title (R-04). */
@Composable
internal fun BlockReviewEntry(onOpen: () -> Unit) {
    SummaryCard(
        title = stringResource(Res.string.block_review_title),
        lines = listOf(stringResource(Res.string.block_review_card_supporting)),
        onClick = onOpen,
        testTag = "trainer_review_card",
        highlighted = true,
    )
}

/** The "Eliminar bloque" confirmation (R-08); the action lives in the top bar's ⋮ sheet of "Tu bloque" (V-54). */
@Composable
private fun RemoveBlockDialog(onEvent: (TrainerEvent) -> Unit) {
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
