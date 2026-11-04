package io.github.lopezjavier.appfitness.domain.coach.builder

import kotlin.test.Test
import kotlin.test.assertEquals

class CardioTargetsTest {
    @Test
    fun noPlan_usesTheDefault150() {
        assertEquals(150, CardioTargets.forWeeklyMinutes(null))
    }

    @Test
    fun zeroOrNegative_usesTheDefault() {
        assertEquals(150, CardioTargets.forWeeklyMinutes(0))
        assertEquals(150, CardioTargets.forWeeklyMinutes(-5))
    }

    @Test
    fun positiveTarget_isKept() {
        assertEquals(300, CardioTargets.forWeeklyMinutes(300))
        assertEquals(45, CardioTargets.forWeeklyMinutes(45))
    }

    @Test
    fun default150Over3Days_is50EachAndNothingOnYourOwn() {
        val plan = CardioPlan.of(CardioTargets.forWeeklyMinutes(null), 3)
        assertEquals(listOf(50, 50, 50), plan.perRoutineMin)
        assertEquals(0, plan.onYourOwnMin)
    }

    @Test
    fun target300Over3Days_is60EachAnd120OnYourOwn() {
        val plan = CardioPlan.of(CardioTargets.forWeeklyMinutes(300), 3)
        assertEquals(listOf(60, 60, 60), plan.perRoutineMin)
        assertEquals(120, plan.onYourOwnMin)
    }

    @Test
    fun default150Over1Day_isCappedAt60AndTheRestIsOnYourOwn() {
        val plan = CardioPlan.of(CardioTargets.forWeeklyMinutes(null), 1)
        assertEquals(listOf(60), plan.perRoutineMin)
        assertEquals(90, plan.onYourOwnMin)
    }
}
