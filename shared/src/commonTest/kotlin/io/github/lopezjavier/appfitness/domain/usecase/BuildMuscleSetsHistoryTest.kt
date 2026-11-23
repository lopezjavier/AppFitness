package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.WeeklyMuscleSets
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildMuscleSetsHistoryTest {
    // 2026-08-31 is a Monday: eight consecutive weeks end on 2026-10-19.
    private val first = LocalDate(2026, 8, 31)

    private fun weeks(vararg groupsPerWeek: List<MuscleGroupSets>): List<WeeklyMuscleSets> =
        groupsPerWeek.mapIndexed { i, groups -> WeeklyMuscleSets(first.plus(i, DateTimeUnit.WEEK), groups) }

    private fun eight(last: List<MuscleGroupSets>, firstWeek: List<MuscleGroupSets> = emptyList()) =
        weeks(firstWeek, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), last)

    @Test
    fun weeks_areTheMondaysOfTheInputInOrder() {
        val history = BuildMuscleSetsHistory(eight(emptyList()))

        assertEquals(8, history.weeks.size)
        assertEquals(first, history.weeks.first())
        assertEquals(LocalDate(2026, 10, 19), history.weeks.last())
    }

    @Test
    fun halfSets_areKept() {
        val history = BuildMuscleSetsHistory(eight(listOf(MuscleGroupSets(MuscleGroup.Triceps, 4.5))))

        assertEquals(4.5, history.series.getValue(MuscleGroup.Triceps).last())
    }

    @Test
    fun emptyWeeks_areZeroAndKeepTheGaps() {
        val history = BuildMuscleSetsHistory(
            eight(listOf(MuscleGroupSets(MuscleGroup.Chest, 3.0)), listOf(MuscleGroupSets(MuscleGroup.Chest, 6.5))),
        )

        assertEquals(listOf(6.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 3.0), history.series.getValue(MuscleGroup.Chest))
    }

    @Test
    fun groupWithoutAnySets_isAbsent() {
        val history = BuildMuscleSetsHistory(
            eight(listOf(MuscleGroupSets(MuscleGroup.Chest, 2.0), MuscleGroupSets(MuscleGroup.Back, 0.0))),
        )

        assertEquals(listOf(MuscleGroup.Chest), history.series.keys.toList())
    }

    @Test
    fun series_followTheEnumOrder() {
        val history = BuildMuscleSetsHistory(
            eight(listOf(MuscleGroupSets(MuscleGroup.Quads, 1.0), MuscleGroupSets(MuscleGroup.Chest, 1.0))),
        )

        assertEquals(listOf(MuscleGroup.Chest, MuscleGroup.Quads), history.series.keys.toList())
    }

    @Test
    fun allWeeksEmpty_giveWeeksButNoSeries() {
        val history = BuildMuscleSetsHistory(eight(emptyList()))

        assertEquals(8, history.weeks.size)
        assertTrue(history.series.isEmpty())
    }

    @Test
    fun noWeeks_giveAnEmptyHistory() {
        val history = BuildMuscleSetsHistory(emptyList())

        assertTrue(history.weeks.isEmpty())
        assertTrue(history.series.isEmpty())
    }
}
