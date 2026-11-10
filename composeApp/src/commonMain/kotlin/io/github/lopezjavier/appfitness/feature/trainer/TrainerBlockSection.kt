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
import io.github.lopezjavier.appfitness.composeapp.resources.action_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_card_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_title
import io.github.lopezjavier.appfitness.composeapp.resources.block_week_paused
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_create
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_proposed_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_proposed_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_action
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
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SummaryCard
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
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
        )

        is BlockSummary.Saved -> Unit
    }
}

/** The saved block on "Tu bloque": its card and "Eliminar bloque" with the confirmation. */
@Composable
internal fun BlockDetailsSection(
    block: BlockSummary,
    online: Boolean,
    removeBlockConfirm: Boolean,
    onEvent: (TrainerEvent) -> Unit,
) {
    if (block !is BlockSummary.Saved) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
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
    hasAppliedChanges: Boolean,
    onOpen: () -> Unit,
) {
    if (status !is BlockStatus.Saved) {
        // Applied coach changes live on "Tu bloque": without a saved block the box still reaches them.
        if (hasAppliedChanges) {
            SummaryCard(stringResource(Res.string.trainer_block_title), emptyList(), onOpen, "trainer_box_block")
        }
        return
    }
    val line = when {
        status.reviewDue -> stringResource(Res.string.trainer_block_status_ended)
        status.paused -> stringResource(Res.string.block_week_paused)
        status.week != null -> stringResource(Res.string.trainer_block_status_week, status.week, status.total)
        else -> stringResource(Res.string.trainer_block_status_upcoming)
    }
    val adherence = currentWeek?.takeIf { status.adherence != null }?.let {
        stringResource(Res.string.trainer_week_progress, it.completed, it.planned, it.adherencePct)
    }
    val deload = if (deloadPlanned) stringResource(Res.string.trainer_box_block_deload) else null
    SummaryCard(
        title = stringResource(Res.string.trainer_block_title),
        lines = listOfNotNull(line, adherence, deload),
        onClick = onOpen,
        testTag = "trainer_box_block",
    )
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
