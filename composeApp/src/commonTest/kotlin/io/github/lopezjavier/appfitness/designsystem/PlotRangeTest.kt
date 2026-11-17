package io.github.lopezjavier.appfitness.designsystem

import kotlin.math.abs
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** C-159 / R-112: the vertical domain of a line chart fits the data (12 % margin, minimum spread, round intervals). */
class PlotRangeTest {
    private fun range(
        values: List<Double?>,
        reference: Double? = null,
        dots: List<Double?>? = null,
        step: Double = DEFAULT_DOMAIN_STEP,
    ): ClosedFloatingPointRange<Double> = assertNotNull(plotRange(reference, values, dots, step))

    private fun assertClose(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) < EPSILON, "expected $expected but was $actual")

    @Test
    fun aNarrowWeightSeries_isFittedToRoundBoundsAroundIt() {
        val range = range(listOf(79.4, 80.1, 79.8))

        assertClose(78.0, range.start)
        assertClose(81.0, range.endInclusive)
    }

    @Test
    fun aFlatSeries_getsTheMinimumSpread_centredOnTheValue() {
        val range = range(listOf(80.0, 80.0, 80.0))

        assertTrue(80.0 in range)
        // Minimum spread is 40 % of the 5 kg step (2 kg), plus the margins: a few kilos, never a 20 kg band.
        val height = range.endInclusive - range.start
        assertTrue(height in 2.0..4.0, "height $height")
        assertTrue(abs((range.start + range.endInclusive) / 2 - 80.0) < 1.0)
    }

    @Test
    fun theDomain_isThreeEqualRoundIntervals() {
        val range = range(listOf(79.4, 80.1))
        val unit = (range.endInclusive - range.start) / 3

        assertClose(unit, round(unit * 10) / 10)
        assertClose(0.0, range.start / unit - round(range.start / unit))
    }

    @Test
    fun theDomain_containsEveryValueDotAndTheReference() {
        val series = listOf(
            listOf(61.2, 63.9, 62.4),
            listOf(100.0, 112.5, 108.0),
            listOf(7.0, 7.0),
            listOf(79.99, 80.01),
        )
        series.forEach { values ->
            val reference = values.first() + 3.7
            val range = range(values, reference = reference, dots = listOf(values.min() - 1.3))

            (values + reference + (values.min() - 1.3)).forEach {
                assertTrue(it in range, "$it not in $range")
            }
        }
    }

    @Test
    fun aDistantReference_widensTheDomainToIncludeIt() {
        val plain = range(listOf(79.4, 80.1))
        val withTarget = range(listOf(79.4, 80.1), reference = 70.0)

        assertTrue(70.0 in withTarget)
        assertTrue(withTarget.start < plain.start)
    }

    @Test
    fun aReferenceInsideTheData_doesNotChangeTheDomain() {
        assertEquals(range(listOf(79.4, 80.1)), range(listOf(79.4, 80.1), reference = 79.8))
    }

    @Test
    fun nullsAreIgnored_andNoDataMeansNoDomain() {
        assertEquals(range(listOf(79.4, 80.1)), range(listOf(null, 79.4, null, 80.1)))
        assertNull(plotRange(null, listOf(null, null), null))
        assertNull(plotRange(null, emptyList(), emptyList()))
    }

    @Test
    fun sleepHours_useTheSameMinimumSpreadOfFortyPercentOfTheirStep() {
        // A steady 7 h of sleep: the minimum spread is 0.4 h (step 1 h), so the domain stays under an hour tall.
        val range = range(listOf(7.0, 7.0, 7.0), step = 1.0)

        assertTrue(7.0 in range)
        val height = range.endInclusive - range.start
        assertTrue(height in 0.4..1.0, "height $height")
    }

    private companion object {
        const val EPSILON = 1e-6
    }
}
