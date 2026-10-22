package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class ValidateCircumferenceTest {
    private val validate = ValidateCircumference()

    private fun problem(value: Double) = (validate(value) as? CircumferenceCheck.Invalid)?.problem

    @Test
    fun boundaries_areValid() {
        assertEquals(CircumferenceCheck.Valid, validate(20.0))
        assertEquals(CircumferenceCheck.Valid, validate(250.0))
        assertEquals(CircumferenceCheck.Valid, validate(84.5))
    }

    @Test
    fun belowTheRange_isTooLow() {
        assertEquals(CircumferenceProblem.TooLow, problem(19.9))
        assertEquals(CircumferenceProblem.TooLow, problem(19.99))
    }

    @Test
    fun aboveTheRange_isTooHigh() {
        assertEquals(CircumferenceProblem.TooHigh, problem(251.0))
        assertEquals(CircumferenceProblem.TooHigh, problem(250.1))
    }

    @Test
    fun twoDecimals_areRejected() {
        assertEquals(CircumferenceProblem.TooManyDecimals, problem(84.55))
    }

    @Test
    fun nanAndInfinity_areNotNumbers() {
        assertEquals(CircumferenceProblem.NotANumber, problem(Double.NaN))
        assertEquals(CircumferenceProblem.NotANumber, problem(Double.POSITIVE_INFINITY))
    }
}
