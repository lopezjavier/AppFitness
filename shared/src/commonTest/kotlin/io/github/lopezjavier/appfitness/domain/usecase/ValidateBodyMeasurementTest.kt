package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class ValidateBodyMeasurementTest {
    private val validate = ValidateBodyMeasurement()

    private fun weightProblem(kg: Double) = BodyMeasurementCheck.Invalid(BodyMeasurementField.Weight, problemOf(kg))

    private fun problemOf(kg: Double): BodyMeasurementProblem =
        (validate(kg, null) as BodyMeasurementCheck.Invalid).problem

    @Test
    fun weightBounds_areInclusive() {
        assertEquals(BodyMeasurementCheck.Valid, validate(20.0, null))
        assertEquals(BodyMeasurementCheck.Valid, validate(400.0, null))
        assertEquals(BodyMeasurementCheck.Valid, validate(74.3, null))
    }

    @Test
    fun weightBelowMinimum_isTooLow() {
        assertEquals(
            BodyMeasurementCheck.Invalid(BodyMeasurementField.Weight, BodyMeasurementProblem.TooLow),
            validate(19.9, null),
        )
    }

    @Test
    fun weightAboveMaximum_isTooHigh() {
        assertEquals(
            BodyMeasurementCheck.Invalid(BodyMeasurementField.Weight, BodyMeasurementProblem.TooHigh),
            validate(400.1, null),
        )
        assertEquals(BodyMeasurementProblem.TooHigh, problemOf(401.0))
    }

    @Test
    fun bodyFatBounds_areInclusiveAndNullIsValid() {
        assertEquals(BodyMeasurementCheck.Valid, validate(70.0, 2.0))
        assertEquals(BodyMeasurementCheck.Valid, validate(70.0, 75.0))
        assertEquals(BodyMeasurementCheck.Valid, validate(70.0, null))
    }

    @Test
    fun bodyFatOutOfRange_namesTheField() {
        assertEquals(
            BodyMeasurementCheck.Invalid(BodyMeasurementField.BodyFat, BodyMeasurementProblem.TooLow),
            validate(70.0, 1.9),
        )
        assertEquals(
            BodyMeasurementCheck.Invalid(BodyMeasurementField.BodyFat, BodyMeasurementProblem.TooHigh),
            validate(70.0, 80.0),
        )
    }

    @Test
    fun twoDecimals_areRejected() {
        assertEquals(
            BodyMeasurementCheck.Invalid(BodyMeasurementField.BodyFat, BodyMeasurementProblem.TooManyDecimals),
            validate(70.0, 73.95),
        )
        assertEquals(BodyMeasurementProblem.TooManyDecimals, problemOf(70.25))
    }

    @Test
    fun notANumber_isRejected() {
        assertEquals(BodyMeasurementProblem.NotANumber, problemOf(Double.NaN))
        assertEquals(
            BodyMeasurementCheck.Invalid(BodyMeasurementField.BodyFat, BodyMeasurementProblem.NotANumber),
            validate(70.0, Double.NaN),
        )
    }

    @Test
    fun weightIsReportedBeforeBodyFat() {
        assertEquals(weightProblem(10.0), validate(10.0, 80.0))
    }
}
