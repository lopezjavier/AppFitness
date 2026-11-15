package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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
import io.github.lopezjavier.appfitness.composeapp.resources.report_sessions_cd
import io.github.lopezjavier.appfitness.composeapp.resources.report_sessions_headline
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_changes_applied
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_cardio_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_headline_unit
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_session_cd_done
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_session_cd_pending
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_starts_chip
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_state_behind
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_state_done
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_state_on_track
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_state_over
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_status_ended
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_status_week
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_label
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_none
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_weeks_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_fact_cardio
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_chip_deload
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_session_pending
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppLinearProgress
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTextButton
import io.github.lopezjavier.appfitness.designsystem.AppTextButtonTone
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.InlineNoteTone
import io.github.lopezjavier.appfitness.designsystem.NonBreaking
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.SegmentedProgress
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusChipDefaults
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.designsystem.SummaryCard
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.PlanWeekSession
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100.0
private const val STACK_FONT_SCALE = 1.2f
private const val SEGMENTS_STAGGER = 2
private const val CARDIO_STAGGER = 3
private const val STEPS_STAGGER = 4

/** Past the target the bar's scale grows to the done figure, never beyond twice the target (the marker stays clear). */
private const val MAX_OVER_SCALE = 2f
private val BarHeight = Spacing.sm

/** What the "Tu bloque" card of the root needs: where a tap goes and the retry event of its weeks note. */
internal class BlockCardActions(val onOpen: () -> Unit, val onEvent: (TrainerEvent) -> Unit)

/**
 * "Tu bloque" card on the Trainer root (C-124, R-103): a summary only. The week chip, the week segments, "2 de 4
 * entrenos esta semana" with the percent chip, the sessions as small cards and the cardio and steps bars; every action
 * (extend the week, "Mi entrenamiento", "Eliminar bloque") lives in the "Tu bloque" screen it opens. [weeks] holds the
 * block's weeks (empty when their read failed: the card then shows a retry note). Without a saved block it only shows
 * while applied changes exist (the proposal has its own card). [today] places the cardio pace marker (05 §6.0f).
 */
@Composable
internal fun BlockSummaryBox(
    status: BlockStatus,
    weeks: List<PlanWeek>,
    appliedChangesCount: Int,
    actions: BlockCardActions,
    modifier: Modifier = Modifier,
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
        content = { BlockBody(status, weeks, shown, current, today) },
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
private fun BlockBody(
    status: BlockStatus.Saved,
    weeks: List<PlanWeek>,
    shown: PlanWeek?,
    current: PlanWeek?,
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
        shown?.takeIf { it.planned > 0 }?.let { week ->
            SessionsHeadline(week)
            SessionCards(week)
        }
        shown?.let { ActivityBars(it, today) }
    }
}

/**
 * "2 de 4" with " entrenos esta semana" and the percent chip at the end: Info while the week is open, Good (with its
 * check glyph) once every planned session is done; sessions never read "Superado" (07 §3 (c)), the chip caps at Good.
 */
