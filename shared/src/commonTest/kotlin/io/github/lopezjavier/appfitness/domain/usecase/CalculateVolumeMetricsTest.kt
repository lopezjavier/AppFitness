package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class CalculateVolumeMetricsTest {
    private fun set(entry: SetEntry, type: SetType = SetType.Working, done: Boolean = true) = WorkoutSet(
        setIndex = 1,
        setType = type,
        entry = entry,
        completedAt = if (done) Instant.fromEpochSeconds(1_800_000_000L) else null,
    )

    @Test
    fun bodyweight_addsTheLoadToTheBodyWeight() {
        val sets = listOf(set(SetEntry.Bodyweight(10.0, 8)))

        assertEquals(672.0, CalculateVolume(sets, bodyWeightKg = 74.0))
    }

    @Test
    fun bodyweight_withoutKnownBodyWeight_countsTheAddedLoadOnly() {
        val sets = listOf(set(SetEntry.Bodyweight(10.0, 8)))

        assertEquals(80.0, CalculateVolume(sets))
        assertEquals(80.0, CalculateVolume(sets, bodyWeightKg = null))
    }

    @Test
    fun bodyweight_withNoAddedLoad_usesTheBodyWeight() {
        assertEquals(740.0, CalculateVolume(listOf(set(SetEntry.Bodyweight(0.0, 10))), bodyWeightKg = 74.0))
    }

    @Test
    fun assisted_subtractsTheAssistFromTheBodyWeight() {
        val sets = listOf(set(SetEntry.Assisted(20.0, 10)))

        assertEquals(540.0, CalculateVolume(sets, bodyWeightKg = 74.0))
    }

    @Test
    fun assisted_neverGoesBelowZero() {
        val sets = listOf(set(SetEntry.Assisted(90.0, 10)))

        assertEquals(0.0, CalculateVolume(sets, bodyWeightKg = 74.0))
    }

    @Test
    fun assisted_withoutKnownBodyWeight_isZero() {
        assertEquals(0.0, CalculateVolume(listOf(set(SetEntry.Assisted(20.0, 10)))))
    }

    @Test
    fun weighted_countsTheAddedLoadOnlyEvenWithBodyWeight() {
        val sets = listOf(set(SetEntry.Weighted(15.0, 6)))

        assertEquals(90.0, CalculateVolume(sets, bodyWeightKg = 74.0))
    }

    @Test
    fun setsWithoutRepsLoad_carryNoVolume() {
        val sets = listOf(
            set(SetEntry.RepsOnly(20)),
            set(SetEntry.Duration(90)),
            set(SetEntry.WeightDuration(32.0, 45)),
            set(SetEntry.DistanceDuration(5000.0, 1800)),
            set(SetEntry.StepsDuration(4000, 1800)),
            set(SetEntry.FloorsDuration(30, 600)),
        )

        assertEquals(0.0, CalculateVolume(sets, bodyWeightKg = 74.0))
    }

    @Test
    fun warmUpBodyweightSets_areExcludedUnlessIncluded() {
        val sets = listOf(
            set(SetEntry.Bodyweight(0.0, 5), SetType.WarmUp),
            set(SetEntry.Bodyweight(10.0, 8)),
        )

        assertEquals(672.0, CalculateVolume(sets, bodyWeightKg = 74.0))
        assertEquals(672.0 + 370.0, CalculateVolume(sets, includeWarmUp = true, bodyWeightKg = 74.0))
    }

    @Test
    fun pendingBodyweightSets_areIgnored() {
        assertEquals(0.0, CalculateVolume(listOf(set(SetEntry.Bodyweight(10.0, 8), done = false)), bodyWeightKg = 74.0))
    }

    @Test
    fun mixedList_sumsOnlyTheMetricsThatCarryVolume() {
        val sets = listOf(
            set(SetEntry.WeightReps(100.0, 5)),
            set(SetEntry.Bodyweight(10.0, 8)),
            set(SetEntry.Assisted(20.0, 10)),
            set(SetEntry.Weighted(15.0, 6)),
            set(SetEntry.Duration(60)),
        )

        assertEquals(500.0 + 672.0 + 540.0 + 90.0, CalculateVolume(sets, bodyWeightKg = 74.0))
    }
}
