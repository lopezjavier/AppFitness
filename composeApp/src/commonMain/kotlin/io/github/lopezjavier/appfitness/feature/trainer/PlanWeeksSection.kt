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
import io.github.lopezjavier.appfitness.composeapp.resources.block_week_paused
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_request
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_request_offline
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.plan_week_cardio
import io.github.lopezjavier.appfitness.composeapp.resources.plan_week_steps
import io.github.lopezjavier.appfitness.composeapp.resources.plan_week_steps_none
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_end
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_chip_current
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_chip_deload
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
import io.github.lopezjavier.appfitness.designsystem.AppLinearProgress
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.PlanWeekSession
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
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
) {
    if (weeks.isEmpty()) return
    Column(Modifier.testTag("trainer_weeks_section"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ListSectionHeader(stringResource(Res.string.trainer_weeks_header))
        BlockPauseLines(pause)
        weeks.forEach { WeekCard(it) }
        if (request.available) {
            GhostButton(
                text = stringResource(Res.string.coach_deload_request),
                onClick = request.onRequest,
                modifier = Modifier.testTag("trainer_deload_request"),
                enabled = request.online && !request.busy,
                tone = GhostTone.Neutral,
                loading = request.busy,
            )
            if (!request.online) OfflineHint(stringResource(Res.string.coach_deload_request_offline))
        }
    }
}

/** "Semana en pausa" while the block is paused and, once any week was paused, the moved end date (US-106). */
@Composable
private fun BlockPauseLines(pause: BlockPause?) {
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
        if (pause.pausedWeeks > 0 && end != null) {
            Text(
                text = stringResource(Res.string.trainer_block_end, AppDateFormat.title(end)),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("trainer_block_end"),
            )
        }
    }
}

/** State of "Hacer descarga la próxima semana": shown only when [available]; disabled offline or while [busy]. */
internal data class DeloadRequestUi(
    val available: Boolean = false,
    val busy: Boolean = false,
    val online: Boolean = true,
    val onRequest: () -> Unit = {},
)

@Composable
private fun WeekCard(week: PlanWeek) {
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
