package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_card_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_title
import io.github.lopezjavier.appfitness.composeapp.resources.block_week_paused
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_create
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_proposed_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_proposed_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_message
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_status_ended
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_status_upcoming
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_status_week
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_box_empty_block
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HubDimensions
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SummaryCard
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The block entry on the Trainer root: "Crear bloque" or the proposed block to review. */
@Composable
internal fun BlockEntrySection(
    plan: PlanSummary,
    block: BlockSummary,
    online: Boolean,
    onOpenBlock: (String) -> Unit,
    onOpenBeforeStart: (BeforeStartMode, String?, String?) -> Unit,
    modifier: Modifier = Modifier,
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
            BlockEmptyCard(modifier) {
                PrimaryButton(
                    text = stringResource(Res.string.trainer_block_create),
                    onClick = { onOpenBeforeStart(BeforeStartMode.Block, planId, null) },
                    modifier = Modifier.fillMaxWidth().testTag("trainer_create_block"),
                    enabled = online,
                )
                if (!online) OfflineHint(stringResource(Res.string.trainer_block_create_action))
            }
        }

        is BlockSummary.Proposed -> SummaryCard(
            title = stringResource(Res.string.trainer_block_proposed_title),
            lines = listOf(stringResource(Res.string.trainer_block_proposed_supporting)),
            onClick = { onOpenBlock(planId) },
            testTag = "trainer_block_card",
            modifier = modifier,
            highlighted = true,
        )

        is BlockSummary.Saved -> Unit
    }
}

/** "Semana 2 de 4" for the current week, or the status line while there is none (paused, ended, not started). */
@Composable
internal fun BlockStatus.Saved.weekLine(): String = when {
    reviewDue -> stringResource(Res.string.trainer_block_status_ended)
    paused -> stringResource(Res.string.block_week_paused)
    week != null -> stringResource(Res.string.trainer_block_status_week, week, total)
    else -> stringResource(Res.string.trainer_block_status_upcoming)
}

/**
 * The block entry without a block (C-129 a): a card with the dumbbell glyph in a 72 dp circle, the empty line and the
 * "Crear bloque" button inside it. [content] is the button (and its offline hint).
 */
@Composable
private fun BlockEmptyCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AppCard(modifier.fillMaxWidth().testTag("trainer_block_empty")) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                Modifier.size(BlockEmptyCircle).clip(AppShapes.full).background(AppTheme.colors.surfaceOverlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_fitness_center),
                    contentDescription = null,
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(Spacing.xl + Spacing.sm),
                )
            }
            Text(
                text = stringResource(Res.string.trainer_box_empty_block),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            content()
        }
    }
}

private val BlockEmptyCircle = HubDimensions.EmptyGlyphCircle

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

/**
 * The "Eliminar bloque" confirmation (R-08); the action is in the ⋮ of the "Tu bloque" card of the root (R-103, R-105).
 */
@Composable
internal fun RemoveBlockDialog(onEvent: (TrainerEvent) -> Unit) {
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
