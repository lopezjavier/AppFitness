package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.MaxRepsPoint
import io.github.lopezjavier.appfitness.domain.model.ProgressMetric
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildExerciseProgressTest {
    private val today = LocalDate(2026, 10, 19)

    private fun session(
        date: LocalDate,
        oneRm: Double? = null,
        volume: Double = 0.0,
        reps: Long = 0,
        durationS: Int? = null,
        distanceM: Double? = null,
        steps: Int? = null,
        floors: Int? = null,
    ) = ExerciseSessionRecords(
        sessionId = "s-$date",
        startedAt = date.atTime(LocalTime(10, 0)).toInstant(TimeZone.UTC),
        sessionVolume = volume,
        bestOneRm = oneRm,
        totalReps = reps,
        bestDurationS = durationS,
        bestDistanceM = distanceM,
        totalSteps = steps,
        totalFloors = floors,
    )

    private fun build(
        records: List<ExerciseSessionRecords>,
        range: ProgressRange = ProgressRange.OneYear,
        type: ExerciseMetricType = ExerciseMetricType.WeightReps,
        maxReps: List<MaxRepsPoint> = emptyList(),
    ) = BuildExerciseProgress(records, type, maxReps, range, today)

    @Test
    fun rangeFromSubtractsCalendarMonthsAndAllTimeHasNoBound() {
        assertEquals(LocalDate(2026, 9, 19), ProgressRange.OneMonth.from(today))
        assertEquals(LocalDate(2026, 7, 19), ProgressRange.ThreeMonths.from(today))
        assertEquals(LocalDate(2026, 4, 19), ProgressRange.SixMonths.from(today))
        assertEquals(LocalDate(2025, 10, 19), ProgressRange.OneYear.from(today))
        assertEquals(null, ProgressRange.AllTime.from(today))
    }

    @Test
    fun theCutOffDateIsIncludedAndTheDayBeforeIsNot() {
        val records = listOf(
            session(LocalDate(2026, 9, 18), oneRm = 90.0, volume = 900.0),
            session(LocalDate(2026, 9, 19), oneRm = 100.0, volume = 1000.0),
            session(LocalDate(2026, 10, 18), oneRm = 110.0, volume = 1100.0),
        )

        val progress = build(records, ProgressRange.OneMonth)

        assertEquals(listOf(100.0, 110.0), progress.primary.map { it.value })
        assertEquals(listOf(1000.0, 1100.0), progress.volume.map { it.value })
    }

    @Test
    fun allTimeIncludesSessionsOlderThanAYear() {
        val records = listOf(
            session(LocalDate(2024, 1, 5), oneRm = 80.0, volume = 800.0),
            session(LocalDate(2026, 10, 18), oneRm = 110.0, volume = 1100.0),
        )

        assertEquals(1, build(records, ProgressRange.OneYear).primary.size)
        assertEquals(2, build(records, ProgressRange.AllTime).primary.size)
    }

    @Test
    fun longerRangesIncludeOlderSessions() {
        val records = listOf(
            session(LocalDate(2026, 5, 1), oneRm = 90.0, volume = 1.0),
            session(LocalDate(2026, 10, 18), oneRm = 110.0, volume = 1.0),
        )

        assertEquals(1, build(records, ProgressRange.ThreeMonths).primary.size)
        assertEquals(2, build(records, ProgressRange.SixMonths).primary.size)
    }

    @Test
    fun aSingleSessionIsNotEnoughToShowEvolution() {
        val progress = build(listOf(session(LocalDate(2026, 10, 18), oneRm = 110.0, volume = 1.0)))

        assertFalse(progress.hasEnoughData)
        assertFalse(progress.isEmpty)
        assertEquals(110.0, progress.primary.single().value)
    }

    @Test
    fun noSessionsGivesAnEmptyProgress() {
        val progress = build(emptyList())

        assertTrue(progress.isEmpty)
        assertFalse(progress.hasEnoughData)
    }

    @Test
    fun benchExampleMarksTheSessionsThatRaiseTheOneRepMax() {
        val records = listOf(
            session(LocalDate(2026, 9, 1), oneRm = 100.0, volume = 3000.0),
            session(LocalDate(2026, 9, 10), oneRm = 105.0, volume = 2500.0),
            session(LocalDate(2026, 9, 20), oneRm = 110.0, volume = 4000.0),
        )

        val progress = build(records)

        // The first session counts as a record; the third raises 1RM and volume.
        assertEquals(listOf(true, true, true), progress.primary.map { it.isRecord })
        assertEquals(listOf(true, false, true), progress.volume.map { it.isRecord })
    }

    @Test
    fun aSessionRaisingOnlyVolumeIsMarkedOnVolumeOnly() {
        val records = listOf(
            session(LocalDate(2026, 9, 1), oneRm = 100.0, volume = 3000.0),
            session(LocalDate(2026, 9, 10), oneRm = 100.0, volume = 3500.0),
        )

        val progress = build(records)

        assertEquals(listOf(true, false), progress.primary.map { it.isRecord })
        assertEquals(listOf(true, true), progress.volume.map { it.isRecord })
    }

    @Test
    fun tiesAreNotRecords() {
        val records = listOf(
            session(LocalDate(2026, 9, 1), oneRm = 100.0, volume = 3000.0),
            session(LocalDate(2026, 9, 10), oneRm = 100.0, volume = 3000.0),
        )

        val progress = build(records)

        assertEquals(listOf(true, false), progress.primary.map { it.isRecord })
        assertEquals(listOf(true, false), progress.volume.map { it.isRecord })
    }

    @Test
    fun recordsAreJudgedOverTheWholeHistoryNotTheVisibleFrame() {
        val records = listOf(
            session(LocalDate(2025, 1, 1), oneRm = 120.0, volume = 5000.0),
            session(LocalDate(2026, 10, 1), oneRm = 100.0, volume = 3000.0),
            session(LocalDate(2026, 10, 10), oneRm = 130.0, volume = 3000.0),
        )

        val progress = build(records, ProgressRange.OneMonth)

        assertEquals(listOf(100.0, 130.0), progress.primary.map { it.value })
        assertEquals(listOf(false, true), progress.primary.map { it.isRecord })
        assertEquals(listOf(false, false), progress.volume.map { it.isRecord })
    }

    @Test
    fun sessionsWithoutValueForAFamilyAreOmittedFromThatChart() {
        val records = listOf(
            session(LocalDate(2026, 9, 1), oneRm = 100.0, volume = 0.0),
            session(LocalDate(2026, 9, 10), oneRm = null, volume = 2000.0),
            session(LocalDate(2026, 9, 20), oneRm = 110.0, volume = 2500.0),
        )

        val progress = build(records)

        assertEquals(listOf(100.0, 110.0), progress.primary.map { it.value })
        assertEquals(listOf(2000.0, 2500.0), progress.volume.map { it.value })
    }

    @Test
    fun exercisesLoggedWithoutLoadPlotMaxRepsWithoutMarkersOrVolume() {
        val records = listOf(session(LocalDate(2026, 10, 1), reps = 8), session(LocalDate(2026, 10, 10), reps = 10))
        val maxReps = listOf(
            MaxRepsPoint("a", LocalDate(2026, 10, 1), 8),
            MaxRepsPoint("b", LocalDate(2026, 10, 10), 10),
        )

        val progress = build(records, ProgressRange.OneMonth, maxReps = maxReps)

        assertEquals(ProgressMetric.MaxReps, progress.mode)
        assertEquals(listOf(8.0, 10.0), progress.primary.map { it.value })
        assertTrue(progress.primary.none { it.isRecord })
        assertTrue(progress.volume.isEmpty())
    }

    @Test
    fun maxRepsRespectTheRange() {
        val records = listOf(session(LocalDate(2026, 10, 10), reps = 10))
        val maxReps = listOf(
            MaxRepsPoint("a", LocalDate(2026, 8, 1), 6),
            MaxRepsPoint("b", LocalDate(2026, 10, 10), 10),
        )

        val progress = build(records, ProgressRange.OneMonth, maxReps = maxReps)

        assertEquals(listOf(10.0), progress.primary.map { it.value })
    }

    @Test
    fun mixedLoadedAndBodyweightSessionsStayInOneRepMaxMode() {
        val records = listOf(session(LocalDate(2026, 10, 1), reps = 8), session(LocalDate(2026, 10, 10), oneRm = 40.0))
        val maxReps = listOf(MaxRepsPoint("a", LocalDate(2026, 10, 1), 8))

        val progress = build(records, ProgressRange.OneMonth, maxReps = maxReps)

        assertEquals(ProgressMetric.OneRepMax, progress.mode)
        assertEquals(listOf(40.0), progress.primary.map { it.value })
    }

    @Test
    fun weightFamiliesFollowOneRepMaxAndVolume() {
        listOf(
            ExerciseMetricType.WeightReps,
            ExerciseMetricType.Weighted,
            ExerciseMetricType.Bodyweight,
            ExerciseMetricType.Assisted,
        ).forEach { assertEquals(ProgressMetric.OneRepMax, ChartMetricSelection.metric(it), "$it") }
    }

    @Test
    fun otherMetricTypesMapToTheirOwnMetric() {
        assertEquals(ProgressMetric.Reps, ChartMetricSelection.metric(ExerciseMetricType.RepsOnly))
        assertEquals(ProgressMetric.Duration, ChartMetricSelection.metric(ExerciseMetricType.Duration))
        assertEquals(ProgressMetric.Duration, ChartMetricSelection.metric(ExerciseMetricType.WeightDuration))
        assertEquals(ProgressMetric.Distance, ChartMetricSelection.metric(ExerciseMetricType.DistanceDuration))
        assertEquals(ProgressMetric.Steps, ChartMetricSelection.metric(ExerciseMetricType.StepsDuration))
        assertEquals(ProgressMetric.Floors, ChartMetricSelection.metric(ExerciseMetricType.FloorsDuration))
    }

    @Test
    fun plankPlotsBestDurationWithMarkersAndNoVolume() {
        val records = listOf(
            session(LocalDate(2026, 10, 1), durationS = 60),
            session(LocalDate(2026, 10, 5), durationS = 50),
            session(LocalDate(2026, 10, 10), durationS = 90),
        )

        val progress = build(records, type = ExerciseMetricType.Duration)

        assertEquals(ProgressMetric.Duration, progress.mode)
        assertEquals(listOf(60.0, 50.0, 90.0), progress.primary.map { it.value })
        assertEquals(listOf(true, false, true), progress.primary.map { it.isRecord })
        assertTrue(progress.volume.isEmpty())
    }

    @Test
    fun treadmillPlotsBestDistanceInMetres() {
        val records = listOf(
            session(LocalDate(2026, 10, 1), distanceM = 3000.0, durationS = 1200),
            session(LocalDate(2026, 10, 10), distanceM = 5000.0, durationS = 1800),
        )

        val progress = build(records, type = ExerciseMetricType.DistanceDuration)

        assertEquals(ProgressMetric.Distance, progress.mode)
        assertEquals(listOf(3000.0, 5000.0), progress.primary.map { it.value })
        assertEquals(listOf(true, true), progress.primary.map { it.isRecord })
        assertTrue(progress.volume.isEmpty())
    }

    @Test
    fun stepsAndFloorsPlotTheirOwnFamily() {
        val records = listOf(
            session(LocalDate(2026, 10, 1), steps = 1000, floors = 10),
            session(LocalDate(2026, 10, 10), steps = 900, floors = 12),
        )

        val steps = build(records, type = ExerciseMetricType.StepsDuration)
        val floors = build(records, type = ExerciseMetricType.FloorsDuration)

        assertEquals(listOf(1000.0, 900.0), steps.primary.map { it.value })
        assertEquals(listOf(true, false), steps.primary.map { it.isRecord })
        assertEquals(listOf(10.0, 12.0), floors.primary.map { it.value })
    }

    @Test
    fun repsOnlyPlotsTotalRepsAndNeverMarks() {
        val records = listOf(session(LocalDate(2026, 10, 1), reps = 20), session(LocalDate(2026, 10, 10), reps = 30))

        val progress = build(records, type = ExerciseMetricType.RepsOnly)

        assertEquals(ProgressMetric.Reps, progress.mode)
        assertEquals(listOf(20.0, 30.0), progress.primary.map { it.value })
        assertTrue(progress.primary.none { it.isRecord })
    }
}
