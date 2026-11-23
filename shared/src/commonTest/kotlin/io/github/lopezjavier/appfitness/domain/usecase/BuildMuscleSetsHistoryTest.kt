package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.WeeklyMuscleVolumeRow
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildMuscleVolumeHistoryTest {
    // 2026-10-19 is a Monday.
    private val monday = LocalDate(2026, 10, 19)

    private fun row(week: LocalDate, group: MuscleGroup, volume: Double) = WeeklyMuscleVolumeRow(week, group, volume)

    @Test
    fun windowStart_isSevenWeeksBeforeTheCurrentMonday() {
        assertEquals(LocalDate(2026, 8, 31), BuildMuscleVolumeHistory.windowStart(monday))
        assertEquals(LocalDate(2026, 8, 31), BuildMuscleVolumeHistory.windowStart(LocalDate(2026, 10, 25)))
    }

    @Test
    fun firstSessionTwoWeeksAgo_givesThreeWeeks() {
        val history = BuildMuscleVolumeHistory(listOf(row(LocalDate(2026, 10, 5), MuscleGroup.Chest, 10.0)), monday)

        assertEquals(3, history.weeks.size)
        assertEquals(LocalDate(2026, 10, 5), history.weeks.first())
        assertEquals(monday, history.weeks.last())
    }

    @Test
    fun firstSessionThisWeek_givesOneWeek() {
        val history = BuildMuscleVolumeHistory(listOf(row(monday, MuscleGroup.Chest, 10.0)), LocalDate(2026, 10, 25))

        assertEquals(listOf(monday), history.weeks)
    }

    @Test
    fun firstSessionTenWeeksAgo_isCappedAtEightWeeksAndOlderRowsDoNotLeak() {
        val rows = listOf(
            row(LocalDate(2026, 8, 10), MuscleGroup.Chest, 999.0),
            row(monday, MuscleGroup.Chest, 3000.0),
        )

        val history = BuildMuscleVolumeHistory(rows, monday)

        assertEquals(8, history.weeks.size)
        assertEquals(LocalDate(2026, 8, 31), history.weeks.first())
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 3000.0), history.series.getValue(MuscleGroup.Chest))
    }

    @Test
    fun firstSessionOlderThanTheCap_keepsEightWeeksEvenWithZerosInTheOldestOnes() {
        val rows = listOf(row(LocalDate(2026, 1, 5), MuscleGroup.Back, 1.0), row(monday, MuscleGroup.Chest, 5.0))

        assertEquals(8, BuildMuscleVolumeHistory(rows, monday).weeks.size)
    }

    @Test
    fun gapWeekAfterTheFirstSession_isZero() {
        val rows = listOf(row(LocalDate(2026, 10, 5), MuscleGroup.Chest, 7.0), row(monday, MuscleGroup.Chest, 3.0))

        val history = BuildMuscleVolumeHistory(rows, monday)

        assertEquals(listOf(7.0, 0.0, 3.0), history.series.getValue(MuscleGroup.Chest))
    }

    @Test
    fun emptyRows_giveNoWeeksAndNoSeries() {
        val history = BuildMuscleVolumeHistory(emptyList(), monday)

        assertTrue(history.weeks.isEmpty())
        assertTrue(history.series.isEmpty())
    }

    @Test
    fun weeks_endOnTheMondayOfTheCurrentWeekWhenTodayIsSunday() {
        val history =
            BuildMuscleVolumeHistory(
                listOf(row(LocalDate(2026, 8, 31), MuscleGroup.Chest, 1.0)),
                LocalDate(2026, 10, 25),
            )

        assertEquals(monday, history.weeks.last())
        assertEquals(LocalDate(2026, 8, 31), history.weeks.first())
    }

    @Test
    fun series_fillsZeroWeeksAndKeepsTheGaps() {
        val rows = listOf(
            row(LocalDate(2026, 9, 7), MuscleGroup.Chest, 5400.0),
            row(monday, MuscleGroup.Chest, 3000.0),
        )

        val history = BuildMuscleVolumeHistory(rows, monday)

        assertEquals(listOf(5400.0, 0.0, 0.0, 0.0, 0.0, 0.0, 3000.0), history.series.getValue(MuscleGroup.Chest))
    }

    @Test
    fun series_dropsGroupsWithOnlyZeros() {
        val rows = listOf(row(monday, MuscleGroup.Chest, 100.0), row(monday, MuscleGroup.Back, 0.0))

        val history = BuildMuscleVolumeHistory(rows, monday)

        assertEquals(listOf(MuscleGroup.Chest), history.series.keys.toList())
    }

    @Test
    fun series_areOrderedByTheEnum() {
        val rows = listOf(row(monday, MuscleGroup.Quads, 1.0), row(monday, MuscleGroup.Chest, 1.0))

        val history = BuildMuscleVolumeHistory(rows, monday)

        assertEquals(listOf(MuscleGroup.Chest, MuscleGroup.Quads), history.series.keys.toList())
    }

    @Test
    fun series_ignoreRowsOutsideTheWindowOrOffMondays() {
        val rows = listOf(
            row(LocalDate(2026, 8, 24), MuscleGroup.Chest, 999.0),
            row(LocalDate(2026, 10, 26), MuscleGroup.Chest, 999.0),
            row(LocalDate(2026, 10, 20), MuscleGroup.Chest, 999.0),
        )

        assertTrue(BuildMuscleVolumeHistory(rows, monday).series.isEmpty())
    }

    @Test
    fun series_sumRowsOfTheSameGroupAndWeek() {
        val rows = listOf(row(monday, MuscleGroup.Back, 100.0), row(monday, MuscleGroup.Back, 50.0))

        val history = BuildMuscleVolumeHistory(rows, monday)

        assertEquals(150.0, history.series.getValue(MuscleGroup.Back).last())
    }

    @Test
    fun emptyRows_giveEmptySeries() {
        assertTrue(BuildMuscleVolumeHistory(emptyList(), monday).series.isEmpty())
    }
}
