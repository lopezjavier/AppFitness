package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterpretMetricTest {
    private val interpret = InterpretMetric()

    private fun fat(value: Double, sex: Sex) = interpret.bodyFat(value, sex)?.status

    private fun whr(value: Double, sex: Sex) = interpret.waistToHip(value, sex)?.status

    @Test
    fun bmi_mapsEachBand() {
        assertEquals(MetricStatus.Underweight, interpret.bmi(18.4).status)
        assertEquals(MetricStatus.Normal, interpret.bmi(23.4).status)
        assertEquals(MetricStatus.Overweight, interpret.bmi(25.0).status)
        assertEquals(MetricStatus.Obese, interpret.bmi(30.0).status)
        assertEquals(MetricKind.Bmi, interpret.bmi(23.4).metric)
        assertEquals(23.4, interpret.bmi(23.4).value)
    }

    @Test
    fun bodyFatMen_everyEdge() {
        assertEquals(MetricStatus.BodyFatLow, fat(9.9, Sex.Male))
        assertEquals(MetricStatus.BodyFatNormal, fat(10.0, Sex.Male))
        assertEquals(MetricStatus.BodyFatNormal, fat(20.0, Sex.Male))
        assertEquals(MetricStatus.BodyFatHigh, fat(20.1, Sex.Male))
        assertEquals(MetricStatus.BodyFatHigh, fat(25.0, Sex.Male))
        assertEquals(MetricStatus.BodyFatVeryHigh, fat(25.1, Sex.Male))
    }

    @Test
    fun bodyFatWomen_everyEdge() {
        assertEquals(MetricStatus.BodyFatLow, fat(17.9, Sex.Female))
        assertEquals(MetricStatus.BodyFatNormal, fat(18.0, Sex.Female))
        assertEquals(MetricStatus.BodyFatNormal, fat(28.0, Sex.Female))
        assertEquals(MetricStatus.BodyFatHigh, fat(28.1, Sex.Female))
        assertEquals(MetricStatus.BodyFatHigh, fat(32.0, Sex.Female))
        assertEquals(MetricStatus.BodyFatVeryHigh, fat(32.1, Sex.Female))
    }

    @Test
    fun waistToHip_edgesPerSex() {
        assertEquals(MetricStatus.WhrLowRisk, whr(0.90, Sex.Male))
        assertEquals(MetricStatus.WhrHighRisk, whr(0.91, Sex.Male))
        assertEquals(MetricStatus.WhrLowRisk, whr(0.85, Sex.Female))
        assertEquals(MetricStatus.WhrHighRisk, whr(0.86, Sex.Female))
    }

    @Test
    fun nullSex_givesNoBodyFatOrWaistToHipStatus() {
        assertNull(interpret.bodyFat(20.0, null))
        assertNull(interpret.waistToHip(0.8, null))
    }
}
