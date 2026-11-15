package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import kotlinx.datetime.LocalDate
import kotlin.math.floor

/** At or above this share of the target cardio minutes and steps read "Superado" (07 §3 "Weekly goal rings"). */
private const val OVER_RATIO = 1.1

/** Without planned sessions, cardio may lag the straight-line pace by this share before it reads "Por detrás". */
private const val PACE_SLACK = 0.1
private const val EPSILON = 1e-9

/** A daily average this close to its target still reads "En camino". */
private const val AVERAGE_NEAR = 0.9

/**
 * Where a weekly goal of the "Tu bloque" bars stands (05 §6.0f): the bar colour, its glyph and the word all come from
 * it, so colour is never the only cue (R-41). [Idle] shows no word (nothing to judge yet).
 */
internal enum class WeekGoalState { Behind, OnTrack, Done, Over, Idle }

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
 * The share of the week's target due by now: the whole sessions the [planned] count asks for after the [elapsed]
 * share of the week (floor(planned * elapsed) / planned), or the straight line [elapsed] when nothing is planned.
 */
internal fun dueShare(elapsed: Float?, planned: Int): Float? = elapsed?.let {
    if (planned > 0) floor(planned * it) / planned else it
}

/**
 * The week's cardio minutes ([done] of [target]) against the [due] share (07 §3 "Weekly goal rings: over target and
 * pace"): paced by the planned sessions with no slack, or the straight line minus 10 % when [planned] is 0.
 */
internal fun cardioGoalState(done: Int, target: Int, due: Float?, planned: Int): WeekGoalState {
    if (target <= 0) return WeekGoalState.Idle
    val ratio = done.toDouble() / target
    val slack = if (planned > 0) 0.0 else PACE_SLACK
    return when {
        ratio >= OVER_RATIO -> WeekGoalState.Over
        ratio >= 1.0 -> WeekGoalState.Done
        due == null || due <= 0f -> if (done > 0) WeekGoalState.OnTrack else WeekGoalState.Idle
        done + EPSILON >= target * due * (1 - slack) -> WeekGoalState.OnTrack
        else -> WeekGoalState.Behind
    }
}

/** A daily average ([avg], null without data) against its daily [target]: no pace, the average is already per day. */
internal fun averageGoalState(avg: Int?, target: Int): WeekGoalState {
    if (avg == null || target <= 0) return WeekGoalState.Idle
    val ratio = avg.toDouble() / target
    return when {
        ratio >= OVER_RATIO -> WeekGoalState.Over
        ratio >= 1.0 -> WeekGoalState.Done
        ratio >= AVERAGE_NEAR -> WeekGoalState.OnTrack
        else -> WeekGoalState.Behind
    }
}
