package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class WaistHipRatioTest {
    private val ratio = WaistHipRatio()

    private fun row(id: String, site: CircumferenceSite, cm: Double, at: String = "2026-10-20T08:00:00Z") =
        BodyCircumference(id, site, cm, Instant.parse(at))

    @Test
    fun waistAndHip_giveTheRatioRoundedToTwoDecimals() {
        val rows = listOf(row("w", CircumferenceSite.Waist, 84.5), row("h", CircumferenceSite.Hip, 98.0))

        assertEquals(0.86, ratio(rows))
    }

    @Test
    fun missingWaistOrHipOrEverything_isNull() {
        assertNull(ratio(listOf(row("w", CircumferenceSite.Waist, 84.5))))
        assertNull(ratio(listOf(row("h", CircumferenceSite.Hip, 98.0))))
        assertNull(ratio(emptyList()))
        assertNull(ratio(listOf(row("n", CircumferenceSite.Neck, 40.0), row("c", CircumferenceSite.Chest, 100.0))))
    }

    @Test
    fun latestRowPerSiteWins() {
        val rows = listOf(
            row("w1", CircumferenceSite.Waist, 100.0, "2026-10-01T08:00:00Z"),
            row("w2", CircumferenceSite.Waist, 90.0, "2026-10-10T08:00:00Z"),
            row("h1", CircumferenceSite.Hip, 100.0, "2026-10-05T08:00:00Z"),
        )

        assertEquals(0.9, ratio(rows))
    }

    @Test
    fun sameTimestamp_breaksTheTieByIdRegardlessOfOrder() {
        val a = row("a", CircumferenceSite.Waist, 80.0)
        val b = row("b", CircumferenceSite.Waist, 90.0)
        val hip = row("h", CircumferenceSite.Hip, 100.0)

        assertEquals(0.9, ratio(listOf(a, b, hip)))
        assertEquals(0.9, ratio(listOf(b, a, hip)))
    }

    @Test
    fun rounding_goesToTheNearestHundredth() {
        val rows = listOf(row("w", CircumferenceSite.Waist, 70.0), row("h", CircumferenceSite.Hip, 90.0))

        assertEquals(0.78, ratio(rows))
    }
}
