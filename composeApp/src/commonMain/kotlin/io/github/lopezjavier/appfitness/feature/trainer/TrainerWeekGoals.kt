package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.ic_heart
import io.github.lopezjavier.appfitness.composeapp.resources.ic_steps
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_goal_state_behind
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_goal_state_done
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_goal_state_no_data
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_goal_state_not_started
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_goal_state_on_track
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_goal_state_over
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_tile_of_min
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_fact_cardio
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_goal_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_goal_of
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_goal_of_per_day
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_goal_sessions
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_label
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_goals_eyebrow
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GoalProgressState
import io.github.lopezjavier.appfitness.designsystem.GoalRingTile
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.PlanActivity
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

/** At or above this share of the target a goal reads "Superado" (and draws its second lap). */
private const val OVER_RATIO = 1.1

/** A running total may lag the straight-line pace by this share before it reads "Por detrás". */
private const val PACE_SLACK = 0.1

/** A daily average this close to its target still reads "En camino". */
private const val AVERAGE_NEAR = 0.9
private const val STACK_FONT_SCALE = 1.2f
private const val GOALS_STAGGER = 3
private const val NO_VALUE = "–"

/**
 * The share of [week] already behind us on [today]: whole days passed (today not counted, so a Monday is 0 and
 * nothing is behind yet). Null when [today] is unknown or outside the week (not started, already over).
 */
internal fun elapsedShare(week: PlanWeek, today: LocalDate?): Float? {
    if (today == null || week.phase != WeekPhase.Current) return null
    val days = week.endDate.toEpochDays() - week.startDate.toEpochDays() + 1
    val passed = today.toEpochDays() - week.startDate.toEpochDays()
    if (days <= 0 || passed < 0 || passed >= days) return null
    return passed.toFloat() / days
}

/**
 * A running weekly total ([done] of [target]) against the straight-line pace of the [elapsed] week. [discrete] counts
 * (sessions) compare with the whole sessions the pace asks for so far, minutes with the pace minus a 10 % slack.
 */
internal fun runningGoalState(done: Double, target: Double, elapsed: Float?, discrete: Boolean): GoalProgressState {
    if (target <= 0.0) return GoalProgressState.Idle
    val ratio = done / target
    return when {
        ratio >= OVER_RATIO -> GoalProgressState.Over
        ratio >= 1.0 -> GoalProgressState.Done
        elapsed == null -> if (done > 0.0) GoalProgressState.OnTrack else GoalProgressState.Idle
        else -> {
            val pace = target * elapsed
            val needed = if (discrete) floor(pace) else pace * (1 - PACE_SLACK)
            if (done >= needed) GoalProgressState.OnTrack else GoalProgressState.Behind
        }
    }
}

/** A daily average ([avg], null without data) against its daily [target]: no pace, the average is already per day. */
internal fun averageGoalState(avg: Double?, target: Double): GoalProgressState {
    if (avg == null || target <= 0.0) return GoalProgressState.Idle
    val ratio = avg / target
    return when {
        ratio >= OVER_RATIO -> GoalProgressState.Over
        ratio >= 1.0 -> GoalProgressState.Done
        ratio >= AVERAGE_NEAR -> GoalProgressState.OnTrack
        else -> GoalProgressState.Behind
    }
}

private class WeekGoal(
    val label: String,
    val icon: DrawableResource,
    val value: String,
    val target: String,
    val state: GoalProgressState,
    val fraction: Float,
    val pace: Float?,
    val tag: String,
)

/**
 * "Esta semana" in the "Tu bloque" card: the eyebrow with the week's days, then one goal ring per weekly goal
 * (Entrenos, Cardio, Pasos al día) with its state in words. A goal without a target is left out. The targets fall
 * back to the plan's [activity] for a week stored before its own targets existed. Three columns, or one ring per row
 * from font scale 1.2.
 */
