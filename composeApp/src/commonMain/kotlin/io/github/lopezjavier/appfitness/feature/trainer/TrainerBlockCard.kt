package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_done
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.block_week_paused
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_my_training_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_changes_applied
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_session_cd_done
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_session_cd_pending
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_starts_chip
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_status_ended
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_status_week
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_weeks_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_chip_deload
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_session_pending
import io.github.lopezjavier.appfitness.designsystem.AppBorder
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTextButton
import io.github.lopezjavier.appfitness.designsystem.AppTextButtonTone
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.InlineNoteTone
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.SegmentedProgress
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.designsystem.SummaryCard
import io.github.lopezjavier.appfitness.domain.model.PlanActivity
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.PlanWeekSession
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.LocalDate
import androidx.compose.ui.unit.dp

private const val STACK_FONT_SCALE = 1.2f
private val DiscSize = 20.dp
private val DiscIconSize = 14.dp

/** What the "Tu bloque" card of the root needs: where a tap goes and the retry event of its weeks note. */
internal class BlockCardActions(val onOpen: () -> Unit, val onEvent: (TrainerEvent) -> Unit)

/**
 * "Tu bloque" card on the Trainer root (C-124, R-103, R-104): a summary only. The week chip and the week segments
 * (the block), then "Esta semana" with one goal ring each for Entrenos, Cardio and Pasos al día (the only place the
 * root shows cardio and steps; targets fall back to the plan's [activity]) and the sessions as small cards; every action
 * (extend the week, "Mi entrenamiento", "Eliminar bloque") lives in the "Tu bloque" screen it opens. [weeks] holds the
 * block's weeks (empty when their read failed: the card then shows a retry note). Without a saved block it only shows
 * while applied changes exist (the proposal has its own card).
 */
@Composable
internal fun BlockSummaryBox(
    status: BlockStatus,
    weeks: List<PlanWeek>,
    appliedChangesCount: Int,
    actions: BlockCardActions,
    modifier: Modifier = Modifier,
    activity: PlanActivity? = null,
    today: LocalDate? = null,
) {
    if (status !is BlockStatus.Saved) {
        // Applied coach changes live on "Tu bloque": without a saved block the box still reaches them (V-50).
        if (appliedChangesCount > 0) {
            SummaryCard(
                title = stringResource(Res.string.trainer_block_title),
                lines = listOf(
                    pluralStringResource(Res.plurals.trainer_block_changes_applied, appliedChangesCount, appliedChangesCount),
                ),
                onClick = actions.onOpen,
                testTag = "trainer_box_block",
                modifier = modifier,
            )
        }
        return
    }
    val current = weeks.firstOrNull { it.phase == WeekPhase.Current }
    val first = weeks.minByOrNull { it.week }
    // The week the figures describe: the current one, or the first while the block has not started (never when ended).
    val shown = if (status.reviewDue) null else current ?: first?.takeIf { it.phase == WeekPhase.Upcoming }
    SummaryCard(
        title = stringResource(Res.string.trainer_block_title),
        lines = emptyList(),
        onClick = actions.onOpen,
        testTag = "trainer_box_block",
        modifier = modifier,
        status = if (weeks.isEmpty()) null else ({ MainChip(status) }),
        content = { BlockBody(status, weeks, shown, current, activity, today) },
        footer = if (weeks.isEmpty()) ({ BlockWeeksError { actions.onEvent(TrainerEvent.Refresh) } }) else null,
    )
}

/** The state chip: "Semana 1 de 4" (Info), the start date or "Bloque terminado" (Neutral). */
@Composable
private fun MainChip(status: BlockStatus.Saved) {
    val week = status.week
    val (text, tone) = when {
        status.reviewDue -> stringResource(Res.string.trainer_block_status_ended) to StatusTone.Neutral
        week != null -> stringResource(Res.string.trainer_block_status_week, week, status.total) to
            if (status.paused) StatusTone.Neutral else StatusTone.Info
        else -> null to StatusTone.Neutral
    }
    if (text != null) StatusChip(text, tone, Modifier.testTag("trainer_box_block_chip"))
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongParameterList") // The block, its weeks and what the week goals read.
private fun BlockBody(
    status: BlockStatus.Saved,
    weeks: List<PlanWeek>,
    shown: PlanWeek?,
    current: PlanWeek?,
    activity: PlanActivity?,
    today: LocalDate?,
) {
    // The weeks could not be read: the footer shows the retry note instead.
    if (weeks.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val extras = buildList<@Composable () -> Unit> {
            if (status.week == null && !status.reviewDue) {
                shown?.let { week ->
                    add {
                        StatusChip(
                            stringResource(
                                Res.string.trainer_block_starts_chip,
                                AppDateFormat.weekdayDayMonth(
                                    week.startDate.dayOfWeek,
                                    week.startDate.day,
                                    week.startDate.month,
                                ),
                            ),
                            StatusTone.Neutral,
                            Modifier.testTag("trainer_box_block_starts"),
                        )
                    }
                }
            }
            if (status.paused && !status.reviewDue) {
                add {
                    StatusChip(
                        stringResource(Res.string.block_week_paused),
                        StatusTone.Neutral,
                        Modifier.testTag("trainer_box_block_paused"),
                    )
                }
            }
            if (current?.deload == true && !status.paused && !status.reviewDue) {
                add { StatusChip(stringResource(Res.string.trainer_week_chip_deload), StatusTone.Info) }
            }
        }
        if (extras.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                extras.forEach { it() }
            }
        }
        if (status.total > 0) {
            val line = status.weekLine()
            SegmentedProgress(
                total = status.total,
                done = when {
                    status.reviewDue -> status.total
                    status.week != null -> (status.week - 1).coerceAtLeast(0)
                    else -> 0
                },
                stateDescription = line,
                current = if (status.reviewDue) null else status.week,
                testTag = "trainer_box_block_weeks",
            )
        }
        shown?.let { week ->
            // Everything below covers this week only: the goal rings carry the "Esta semana" eyebrow (scope).
            WeekGoals(week, activity, today)
            if (week.planned > 0) SessionCards(week)
        }
    }
}

