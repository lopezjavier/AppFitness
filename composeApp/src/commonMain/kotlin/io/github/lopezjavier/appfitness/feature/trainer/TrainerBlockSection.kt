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
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SegmentedProgress
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SummaryCard
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.report_sessions_headline
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_headline_unit
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_starts
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_todo
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_week_complete
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_box_empty_block
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_chip_deload
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import org.jetbrains.compose.resources.painterResource
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
    modifier: Modifier = Modifier,
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
 * "Tu bloque" box on the Trainer root (US-120, C-124): the week chip, "2 de 4 entrenos esta semana" with one segment per
 * workout, what is left to do and the cardio and steps progress; opens the block screen. [weeks] holds the block's
 * weeks (empty when their read failed: the box then keeps its two lines). Without a saved block it only shows while
 * applied changes exist (the proposal has its own card).
 */
@Composable
internal fun BlockSummaryBox(
    status: BlockStatus,
    weeks: List<PlanWeek>,
    appliedChangesCount: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
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
                modifier = modifier,
            )
        }
        return
    }
    val current = weeks.firstOrNull { it.phase == WeekPhase.Current }
    val first = weeks.minByOrNull { it.week }
    // The week the figures describe: the current one, or the first while the block has not started.
    val shown = if (status.reviewDue || status.paused) null else current ?: first?.takeIf { it.phase == WeekPhase.Upcoming }
    val withHeadline = shown?.takeIf { it.planned > 0 }
    SummaryCard(
        title = stringResource(Res.string.trainer_block_title),
        lines = blockLines(status, shown, current, weeks.isEmpty()),
        onClick = onOpen,
        testTag = "trainer_box_block",
        modifier = modifier,
        headline = withHeadline?.let { stringResource(Res.string.report_sessions_headline, it.completed, it.planned) },
        headlineUnit = withHeadline?.let { stringResource(Res.string.trainer_block_headline_unit) },
        facts = weekActivityFacts(shown),
        status = if (weeks.isEmpty()) {
            null
        } else {
            { BlockChips(status.weekLine(), status, deload = current?.deload == true && !status.paused) }
        },
        progress = withHeadline?.let { week ->
            { SessionSegments(week.completed, week.planned, "trainer_box_block_progress", BLOCK_SEGMENTS_STAGGER) }
        },
    )
}

/** The text under the segments: what is left this week, "Semana completa.", the start date, or the two-line fallback. */
@Composable
private fun blockLines(status: BlockStatus.Saved, shown: PlanWeek?, current: PlanWeek?, noWeeks: Boolean): List<String> =
    when {
        // The weeks could not be read: today's two lines (title and week line).
        noWeeks -> listOf(status.weekLine())

        shown == null -> emptyList()

        shown == current -> {
            val todo = shown.sessions.filter { !it.done }.map { it.name }
            when {
                todo.isNotEmpty() -> listOf(stringResource(Res.string.trainer_block_todo, todo.joinToString(" · ")))
                shown.planned > 0 -> listOf(stringResource(Res.string.trainer_block_week_complete))
                else -> emptyList()
            }
        }

        else -> listOf(
            stringResource(
                Res.string.trainer_block_starts,
                AppDateFormat.weekdayDayMonth(shown.startDate.dayOfWeek, shown.startDate.day, shown.startDate.month),
            ),
        )
    }

/** The status chips of the box: "Semana 1 de 4" (Info) and "Descarga" in a deload week; Neutral for the other states. */
@Composable
private fun BlockChips(line: String, status: BlockStatus.Saved, deload: Boolean) {
    val running = !status.reviewDue && !status.paused && status.week != null
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        StatusChip(line, if (running) StatusTone.Info else StatusTone.Neutral)
        if (deload) StatusChip(stringResource(Res.string.trainer_week_chip_deload), StatusTone.Info)
    }
}

/**
 * The block entry without a block (C-129 a): a card with the dumbbell glyph in a 72 dp circle, the empty line and the
 * "Crear bloque" button inside it. [content] is the button (and its offline hint).
 */
@Composable
private fun BlockEmptyCard(modifier: Modifier, content: @Composable () -> Unit) {
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

private val BlockEmptyCircle = 72.dp

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
