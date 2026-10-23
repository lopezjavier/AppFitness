package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DoubleDumbbellVolumeTest {
    private val start = Instant.parse("2026-10-19T10:00:00Z")

    private fun set(entry: SetEntry, type: SetType = SetType.Working) = WorkoutSet(1, type, entry, start)

    private fun exercise(id: String, position: Int, entry: SetEntry) =
        SessionExercise(id, position, null, listOf(set(entry)))

    private val twentyByTen = listOf(set(SetEntry.WeightReps(20.0, 10)))

    @Test
    fun doubleDumbbell_doublesWeightRepsVolume() {
        assertEquals(400.0, CalculateVolume(twentyByTen, doubleDumbbell = true))
    }

    @Test
    fun doubleDumbbell_offKeepsTheLoggedWeight() {
        assertEquals(200.0, CalculateVolume(twentyByTen, doubleDumbbell = false))
        assertEquals(200.0, CalculateVolume(twentyByTen))
    }

    @Test
    fun doubleDumbbell_doublesWeightedVolume() {
        assertEquals(160.0, CalculateVolume(listOf(set(SetEntry.Weighted(8.0, 10))), doubleDumbbell = true))
    }

    @Test
    fun doubleDumbbell_neverChangesNoLoadMetrics() {
        val sets = listOf(set(SetEntry.RepsOnly(15)), set(SetEntry.Duration(60)))

        assertEquals(0.0, CalculateVolume(sets, doubleDumbbell = true))
    }

    @Test
    fun doubleDumbbell_stillExcludesWarmUpUnlessIncluded() {
        val sets = listOf(set(SetEntry.WeightReps(10.0, 10), SetType.WarmUp), set(SetEntry.WeightReps(20.0, 10)))

        assertEquals(400.0, CalculateVolume(sets, doubleDumbbell = true))
        assertEquals(600.0, CalculateVolume(sets, includeWarmUp = true, doubleDumbbell = true))
    }

    @Test
    fun bodyweightVolume_usesTheUpdatedBodyWeight() {
        assertEquals(740.0, CalculateVolume(listOf(set(SetEntry.Bodyweight(0.0, 10))), bodyWeightKg = 74.0))
    }

    @Test
    fun sessionStats_doubleOnlyTheExercisesInTheSet() {
        val session = WorkoutSession(
            "s1",
            null,
            "Torso",
            SessionStatus.Completed,
            start,
            start,
            listOf(
                exercise("curl", 0, SetEntry.WeightReps(20.0, 10)),
                exercise("bench", 1, SetEntry.WeightReps(20.0, 10)),
            ),
        )

        assertEquals(600.0, ComputeSessionStats(session, doubleDumbbellExerciseIds = setOf("curl")).volumeKg)
        assertEquals(400.0, ComputeSessionStats(session).volumeKg)
    }

    @Test
    fun sessionSummary_doubleOnlyTheExercisesInTheSet() {
        val session = WorkoutSession(
            "s1",
            null,
            "Torso",
            SessionStatus.InProgress,
            start,
            null,
            listOf(
                exercise("curl", 0, SetEntry.WeightReps(20.0, 10)),
                exercise("bench", 1, SetEntry.WeightReps(20.0, 10)),
            ),
        )

        val summary = BuildSessionSummary(session, start, emptyMap(), doubleDumbbellExerciseIds = setOf("curl"))

        assertEquals(listOf(400.0, 200.0), summary.exercises.map { it.volume })
    }
}
