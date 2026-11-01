package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanInputsTest {
    private fun input(key: PlanInputKey, status: PlanInputStatus, blocking: Boolean) = PlanInput(
        key,
        PlanInputValue.Absent,
        PlanInputSource.Profile,
        status,
        blocking,
        null,
        null,
    )

    private fun inputs(vararg list: PlanInput) = PlanInputs(PlanInputKind.Plan, null, list.toList())

    @Test
    fun hasBlocking_isTrueForABlockingMissingInput() {
        assertTrue(inputs(input(PlanInputKey.Sex, PlanInputStatus.Missing, true)).hasBlocking)
    }

    @Test
    fun hasBlocking_isFalseForNonBlockingMissingStaleAndUnsetOptional() {
        val result = inputs(
            input(PlanInputKey.TotalExpenditure, PlanInputStatus.Missing, false),
            input(PlanInputKey.Weight, PlanInputStatus.Stale, false),
            input(PlanInputKey.DaysPerWeek, PlanInputStatus.UnsetOptional, false),
        )

        assertFalse(result.hasBlocking)
    }

    @Test
    fun missingFields_listsOnlyBlockingMissingOnes() {
        val result = inputs(
            input(PlanInputKey.Intention, PlanInputStatus.Missing, true),
            input(PlanInputKey.Age, PlanInputStatus.Missing, false),
            input(PlanInputKey.Height, PlanInputStatus.Missing, true),
            input(PlanInputKey.Weight, PlanInputStatus.Stale, false),
        )

        assertEquals(setOf(MissingField.Intention, MissingField.Height), result.missingFields)
    }

    @Test
    fun toMissingField_mapsTheT174NamesAndNothingElse() {
        assertEquals(MissingField.Age, PlanInputKey.Age.toMissingField())
        assertEquals(MissingField.Expenditure, PlanInputKey.TotalExpenditure.toMissingField())
        assertEquals(MissingField.Place, PlanInputKey.Place.toMissingField())
        assertEquals(MissingField.Unknown, PlanInputKey.Unknown.toMissingField())
        assertNull(PlanInputKey.Diary.toMissingField())
    }
}
