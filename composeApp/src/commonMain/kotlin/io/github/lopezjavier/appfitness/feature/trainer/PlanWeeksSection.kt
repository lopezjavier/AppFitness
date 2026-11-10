package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.action_done
import io.github.lopezjavier.appfitness.composeapp.resources.block_week_paused
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_help_title
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_request
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_request_offline
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.plan_week_cardio
import io.github.lopezjavier.appfitness.composeapp.resources.plan_week_steps
import io.github.lopezjavier.appfitness.composeapp.resources.plan_week_steps_none
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_end
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_end_busy
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_end_edit_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_end_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_end_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_end_help_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_end_offline
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_chip_current
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_chip_deload
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_chip_diet_break
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_planned
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_progress
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_session_done
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_session_missed
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_session_pending
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_session_planned
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_weeks_header
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppDatePickerDialog
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppLinearProgress
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.PlanWeekSession
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The weeks of the saved block (US-73, US-75): one card per week with its date span, the "Actual" / "Descarga"
 * chips, "3 de 4 entrenos · 75 %" and one status row per planned routine. Absent when there are no weeks.
 */
@Composable
internal fun PlanWeeksSection(
    weeks: List<PlanWeek>,
    request: DeloadRequestUi = DeloadRequestUi(),
    pause: BlockPause? = null,
    dietBreakWeek: Int? = null,
    extend: ExtendBlockUi = ExtendBlockUi(),
) {
    if (weeks.isEmpty()) return
    Column(Modifier.testTag("trainer_weeks_section"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ListSectionHeader(stringResource(Res.string.trainer_weeks_header))
        BlockPauseLines(pause, extend)
        weeks.forEach { WeekCard(it, dietBreak = it.week == dietBreakWeek) }
        if (request.available) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton(
                    text = stringResource(Res.string.coach_deload_request),
                    onClick = request.onRequest,
                    modifier = Modifier.weight(1f).testTag("trainer_deload_request"),
                    enabled = request.online && !request.busy,
                    tone = GhostTone.Neutral,
                    loading = request.busy,
                )
                HelpButton(
                    contentDescription = stringResource(Res.string.coach_deload_help_cd),
                    title = stringResource(Res.string.coach_deload_help_title),
                    body = stringResource(Res.string.coach_deload_help_body),
                    testTag = "trainer_deload_help",
                )
            }
            if (!request.online) OfflineHint(stringResource(Res.string.coach_deload_request_offline))
        }
    }
}

/**
 * "Semana en pausa" while the block is paused and the block's last day with its pencil (US-106, US-134): the pencil
 * and its help show only while the block can still be changed; offline it is disabled with the standard hint.
 */
@Composable
private fun BlockPauseLines(pause: BlockPause?, extend: ExtendBlockUi) {
    if (pause == null) return
    val end = pause.blockEnd
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (pause.paused) {
            Row {
                StatusChip(
                    stringResource(Res.string.block_week_paused),
                    StatusTone.Caution,
                    Modifier.testTag("trainer_block_paused"),
                )
            }
        }
        if (end != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.trainer_block_end, AppDateFormat.title(end)),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f).testTag("trainer_block_end"),
                )
                if (pause.extendable) {
                    HelpButton(
                        contentDescription = stringResource(Res.string.trainer_block_end_help_cd),
                        title = stringResource(Res.string.trainer_block_end_help_title),
                        body = stringResource(Res.string.trainer_block_end_help_body),
                        testTag = "trainer_block_end_help",
                    )
                    AppIconButton(
                        contentDescription = stringResource(Res.string.trainer_block_end_edit_cd),
                        icon = Res.drawable.ic_edit,
                        testTag = "trainer_block_end_edit",
                        onClick = extend.onOpen,
                        enabled = extend.online && !extend.busy,
                        busyDescription = if (extend.busy) stringResource(Res.string.trainer_block_end_busy) else null,
                    )
                }
            }
            if (pause.extendable && !extend.online) OfflineHint(stringResource(Res.string.trainer_block_end_offline))
            if (pause.extendable && extend.pickerOpen) ExtendBlockPicker(pause, extend)
        }
    }
}

/** The date picker of the block's last day: selectable from `minEnd` to `maxEnd`, starting on the current end. */
@Composable
private fun ExtendBlockPicker(pause: BlockPause, extend: ExtendBlockUi) {
    AppDatePickerDialog(
        selected = pause.blockEnd,
        onConfirm = extend.onConfirm,
        onDismiss = extend.onDismiss,
        confirmLabel = stringResource(Res.string.action_done),
        dismissLabel = stringResource(Res.string.action_cancel),
        maxDate = pause.maxEnd,
        minDate = pause.minEnd,
        testTagPrefix = "trainer_block_end_picker",
    )
}

