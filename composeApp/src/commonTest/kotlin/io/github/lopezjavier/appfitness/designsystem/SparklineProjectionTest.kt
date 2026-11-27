package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SparklineProjectionTest {
    private val top = 0f
    private val bottom = 100f

    @Test
    fun minSpan_centresASmallMoveInsideTheSpan_withNoPointOnAnEdge() {
        val points = sparkPoints(listOf(84.0, 84.5), left = 0f, right = 100f, top = top, bottom = bottom, minSpan = 6.0)

        points.forEach { assertTrue(it.y > top && it.y < bottom, "y=${it.y} touches an edge") }
        // 0.5 cm of a 6 cm span is 1/12 of the height, centred on the midpoint.
        assertEquals(50f + 100f / 24, points[0].y, 0.01f)
        assertEquals(50f - 100f / 24, points[1].y, 0.01f)
    }

    @Test
    fun nullMinSpan_keepsTheSeriesOwnRange() {
        val points = sparkPoints(listOf(84.0, 84.5), left = 0f, right = 100f, top = top, bottom = bottom)

        assertEquals(bottom, points[0].y, 0.01f)
        assertEquals(top, points[1].y, 0.01f)
    }

    @Test
    fun minSpan_smallerThanTheRange_keepsTheSeriesOwnRange() {
        val points = sparkPoints(listOf(80.0, 90.0), left = 0f, right = 100f, top = top, bottom = bottom, minSpan = 4.0)

        assertEquals(bottom, points[0].y, 0.01f)
        assertEquals(top, points[1].y, 0.01f)
    }
}
