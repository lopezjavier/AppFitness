package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class PersonalRecordCatalogueTest {
    private val t0 = Instant.parse("2026-10-01T10:00:00Z")
    private val done = Instant.parse("2026-10-01T10:30:00Z")

    private fun set(index: Int, entry: SetEntry, type: SetType = SetType.Working, completed: Boolean = true) =
        WorkoutSet(index, type, entry, if (completed) done else null)

    private fun lift(index: Int, kg: Double, reps: Int, type: SetType = SetType.Working, completed: Boolean = true) =
        set(index, SetEntry.WeightReps(kg, reps), type, completed)

    private fun records(
        sets: List<WorkoutSet>,
        id: String = "s1",
        startedAt: Instant = t0,
        includeWarmUp: Boolean = false,
        bodyWeightKg: Double? = null,
        doubleDumbbell: Boolean = false,
    ) = PersonalRecordCatalogue.fromSets(id, startedAt, sets, includeWarmUp, bodyWeightKg, doubleDumbbell)

    private val squat = listOf(lift(1, 100.0, 5), lift(2, 85.0, 8))

    @Test
    fun weightFamiliesComeFromTheSquatFixture() {
        val r = records(squat)

        assertEquals(100.0, r.heaviestLift)
        assertEquals(680.0, r.bestSet)
        assertEquals(1180.0, r.sessionVolume)
        assertEquals(116.5, r.bestOneRm)
        assertEquals(13L, r.totalReps)
    }

    @Test
    fun oneRepMaxExamplesOfTheRulesBook() {
        assertEquals(116.5, records(listOf(lift(1, 100.0, 5))).bestOneRm)
        assertEquals(107.5, records(listOf(lift(1, 85.0, 8))).bestOneRm)
    }

    @Test
    fun oneRepMaxIgnoresZeroRepsMoreThanTwelveRepsAndZeroWeight() {
        val r = records(listOf(lift(1, 100.0, 0), lift(2, 50.0, 13), lift(3, 0.0, 10)))

        assertNull(r.bestOneRm)
    }

    @Test
    fun bestSetAndMaxVolumeDiffer() {
        val r = records(listOf(lift(1, 100.0, 10), lift(2, 60.0, 10), lift(3, 60.0, 10)))

        assertEquals(1000.0, r.bestSet)
        assertEquals(2200.0, r.sessionVolume)
    }

    @Test
    fun pendingSetsAreIgnored() {
        val r = records(listOf(lift(1, 100.0, 5), lift(2, 200.0, 5, completed = false)))

        assertEquals(100.0, r.heaviestLift)
        assertEquals(5L, r.totalReps)
    }

    @Test
    fun warmUpSetsCountOnlyWhenIncluded() {
        val sets = listOf(lift(1, 60.0, 5), lift(2, 140.0, 3, SetType.WarmUp))

        val excluded = records(sets)
        val included = records(sets, includeWarmUp = true)

        assertEquals(60.0, excluded.heaviestLift)
        assertEquals(5L, excluded.totalReps)
        assertEquals(140.0, included.heaviestLift)
        assertEquals(8L, included.totalReps)
    }

    @Test
    fun doubleDumbbellRaisesVolumeButNotTheOneRepMax() {
        val single = records(listOf(lift(1, 20.0, 10)))
        val double = records(listOf(lift(1, 20.0, 10)), doubleDumbbell = true)

        assertEquals(400.0, double.sessionVolume)
        assertEquals(400.0, double.bestSet)
        assertEquals(single.bestOneRm, double.bestOneRm)
        assertEquals(20.0, double.heaviestLift)
    }

    @Test
    fun bodyweightVolumeUsesTheLatestBodyWeight() {
        val r = records(listOf(set(1, SetEntry.Bodyweight(10.0, 5))), bodyWeightKg = 70.0)

        assertEquals(400.0, r.sessionVolume)
        assertEquals(400.0, r.bestSet)
    }

    @Test
    fun cardioFixtureFillsTimeDistanceStepsAndFloors() {
        val r = records(
            listOf(
                set(1, SetEntry.DistanceDuration(5000.0, 1500)),
                set(2, SetEntry.DistanceDuration(3000.0, 1800)),
                set(3, SetEntry.StepsDuration(900, 600)),
                set(4, SetEntry.FloorsDuration(12, 300)),
            ),
        )

        assertEquals(1800, r.bestDurationS)
        assertEquals(5000.0, r.bestDistanceM)
        assertEquals(900, r.totalSteps)
        assertEquals(12, r.totalFloors)
    }

    @Test
    fun cardioCurrentRecordsListTimeDistanceAndSteps() {
        val r = records(
            listOf(set(1, SetEntry.DistanceDuration(5000.0, 1500)), set(2, SetEntry.StepsDuration(900, 600))),
        )

        val types = PersonalRecordCatalogue.current(listOf(r)).map { it.type }

        assertTrue(
            types.containsAll(
                listOf(PersonalRecordType.BestDuration, PersonalRecordType.BestDistance, PersonalRecordType.MostSteps),
            ),
        )
    }

    @Test
    fun familiesFollowTheMetricType() {
        assertEquals(
            listOf(
                PersonalRecordType.HeaviestLift,
                PersonalRecordType.BestSet,
                PersonalRecordType.MaxVolume,
                PersonalRecordType.Best1Rm,
                PersonalRecordType.TotalReps,
            ),
            PersonalRecordCatalogue.families(ExerciseMetricType.WeightReps),
        )
        assertEquals(
            listOf(PersonalRecordType.BestDuration),
            PersonalRecordCatalogue.families(ExerciseMetricType.Duration),
        )
        assertEquals(
            listOf(PersonalRecordType.BestDistance, PersonalRecordType.BestDuration),
            PersonalRecordCatalogue.families(ExerciseMetricType.DistanceDuration),
        )
        assertEquals(
            listOf(PersonalRecordType.MostSteps, PersonalRecordType.BestDuration),
            PersonalRecordCatalogue.families(ExerciseMetricType.StepsDuration),
        )
        assertEquals(
            listOf(PersonalRecordType.MostFloors, PersonalRecordType.BestDuration),
            PersonalRecordCatalogue.families(ExerciseMetricType.FloorsDuration),
        )
    }

    @Test
    fun currentTakesTheBestPerFamilyAndTheEarliestOnTies() {
        val a = ExerciseSessionRecords("a", t0, heaviestLift = 100.0, sessionVolume = 500.0, totalReps = 10)
        val b = ExerciseSessionRecords(
            "b",
            Instant.parse("2026-10-08T10:00:00Z"),
            heaviestLift = 100.0,
            sessionVolume = 900.0,
            totalReps = 20,
        )

        val current = PersonalRecordCatalogue.current(listOf(b, a)).associateBy { it.type }

        assertEquals("a", current.getValue(PersonalRecordType.HeaviestLift).sessionId)
        assertEquals(LocalDate(2026, 10, 1), current.getValue(PersonalRecordType.HeaviestLift).date)
        assertEquals("b", current.getValue(PersonalRecordType.MaxVolume).sessionId)
        assertEquals(900.0, current.getValue(PersonalRecordType.MaxVolume).value)
    }

    @Test
    fun totalRepsSumsEverySessionAndHasNoSession() {
        val a = ExerciseSessionRecords("a", t0, totalReps = 10)
        val b = ExerciseSessionRecords("b", Instant.parse("2026-10-08T10:00:00Z"), totalReps = 25)

        val total = PersonalRecordCatalogue.current(listOf(a, b)).single { it.type == PersonalRecordType.TotalReps }

        assertEquals(35.0, total.value)
        assertNull(total.sessionId)
        assertNull(total.date)
    }

    @Test
    fun emptyHistoryHasNoRecords() {
        assertTrue(PersonalRecordCatalogue.current(emptyList()).isEmpty())
    }

    @Test
    fun theFirstSessionBeatsEveryFamilyItHasDataFor() {
        val first = records(squat)

        val beaten = PersonalRecordCatalogue.beatenBy(first, emptyList())

        assertEquals(
            setOf(
                PersonalRecordType.HeaviestLift,
                PersonalRecordType.BestSet,
                PersonalRecordType.MaxVolume,
                PersonalRecordType.Best1Rm,
            ),
            beaten,
        )
    }

    @Test
    fun anEqualValueIsNotABeatAndTotalRepsNeverIs() {
        val earlier = records(squat, id = "old")
        val same = records(squat, id = "new", startedAt = Instant.parse("2026-10-08T10:00:00Z"))

        assertTrue(PersonalRecordCatalogue.beatenBy(same, listOf(earlier)).isEmpty())
    }

    @Test
    fun onlyTheFamiliesStrictlyAboveEarlierSessionsAreBeaten() {
        val earlier = records(squat, id = "old")
        val heavier = records(
            listOf(lift(1, 120.0, 1)),
            id = "new",
            startedAt = Instant.parse("2026-10-08T10:00:00Z"),
        )

        val beaten = PersonalRecordCatalogue.beatenBy(heavier, listOf(earlier))

        assertEquals(setOf(PersonalRecordType.HeaviestLift, PersonalRecordType.Best1Rm), beaten)
    }

    @Test
    fun aWarmUpOnlySessionBeatsNothingWhileExcluded() {
        val earlier = records(squat, id = "old")
        val warm = records(
            listOf(lift(1, 200.0, 5, SetType.WarmUp)),
            id = "new",
            startedAt = Instant.parse("2026-10-08T10:00:00Z"),
        )

        assertTrue(PersonalRecordCatalogue.beatenBy(warm, listOf(earlier)).isEmpty())
    }

    @Test
    fun sessionsAfterTheCurrentOneAreNotEarlier() {
        val current = records(squat, id = "mid", startedAt = Instant.parse("2026-10-05T10:00:00Z"))
        val later = records(
            listOf(lift(1, 300.0, 5)),
            id = "late",
            startedAt = Instant.parse("2026-10-09T10:00:00Z"),
        )

        val beaten = PersonalRecordCatalogue.beatenBy(current, listOf(current, later))

        assertTrue(PersonalRecordType.HeaviestLift in beaten)
    }

    @Test
    fun sameDaySessionsAreOrderedByStartTime() {
        val morning = records(listOf(lift(1, 100.0, 5)), id = "am", startedAt = Instant.parse("2026-10-05T08:00:00Z"))
        val evening = records(listOf(lift(1, 90.0, 5)), id = "pm", startedAt = Instant.parse("2026-10-05T19:00:00Z"))

        assertTrue(PersonalRecordCatalogue.beatenBy(evening, listOf(morning)).isEmpty())
        assertTrue(PersonalRecordType.HeaviestLift in PersonalRecordCatalogue.beatenBy(morning, listOf(evening)))
    }
}
