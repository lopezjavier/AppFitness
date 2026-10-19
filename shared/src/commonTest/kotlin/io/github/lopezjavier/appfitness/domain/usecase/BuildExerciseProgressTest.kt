package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.MaxRepsPoint
import io.github.lopezjavier.appfitness.domain.model.ProgressMode
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildExerciseProgressTest {
    private val today = LocalDate(2026, 10, 19)

    private fun point(date: LocalDate, best: Double?, volume: Double = 1000.0) =
        ExerciseHistoryPoint("s-$date", date, best, volume)

    private fun reps(date: LocalDate, max: Int) = MaxRepsPoint("s-$date", date, max)

    @Test
    fun rangeFromSubtractsCalendarMonths() {
        assertEquals(LocalDate(2026, 9, 19), ProgressRange.OneMonth.from(today))
        assertEquals(LocalDate(2026, 7, 19), ProgressRange.ThreeMonths.from(today))
        assertEquals(LocalDate(2026, 4, 19), ProgressRange.SixMonths.from(today))
        assertEquals(LocalDate(2025, 10, 19), ProgressRange.OneYear.from(today))
    }

    @Test
    fun theCutOffDateIsIncludedAndTheDayBeforeIsNot() {
        val history = listOf(
            point(LocalDate(2026, 9, 18), 90.0),
            point(LocalDate(2026, 9, 19), 100.0),
            point(LocalDate(2026, 10, 18), 110.0),
        )

        val progress = BuildExerciseProgress(history, emptyList(), ProgressRange.OneMonth, today)

        assertEquals(listOf(100.0, 110.0), progress.primary.map { it.value })
        assertEquals(listOf(1000.0, 1000.0), progress.volume.map { it.value })
    }

    @Test
    fun longerRangesIncludeOlderSessions() {
        val history = listOf(point(LocalDate(2026, 5, 1), 90.0), point(LocalDate(2026, 10, 18), 110.0))

        assertEquals(
            1,
            BuildExerciseProgress(history, emptyList(), ProgressRange.ThreeMonths, today).primary.size,
        )
        assertEquals(
            2,
            BuildExerciseProgress(history, emptyList(), ProgressRange.OneYear, today).primary.size,
        )
    }

    @Test
    fun aSingleSessionIsNotEnoughToShowEvolution() {
        val progress = BuildExerciseProgress(
            listOf(point(LocalDate(2026, 10, 18), 110.0)),
            emptyList(),
            ProgressRange.OneMonth,
            today,
        )

        assertFalse(progress.hasEnoughData)
        assertFalse(progress.isEmpty)
        assertEquals(110.0, progress.primary.single().value)
    }

    @Test
    fun noSessionsGivesAnEmptyProgress() {
        val progress = BuildExerciseProgress(emptyList(), emptyList(), ProgressRange.OneMonth, today)

        assertTrue(progress.isEmpty)
        assertFalse(progress.hasEnoughData)
    }

    @Test
    fun exercisesLoggedWithoutLoadPlotMaxRepsAndNoVolume() {
        val history = listOf(point(LocalDate(2026, 10, 1), null, 0.0), point(LocalDate(2026, 10, 10), null, 0.0))
        val maxReps = listOf(reps(LocalDate(2026, 10, 1), 8), reps(LocalDate(2026, 10, 10), 10))

        val progress = BuildExerciseProgress(history, maxReps, ProgressRange.OneMonth, today)

        assertEquals(ProgressMode.MaxReps, progress.mode)
        assertEquals(listOf(8.0, 10.0), progress.primary.map { it.value })
        assertTrue(progress.volume.isEmpty())
        assertTrue(progress.hasEnoughData)
    }

    @Test
    fun maxRepsRespectTheRange() {
        val history = listOf(point(LocalDate(2026, 10, 10), null, 0.0))
        val maxReps = listOf(reps(LocalDate(2026, 8, 1), 6), reps(LocalDate(2026, 10, 10), 10))

        val progress = BuildExerciseProgress(history, maxReps, ProgressRange.OneMonth, today)

        assertEquals(listOf(10.0), progress.primary.map { it.value })
    }

    @Test
    fun mixedLoadedAndBodyweightSessionsStayInOneRepMaxMode() {
        val history = listOf(point(LocalDate(2026, 10, 1), null, 0.0), point(LocalDate(2026, 10, 10), 40.0))
        val maxReps = listOf(reps(LocalDate(2026, 10, 1), 8), reps(LocalDate(2026, 10, 10), 6))

        val progress = BuildExerciseProgress(history, maxReps, ProgressRange.OneMonth, today)

        assertEquals(ProgressMode.OneRepMax, progress.mode)
        assertEquals(listOf(40.0), progress.primary.map { it.value })
    }
}