/** State of the block's last-day pencil: the picker is [pickerOpen]; disabled offline or while [busy]. */
internal data class ExtendBlockUi(
    val pickerOpen: Boolean = false,
    val busy: Boolean = false,
    val online: Boolean = true,
    val onOpen: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onConfirm: (LocalDate) -> Unit = {},
)

/** State of "Hacer descarga la próxima semana": shown only when [available]; disabled offline or while [busy]. */
internal data class DeloadRequestUi(
    val available: Boolean = false,
    val busy: Boolean = false,
    val online: Boolean = true,
    val onRequest: () -> Unit = {},
)

@Composable
private fun WeekCard(week: PlanWeek, dietBreak: Boolean) {
    AppCard(Modifier.fillMaxWidth().testTag("trainer_week_${week.week}")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.trainer_week_title, week.week),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                )
                if (week.phase == WeekPhase.Current) {
                    StatusChip(
                        stringResource(Res.string.trainer_week_chip_current),
                        StatusTone.Good,
                        Modifier.testTag("trainer_week_${week.week}_current"),
                    )
                }
                if (dietBreak) {
                    StatusChip(
                        stringResource(Res.string.trainer_week_chip_diet_break),
                        StatusTone.Good,
                        Modifier.testTag("trainer_block_week_diet_break"),
                    )
                }
                if (week.deload) {
                    StatusChip(
                        stringResource(Res.string.trainer_week_chip_deload),
                        StatusTone.Caution,
                        Modifier.testTag("trainer_week_${week.week}_deload"),
                    )
                }
            }
            Text(
                text = AppDateFormat.dayRange(week.startDate, week.endDate),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
            Text(
                text = if (week.phase == WeekPhase.Upcoming) {
                    pluralStringResource(Res.plurals.trainer_week_planned, week.planned, week.planned)
                } else {
                    stringResource(Res.string.trainer_week_progress, week.completed, week.planned, week.adherencePct)
                },
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.testTag("trainer_week_${week.week}_adherence"),
            )
            WeekCardio(week)
            WeekSteps(week)
            week.sessions.forEach { SessionRow(week, it) }
        }
    }
}

/** "Cardio de la semana: 100 de 150 min" with its bar; hidden for a plan without a weekly cardio target. */
@Composable
private fun WeekCardio(week: PlanWeek) {
    val target = week.cardioMinTarget?.takeIf { it > 0 } ?: return
    // An upcoming week has nothing logged yet: it shows the target against 0.
    val done = if (week.phase == WeekPhase.Upcoming) 0 else week.cardioMinDone
    val text = stringResource(Res.string.plan_week_cardio, done, target)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = text,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.testTag("plan_week_cardio_value_${week.week}"),
        )
        AppLinearProgress(
            fraction = done.toFloat() / target,
            stateDescription = text,
            modifier = Modifier.testTag("plan_week_cardio_bar_${week.week}"),
        )
    }
}

/** "Pasos: media 7.800 de 7.000", or "Sin datos de pasos"; current and past weeks only, hidden without a target. */
@Composable
private fun WeekSteps(week: PlanWeek) {
    val target = week.stepsTarget ?: return
    if (week.phase == WeekPhase.Upcoming) return
    val average = week.stepsAvg
    Text(
        text = if (average == null) {
            stringResource(Res.string.plan_week_steps_none)
        } else {
            stringResource(
                Res.string.plan_week_steps,
                AppNumberFormat.integer(average),
                AppNumberFormat.integer(target),
            )
        },
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textPrimary,
        modifier = Modifier.testTag("plan_week_steps_value_${week.week}"),
    )
}

/** Status row (no chevron): a check when done, otherwise text; a missed past workout is neutral, never an error. */
@Composable
private fun SessionRow(week: PlanWeek, session: PlanWeekSession) {
    val tag = "trainer_week_${week.week}_session_${session.sessionIndex}"
    AppListRow(
        headline = session.name,
        modifier = Modifier.testTag(tag),
        trailing = {
            if (session.done) {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = stringResource(Res.string.trainer_week_session_done),
                    tint = AppTheme.colors.electricGreen,
                    modifier = Modifier.size(Spacing.xl).testTag("${tag}_done"),
                )
            } else {
                Text(
                    text = stringResource(
                        when (week.phase) {
                            WeekPhase.Past -> Res.string.trainer_week_session_missed
                            WeekPhase.Current -> Res.string.trainer_week_session_pending
                            WeekPhase.Upcoming -> Res.string.trainer_week_session_planned
                        },
                    ),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("${tag}_pending"),
                )
            }
        },
    )
}
