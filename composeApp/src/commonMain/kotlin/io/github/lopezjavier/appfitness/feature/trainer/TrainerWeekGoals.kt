package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import kotlinx.datetime.LocalDate
import kotlin.math.floor

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
 * Drawn as the cardio pace tick; it never colours the bar (the goal bars are always cyan, 05 §6.0g).
 */
internal fun dueShare(elapsed: Float?, planned: Int): Float? = elapsed?.let {
    if (planned > 0) floor(planned * it) / planned else it
}
