package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GhostDataTest {
    private val last = listOf(PreviousSet(1, 80.0, 10), PreviousSet(2, 85.0, 8), PreviousSet(3, 82.5, 6))

    @Test
    fun setTwo_showsWeightTimesReps() = assertEquals("85 kg × 8", GhostData.previousText(GhostData.forSet(last, 2)))

    @Test
    fun moreSetsThanLastTime_hasNoPrevious() {
        assertNull(GhostData.forSet(last, 4))
        assertNull(GhostData.previousText(GhostData.forSet(last, 4)))
    }

    @Test
    fun neverDone_hasNoPrevious() = assertNull(GhostData.forSet(emptyList(), 1))

    @Test
    fun decimalWeight_usesACommaAndNoTrailingZero() =
        assertEquals("82,5 kg × 6", GhostData.previousText(GhostData.forSet(last, 3)))

    @Test
    fun thousands_areNotGrouped() = assertEquals("1000 kg × 5", GhostData.previousText(PreviousSet(1, 1000.0, 5)))

    @Test
    fun placeholders_arePreviousWeightAndReps() {
        assertEquals("82,5" to "6", GhostData.placeholders(GhostData.forSet(last, 3)))
        assertNull(GhostData.placeholders(null))
    }

    @Test
    fun previousRpe_isASecondLine() =
        assertEquals("85 kg × 8\nRPE 8", GhostData.previousText(PreviousSet(2, 85.0, 8, rpe = 8.0)))

    @Test
    fun previousHalfPointRpe_usesADecimalComma() =
        assertEquals("85 kg × 8\nRPE 7,5", GhostData.previousText(PreviousSet(2, 85.0, 8, rpe = 7.5)))

    @Test
    fun noPreviousRpe_leavesTheTextUnchanged() =
        assertEquals("85 kg × 8", GhostData.previousText(PreviousSet(2, 85.0, 8)))

    @Test
    fun rpeText_isNullWithoutRpe() {
        assertEquals("RPE 8", GhostData.rpeText(PreviousSet(2, 85.0, 8, rpe = 8.0)))
        assertNull(GhostData.rpeText(PreviousSet(2, 85.0, 8)))
        assertNull(GhostData.rpeText(null))
    }

    @Test
    fun placeholders_ignoreRpe() = assertEquals("85" to "8", GhostData.placeholders(PreviousSet(2, 85.0, 8, rpe = 8.0)))

    @Test
    fun routineFilter_routineModeWithARoutine_isTheRoutineId() =
        assertEquals("r1", GhostData.routineFilter(PreviousValuesMode.Routine, "r1"))

    @Test
    fun routineFilter_routineModeInAFreeSession_isNull() =
        assertNull(GhostData.routineFilter(PreviousValuesMode.Routine, null))

    @Test
    fun routineFilter_overallMode_isNull() = assertNull(GhostData.routineFilter(PreviousValuesMode.Overall, "r1"))
}
