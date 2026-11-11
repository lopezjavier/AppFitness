package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.SetType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlannedSetsTest {
    private fun sets(count: Int, reps: Int = 8, weight: Double? = null) = List(count) { PlannedSet(weight, reps) }

    @Test
    fun oneAndTenSetsAreValid() {
        assertNull(validatePlannedSets(sets(1)))
        assertNull(validatePlannedSets(sets(10)))
    }

    @Test
    fun zeroAndElevenSetsAreRejected() {
        assertEquals(PlannedSetsError.SetCount, validatePlannedSets(sets(0)))
        assertEquals(PlannedSetsError.SetCount, validatePlannedSets(sets(11)))
    }

    @Test
    fun repsOutsideOneToFiftyAreRejected() {
        assertEquals(PlannedSetsError.Reps, validatePlannedSets(sets(1, reps = 0)))
        assertNull(validatePlannedSets(sets(1, reps = 1)))
        assertNull(validatePlannedSets(sets(1, reps = 50)))
        assertEquals(PlannedSetsError.Reps, validatePlannedSets(sets(1, reps = 51)))
    }

    @Test
    fun negativeWeightIsRejectedButZeroAndNullAreValid() {
        assertEquals(PlannedSetsError.Weight, validatePlannedSets(sets(1, weight = -0.5)))
        assertNull(validatePlannedSets(sets(1, weight = 0.0)))
        assertNull(validatePlannedSets(sets(1, weight = null)))
    }

    @Test
    fun legacyColumnsMapToIdenticalPlannedSets() {
        val planned = plannedSetsFromLegacy(targetSets = 3, targetReps = 8, weightKg = 60.0, type = SetType.WarmUp)
        assertEquals(List(3) { PlannedSet(60.0, 8, SetType.WarmUp) }, planned)
    }

    @Test
    fun legacyMappingKeepsAMissingWeightAsNull() {
        assertEquals(listOf(PlannedSet(null, 10, SetType.Working)), plannedSetsFromLegacy(1, 10, null, SetType.Working))
    }
}