/** The week's sessions as small read-only cards in two columns (one column from font scale 1.2), no tap. */
@Composable
private fun SessionCards(week: PlanWeek) {
    val columns = if (LocalDensity.current.fontScale >= STACK_FONT_SCALE) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        week.sessions.chunked(columns).forEach { rowSessions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                rowSessions.forEach { SessionCard(week, it, Modifier.weight(1f)) }
                repeat(columns - rowSessions.size) { Row(Modifier.weight(1f)) {} }
            }
        }
    }
}

@Composable
private fun SessionCard(week: PlanWeek, session: PlanWeekSession, modifier: Modifier) {
    val description = stringResource(
        if (session.done) Res.string.trainer_block_session_cd_done else Res.string.trainer_block_session_cd_pending,
        session.name,
    )
    Row(
        modifier
            .heightIn(min = Spacing.minTouchTarget)
            .background(AppTheme.colors.surfaceControl, AppShapes.md)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("trainer_box_block_session_${week.week}_${session.sessionIndex}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionDisc(session.done)
        Text(
            text = session.name,
            style = AppTheme.typography.bodyMedium,
            fontWeight = FontWeight.W600,
            color = AppTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (session.done) {
            Text(
                text = stringResource(Res.string.action_done),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.electricGreen,
            )
        } else {
            Text(
                text = stringResource(Res.string.trainer_week_session_pending),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

/** A 20 dp disc before the name: filled green with a check when done, an empty ring when pending (shape + text). */
@Composable
private fun SessionDisc(done: Boolean) {
    val colors = AppTheme.colors
    Box(
        Modifier
            .size(DiscSize)
            .clip(AppShapes.full)
            .then(
                if (done) {
                    Modifier.background(colors.electricGreen)
                } else {
                    Modifier.border(AppBorder.focus, colors.textTertiary, AppShapes.full)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = null,
                tint = colors.onNeonLime,
                modifier = Modifier.size(DiscIconSize),
            )
        }
    }
}

/**
 * The actions of the "Tu bloque" screen: "Mi entrenamiento" and "Eliminar bloque" (Danger tone); the removal is
 * disabled offline with its hint. The confirmation dialog is rendered here while [removeConfirm] holds.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BlockDetailsActions(
    online: Boolean,
    removeConfirm: Boolean,
    onOpenMyTraining: () -> Unit,
    onEvent: (TrainerEvent) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            AppTextButton(
                text = stringResource(Res.string.preferences_my_training_title),
                onClick = onOpenMyTraining,
                modifier = Modifier.testTag("trainer_box_block_my_training"),
            )
            AppTextButton(
                text = stringResource(Res.string.trainer_block_remove),
                onClick = { onEvent(TrainerEvent.RequestRemoveBlock) },
                modifier = Modifier.testTag("trainer_remove_block"),
                enabled = online,
                tone = AppTextButtonTone.Danger,
            )
        }
        if (!online) OfflineHint(stringResource(Res.string.trainer_block_remove_action))
    }
    if (removeConfirm) RemoveBlockDialog(onEvent)
}

/** The "weeks could not be read" note of the card, tapping it retries (C-138). */
@Composable
private fun BlockWeeksError(onRetry: () -> Unit) {
    InlineNote(
        text = stringResource(Res.string.trainer_block_weeks_error),
        testTag = "trainer_box_block_weeks_error",
        onClick = onRetry,
        onClickLabel = stringResource(Res.string.action_retry),
        tone = InlineNoteTone.Error,
    )
}
