package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhaseOptionsTest {
    @Test
    fun cutRateBounds_areValid() {
        assertTrue(CutRate.isValid(0.5))
        assertTrue(CutRate.isValid(1.0))
        assertTrue(CutRate.isValid(0.75))
    }

    @Test
    fun cutRateOutsideTheBounds_isInvalid() {
        assertFalse(CutRate.isValid(0.4))
        assertFalse(CutRate.isValid(1.2))
    }

    @Test
    fun defaultOptions_areTheSlowestCutWithoutDietBreak() {
        assertEquals(PhaseOptions(cutRatePct = 0.5, dietBreak = false), PhaseOptions())
    }

    @Test
    fun phaseCodes_mapAndUnknownIsNull() {
        assertEquals(null, phaseOrNull("minimal"))
        assertEquals(Phase.Maintenance, storedPhaseOrNull("minimal"))
        assertEquals(null, phaseOrNull("x"))
        assertEquals(null, phaseOrNull("regain"))
        assertEquals(Phase.Maintenance, storedPhaseOrNull("regain"))
        assertEquals(null, storedPhaseOrNull(null))
        assertEquals(PhaseReason.Intention, phaseReasonOf("returning_break"))
        assertEquals(PhaseReason.UserBusy, phaseReasonOf("user_busy"))
        assertEquals(PhaseReason.Intention, phaseReasonOf(null))
    }
}
