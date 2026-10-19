package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class EstimateOneRepMaxTest {
    @Test
    fun hundredByFive_is116point5() = assertEquals(116.5, EstimateOneRepMax(100.0, 5))

    @Test
    fun eightyFiveByEight_is107point5() = assertEquals(107.5, EstimateOneRepMax(85.0, 8))

    @Test
    fun hundredByFour_is113point5() = assertEquals(113.5, EstimateOneRepMax(100.0, 4))

    @Test
    fun singleRep_isTheWeight() = assertEquals(102.5, EstimateOneRepMax(102.5, 1))

    @Test
    fun twelveReps_isEligible() = assertEquals(140.0, EstimateOneRepMax(100.0, 12))

    @Test
    fun zeroRepsThirteenRepsOrZeroWeight_areNotEligible() {
        assertNull(EstimateOneRepMax(100.0, 0))
        assertNull(EstimateOneRepMax(100.0, 13))
        assertNull(EstimateOneRepMax(0.0, 5))
    }

    @Test
    fun roundsToTheNearestHalfKilo() {
        // 50 * (1 + 2/30) = 53.33 -> 53.5; 60 * (1 + 2/30) = 64.0; 52.5 * (1 + 2/30) = 56.0
        assertEquals(53.5, EstimateOneRepMax(50.0, 2))
        assertEquals(64.0, EstimateOneRepMax(60.0, 2))
        assertEquals(56.0, EstimateOneRepMax(52.5, 2))
        // 20 * (1 + 3/30) = 22.0; 22.5 * (1 + 3/30) = 24.75 -> 25.0 (tie goes up)
        assertEquals(25.0, EstimateOneRepMax(22.5, 3))
    }
}

private val DONE = Instant.fromEpochSeconds(1_700_000_000)

private fun set(weight: Double, reps: Int, type: SetType = SetType.Working, done: Boolean = true) =
    WorkoutSet(1, type, SetEntry.WeightReps(weight, reps), if (done) DONE else null)

class CalculateVolumeTest {
    @Test
    fun sessionExample_is2120() {
        val sets = listOf(set(60.0, 12), set(87.5, 8), set(87.5, 8))

        assertEquals(2120.0, CalculateVolume(sets))
    }

    @Test
    fun pendingSets_areIgnored() {
        assertEquals(480.0, CalculateVolume(listOf(set(60.0, 8), set(100.0, 10, done = false))))
    }

    @Test
    fun bodyweightSets_contributeZero() {
        assertEquals(0.0, CalculateVolume(listOf(set(0.0, 15))))
    }

    @Test
    fun warmUpSets_areExcluded() {
        assertEquals(800.0, CalculateVolume(listOf(set(40.0, 10, SetType.WarmUp), set(100.0, 8))))
    }

    @Test
    fun noSets_isZero() = assertEquals(0.0, CalculateVolume(emptyList()))
}

class DetectPersonalRecordsTest {
    private fun point(session: String, oneRm: Double?) =
        ExerciseHistoryPoint(session, LocalDate(2026, 10, 1), oneRm, 0.0)

    @Test
    fun greaterThanPreviousBest_isARecord() {
        val result = DetectPersonalRecords(mapOf("e1" to 113.5), mapOf("e1" to listOf(point("s1", 107.5))))

        assertEquals(setOf("e1"), result)
    }

    @Test
    fun equalOrLower_isNotARecord() {
        val history = mapOf("e1" to listOf(point("s1", 110.0)), "e2" to listOf(point("s2", 110.0)))

        assertEquals(emptySet(), DetectPersonalRecords(mapOf("e1" to 110.0, "e2" to 100.0), history))
    }

    @Test
    fun firstEverSessionOfTheExercise_isARecord() {
        assertEquals(setOf("e1"), DetectPersonalRecords(mapOf("e1" to 90.0), emptyMap()))
    }

    @Test
    fun previousSessionsWithoutEligibleSets_dontBlockARecord() {
        assertEquals(setOf("e1"), DetectPersonalRecords(mapOf("e1" to 90.0), mapOf("e1" to listOf(point("s1", null)))))
    }

    @Test
    fun noEligibleSetNow_isNotARecordEvenForFirstSession() {
        assertEquals(emptySet(), DetectPersonalRecords(mapOf("e1" to null), emptyMap()))
    }

    @Test
    fun currentSessionInHistory_isIgnored() {
        val history = mapOf("e1" to listOf(point("s1", 100.0), point("current", 120.0)))

        assertEquals(setOf("e1"), DetectPersonalRecords(mapOf("e1" to 120.0), history, currentSessionId = "current"))
    }

    @Test
    fun severalExercises_areJudgedIndependently() {
        val history = mapOf("e1" to listOf(point("s1", 100.0)), "e2" to listOf(point("s1", 100.0)))

        assertEquals(
            setOf("e1", "e3"),
            DetectPersonalRecords(mapOf("e1" to 105.0, "e2" to 95.0, "e3" to 50.0), history),
        )
    }
}