@Composable
internal fun WeekGoals(week: PlanWeek, activity: PlanActivity?, today: LocalDate?) {
    val elapsed = elapsedShare(week, today)
    val goals = buildList {
        if (week.planned > 0) {
            add(
                WeekGoal(
                    label = stringResource(Res.string.trainer_week_goal_sessions),
                    icon = Res.drawable.ic_fitness_center,
                    value = week.completed.toString(),
                    target = stringResource(Res.string.trainer_week_goal_of, week.planned.toString()),
                    state = runningGoalState(week.completed.toDouble(), week.planned.toDouble(), elapsed, true),
                    fraction = week.completed.toFloat() / week.planned,
                    pace = elapsed,
                    tag = "trainer_box_block_goal_sessions",
                ),
            )
        }
        val cardioTarget = week.cardioMinTarget?.takeIf { it > 0 } ?: activity?.cardioMinWeek?.takeIf { it > 0 }
        cardioTarget?.let { target ->
            add(
                WeekGoal(
                    label = stringResource(Res.string.trainer_plan_fact_cardio),
                    icon = Res.drawable.ic_heart,
                    value = AppNumberFormat.integer(week.cardioMinDone),
                    target = stringResource(Res.string.trainer_plan_tile_of_min, AppNumberFormat.integer(target)),
                    state = runningGoalState(week.cardioMinDone.toDouble(), target.toDouble(), elapsed, false),
                    fraction = week.cardioMinDone.toFloat() / target,
                    pace = elapsed,
                    tag = "trainer_box_block_cardio",
                ),
            )
        }
        val stepsTarget = week.stepsTarget?.takeIf { it > 0 } ?: activity?.stepsDay?.takeIf { it > 0 }
        stepsTarget?.let { target ->
            val avg = week.stepsAvg
            add(
                WeekGoal(
                    label = stringResource(Res.string.trainer_block_steps_label),
                    icon = Res.drawable.ic_steps,
                    value = avg?.let { AppNumberFormat.integer(it) } ?: NO_VALUE,
                    target = stringResource(Res.string.trainer_week_goal_of_per_day, AppNumberFormat.integer(target)),
                    state = averageGoalState(avg?.toDouble(), target.toDouble()),
                    fraction = (avg ?: 0).toFloat() / target,
                    // An average has no pace to keep: no tick.
                    pace = null,
                    tag = "trainer_box_block_steps",
                ),
            )
        }
    }
    if (goals.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag("trainer_box_block_goals"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        WeekGoalsEyebrow(week)
        val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
        if (stacked) {
            goals.forEachIndexed { index, goal -> WeekGoalTile(goal, index, horizontal = true, Modifier.fillMaxWidth()) }
        } else {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                goals.forEachIndexed { index, goal ->
                    WeekGoalTile(goal, index, horizontal = false, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** "ESTA SEMANA" at the start and the week's days ("26 oct - 1 nov") at the end: the scope of every ring below. */
@Composable
private fun WeekGoalsEyebrow(week: PlanWeek) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.trainer_week_goals_eyebrow).uppercase(),
            style = AppTheme.typography.labelTechnical,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = AppDateFormat.dayRange(week.startDate, week.endDate),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("trainer_box_block_goals_range"),
        )
    }
}

@Composable
private fun WeekGoalTile(goal: WeekGoal, index: Int, horizontal: Boolean, modifier: Modifier) {
    val status = stringResource(statusText(goal.state, goal.value == NO_VALUE))
    WeekGoalTileContent(goal, status, index, horizontal, modifier)
}

@Composable
private fun WeekGoalTileContent(goal: WeekGoal, status: String, index: Int, horizontal: Boolean, modifier: Modifier) {
    GoalRingTile(
        label = goal.label,
        icon = goal.icon,
        value = goal.value,
        target = goal.target,
        state = goal.state,
        statusText = status,
        fraction = goal.fraction,
        description = stringResource(Res.string.trainer_week_goal_cd, goal.label, goal.value, goal.target, status),
        modifier = modifier,
        paceFraction = goal.pace,
        horizontal = horizontal,
        staggerIndex = GOALS_STAGGER + index,
        testTag = goal.tag,
    )
}

private fun statusText(state: GoalProgressState, noData: Boolean): StringResource = when (state) {
    GoalProgressState.Behind -> Res.string.trainer_goal_state_behind
    GoalProgressState.OnTrack -> Res.string.trainer_goal_state_on_track
    GoalProgressState.Done -> Res.string.trainer_goal_state_done
    GoalProgressState.Over -> Res.string.trainer_goal_state_over
    GoalProgressState.Idle ->
        if (noData) Res.string.trainer_goal_state_no_data else Res.string.trainer_goal_state_not_started
}