@Composable
private fun SessionsHeadline(week: PlanWeek) {
    val percent = (week.completed * PERCENT / week.planned).roundToInt()
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(Res.string.report_sessions_headline, week.completed, week.planned),
                style = AppTheme.typography.headlineMetric,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.testTag("trainer_box_block_headline"),
            )
            Text(
                text = stringResource(Res.string.trainer_block_headline_unit),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        StatusChip(
            text = NonBreaking.join(percent.toString(), "%"),
            tone = if (week.completed >= week.planned) StatusTone.Good else StatusTone.Info,
            modifier = Modifier.testTag("trainer_box_block_percent"),
        )
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
            Icon(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = null,
                tint = AppTheme.colors.electricGreen,
                modifier = Modifier.size(Spacing.lg),
            )
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

/**
 * "Cardio" and "Pasos al día" with an 8 dp bar each (05 §6.0f): the bar colour, a glyph and the state word say where
 * the goal stands (07 §3 "Weekly goal rings: over target and pace"); cardio carries the "where you should be by now"
 * marker while open, and past the target either bar fills completely and shows the target as a marker. A row without
 * a target is omitted; steps without data read muted.
 */
@Composable
private fun ActivityBars(week: PlanWeek, today: LocalDate?) {
    val cardioTarget = week.cardioMinTarget?.takeIf { it > 0 }
    val stepsTarget = week.stepsTarget?.takeIf { it > 0 }
    if (cardioTarget == null && stepsTarget == null) return
    // More air above and between the bars than in the sessions grid: the two goals read as their own group.
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        cardioTarget?.let { target ->
            val due = dueShare(elapsedShare(week, today), week.planned)
            val state = cardioGoalState(week.cardioMinDone, target, due, week.planned)
            ActivityBar(
                label = stringResource(Res.string.trainer_plan_fact_cardio),
                value = stringResource(
                    Res.string.trainer_block_cardio_value,
                    AppNumberFormat.integer(week.cardioMinDone),
                    AppNumberFormat.integer(target),
                ),
                ratio = week.cardioMinDone.toFloat() / target,
                state = state,
                pace = due?.takeIf { state == WeekGoalState.Behind || state == WeekGoalState.OnTrack },
                stagger = CARDIO_STAGGER,
                tag = "trainer_box_block_cardio",
            )
        }
        stepsTarget?.let { target ->
            val avg = week.stepsAvg
            ActivityBar(
                label = stringResource(Res.string.trainer_block_steps_label),
                value = if (avg == null) {
                    stringResource(Res.string.trainer_block_steps_none)
                } else {
                    stringResource(
                        Res.string.trainer_block_steps_value,
                        AppNumberFormat.integer(avg),
                        AppNumberFormat.integer(target),
                    )
                },
                ratio = avg?.let { it.toFloat() / target },
                state = averageGoalState(avg, target),
                // An average has no pace to keep: no marker while open.
                pace = null,
                stagger = STEPS_STAGGER,
                tag = "trainer_box_block_steps",
            )
        }
    }
}

private fun WeekGoalState.tone(): StatusTone? = when (this) {
    WeekGoalState.Behind -> StatusTone.Caution
    WeekGoalState.OnTrack -> StatusTone.Info
    WeekGoalState.Done, WeekGoalState.Over -> StatusTone.Good
    WeekGoalState.Idle -> null
}

@Composable
private fun WeekGoalState.word(): String? = when (this) {
    WeekGoalState.Behind -> stringResource(Res.string.trainer_block_state_behind)
    WeekGoalState.OnTrack -> stringResource(Res.string.trainer_block_state_on_track)
    WeekGoalState.Done -> stringResource(Res.string.trainer_block_state_done)
    WeekGoalState.Over -> stringResource(Res.string.trainer_block_state_over)
    WeekGoalState.Idle -> null
}

@Suppress("LongParameterList") // One bar row: each figure is its own slot.
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ActivityBar(
    label: String,
    value: String,
    ratio: Float?,
    state: WeekGoalState,
    pace: Float?,
    stagger: Int,
    tag: String,
) {
    val tone = state.tone()
    val color = tone?.let { StatusChipDefaults.color(it, AppTheme.colors) } ?: AppTheme.colors.athleticCyan
    val word = state.word()
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = label,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                if (word != null && tone != null) {
                    GoalStateWord(word, tone, color, Modifier.align(Alignment.CenterVertically))
                }
            }
            Text(
                text = value,
                style = AppTheme.typography.bodyMedium,
                fontWeight = if (ratio == null) FontWeight.Normal else FontWeight.W600,
                color = if (ratio == null) AppTheme.colors.textTertiary else AppTheme.colors.textPrimary,
            )
        }
        if (ratio != null) {
            // Past the target the scale stretches to the done figure: the fill is full and the marker is the target.
            val over = ratio > 1f
            val scale = ratio.coerceIn(1f, MAX_OVER_SCALE)
            AppLinearProgress(
                fraction = if (over) 1f else ratio,
                stateDescription = listOfNotNull(label, value, word).joinToString(", "),
                fillColor = color,
                barHeight = BarHeight,
                staggerIndex = stagger,
                markerFraction = if (over) 1f / scale else pace?.takeIf { it > 0f && it < 1f },
                modifier = Modifier.testTag("${tag}_bar"),
            )
        }
    }
}

/** The state in words in the bar's colour, after the glyph of its `StatusChip` tone when it has one (R-98). */
@Composable
private fun GoalStateWord(word: String, tone: StatusTone, color: Color, modifier: Modifier) {
    Row(
        modifier.testTag("trainer_box_block_goal_state"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChipDefaults.icon(tone)?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(StatusChipDefaults.IconSize),
            )
        }
        Text(text = word, style = AppTheme.typography.bodySmall, color = color)
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
