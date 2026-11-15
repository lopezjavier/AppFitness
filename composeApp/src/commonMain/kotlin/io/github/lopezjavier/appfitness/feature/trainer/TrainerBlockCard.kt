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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.block_week_paused
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
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
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_session_done
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_starts_chip
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_status_ended
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_status_week
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_label
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_none
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_weeks_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_fact_cardio
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_chip_deload
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_extend_busy
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_extend_edit_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_extend_offline
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_session_pending
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
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
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.designsystem.SummaryCard
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.PlanWeekSession
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100.0
private const val STACK_FONT_SCALE = 1.5f
private const val SEGMENTS_STAGGER = 2
private const val CARDIO_STAGGER = 3
private const val STEPS_STAGGER = 4
private val BarHeight = Spacing.sm

/** What the "Tu bloque" card needs besides the block status and its weeks: the pencil's state and the card's events. */
@Suppress("LongParameterList") // The card's state and its three entries.
internal class BlockCardActions(
    val online: Boolean,
    val extend: ExtendWeekUi,
    val removeConfirm: Boolean,
    val onOpen: () -> Unit,
    val onOpenMyTraining: () -> Unit,
    val onEvent: (TrainerEvent) -> Unit,
)

/**
 * "Tu bloque" card on the Trainer root (C-124, R-103): the week chip with its pencil, the week segments, "2 de 4
 * entrenos esta semana" with the percent chip, the sessions as small cards, the cardio and steps bars and, outside the
 * card's tap target, "Mi entrenamiento" and "Eliminar bloque". [weeks] holds the block's weeks (empty when their read
 * failed: the card then shows a retry note). Without a saved block it only shows while applied changes exist (the
 * proposal has its own card).
 */
@Composable
internal fun BlockSummaryBox(
    status: BlockStatus,
    weeks: List<PlanWeek>,
    appliedChangesCount: Int,
    actions: BlockCardActions,
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
    val extension = if (status.reviewDue || status.paused) null else current?.let { extendableExtension(it, actions.extend) }
    SummaryCard(
        title = stringResource(Res.string.trainer_block_title),
        lines = emptyList(),
        onClick = actions.onOpen,
        testTag = "trainer_box_block",
        modifier = modifier,
        status = if (weeks.isEmpty()) null else ({ MainChip(status) }),
        titleAction = extension?.let { { WeekPencil(actions.extend) } },
        content = { BlockBody(status, weeks, shown, current) },
        footer = { BlockLinks(actions, weeksFailed = weeks.isEmpty()) },
    )
    if (extension != null && actions.extend.pickerOpen) ExtendWeekPicker(extension, actions.extend)
    if (actions.removeConfirm) RemoveBlockDialog(actions.onEvent)
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

/** The pencil of the active week: its own 48 dp target outside the card's tap target (R-103); the same sheet as "Actual". */
@Composable
private fun WeekPencil(extend: ExtendWeekUi) {
    AppIconButton(
        contentDescription = stringResource(Res.string.trainer_week_extend_edit_cd),
        icon = Res.drawable.ic_edit,
        testTag = "trainer_box_block_extend_edit",
        onClick = extend.onOpen,
        enabled = extend.online && !extend.busy,
        busyDescription = if (extend.busy) stringResource(Res.string.trainer_week_extend_busy) else null,
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BlockBody(status: BlockStatus.Saved, weeks: List<PlanWeek>, shown: PlanWeek?, current: PlanWeek?) {
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
        shown?.let { ActivityBars(it) }
    }
}

/** "2 de 4" with " entrenos esta semana" and the percent chip at the end. */
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
            tone = StatusTone.Info,
            modifier = Modifier.testTag("trainer_box_block_percent"),
        )
    }
}

/** The week's sessions as small read-only cards in two columns (one column from font scale 1.5), no tap. */
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
                text = stringResource(Res.string.trainer_block_session_done),
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

/** "Cardio" and "Pasos al día" with an 8 dp bar each; a row without a target is omitted, steps without data read muted. */
@Composable
private fun ActivityBars(week: PlanWeek) {
    week.cardioMinTarget?.takeIf { it > 0 }?.let { target ->
        ActivityBar(
            label = stringResource(Res.string.trainer_plan_fact_cardio),
            value = stringResource(
                Res.string.trainer_block_cardio_value,
                AppNumberFormat.integer(week.cardioMinDone),
                AppNumberFormat.integer(target),
            ),
            fraction = week.cardioMinDone.toFloat() / target,
            stagger = CARDIO_STAGGER,
            tag = "trainer_box_block_cardio",
        )
    }
    week.stepsTarget?.takeIf { it > 0 }?.let { target ->
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
            fraction = avg?.let { it.toFloat() / target },
            stagger = STEPS_STAGGER,
            tag = "trainer_box_block_steps",
        )
    }
}

@Composable
private fun ActivityBar(label: String, value: String, fraction: Float?, stagger: Int, tag: String) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = label,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = AppTheme.typography.bodyMedium,
                fontWeight = if (fraction == null) FontWeight.Normal else FontWeight.W600,
                color = if (fraction == null) AppTheme.colors.textTertiary else AppTheme.colors.textPrimary,
            )
        }
        if (fraction != null) {
            AppLinearProgress(
                fraction = fraction,
                stateDescription = "$label $value",
                barHeight = BarHeight,
                staggerIndex = stagger,
            )
        }
    }
}

/** The card's footer: the weeks note when their read failed, then "Mi entrenamiento" and "Eliminar bloque" (R-103). */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BlockLinks(actions: BlockCardActions, weeksFailed: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (weeksFailed) BlockWeeksError { actions.onEvent(TrainerEvent.Refresh) }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            AppTextButton(
                text = stringResource(Res.string.preferences_my_training_title),
                onClick = actions.onOpenMyTraining,
                modifier = Modifier.testTag("trainer_box_block_my_training"),
            )
            AppTextButton(
                text = stringResource(Res.string.trainer_block_remove),
                onClick = { actions.onEvent(TrainerEvent.RequestRemoveBlock) },
                modifier = Modifier.testTag("trainer_remove_block"),
                enabled = actions.online,
                tone = AppTextButtonTone.Danger,
            )
        }
        if (!actions.online) OfflineHint(stringResource(Res.string.trainer_block_remove_action))
        if (!actions.online && actions.extend.extension != null) {
            OfflineHint(stringResource(Res.string.trainer_week_extend_offline))
        }
    }
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
