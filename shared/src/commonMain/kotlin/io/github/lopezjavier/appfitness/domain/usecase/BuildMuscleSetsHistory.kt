package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleSetsHistory
import io.github.lopezjavier.appfitness.domain.model.WeeklyMuscleSets

/**
 * Turns the `weekly_muscle_sets` weeks (already consecutive, oldest first, empty weeks included) into one series per
 * muscle group, zero-filled. Groups without any sets in the window are dropped and the series follow the
 * [MuscleGroup] order.
 */
object BuildMuscleSetsHistory {
    operator fun invoke(weeks: List<WeeklyMuscleSets>): MuscleSetsHistory {
        val series = MuscleGroup.entries
            .associateWith { group ->
                weeks.map { week -> week.groups.filter { it.muscleGroup == group }.sumOf { it.sets } }
            }
            .filterValues { values -> values.any { it > 0.0 } }
        return MuscleSetsHistory(weeks.map { it.weekStart }, series)
    }
}
