package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.ReportTeaser
import io.github.lopezjavier.appfitness.domain.model.reportTeaserOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReportTeaserTest {
    private fun parse(teaserJson: String?, kind: String = "week_summary", paused: Boolean = false) =
        reportItemOf(row(teaserJson, kind, paused))

    private fun row(teaserJson: String?, kind: String, paused: Boolean): JsonObject {
        val teaser = teaserJson?.let { ""","teaser":$it""" }.orEmpty()
        return Json.parseToJsonElement(
            """{"id":"r1","kind":"$kind","plan_id":"p1","week":3,"paused":$paused,""" +
                """"period_start":"2027-01-04","period_end":"2027-01-10",""" +
                """"created_at":"2027-01-11T08:00:00+00:00","phase_decision":null$teaser}""",
        ) as JsonObject
    }

    @Test
    fun week_readsUpsAndWeight() {
        val item = parse("""{"ups":3,"total":null,"weight_change_kg":-0.3}""")

        assertEquals(ReportTeaser(3, null, -0.3), item?.teaser)
    }

    @Test
    fun weekOne_hasWeightOnly() {
        val item = parse("""{"ups":null,"total":null,"weight_change_kg":-0.3}""")

        assertEquals(ReportTeaser(null, null, -0.3), item?.teaser)
    }

    @Test
    fun pausedWeek_withNullTeaser_hasNone() {
        val item = parse("null", paused = true)

        assertNotNull(item)
        assertNull(item.teaser)
    }

    @Test
    fun block_readsUpsOfTotal() {
        val item = parse("""{"ups":12,"total":15,"weight_change_kg":-1.8}""", kind = "block_review")

        assertEquals(ReportTeaser(12, 15, -1.8), item?.teaser)
    }

    @Test
    fun nonObjectTeaser_isNullAndTheRowIsKept() {
        listOf("null", "\"x\"", "[1,2]").forEach { json ->
            val item = parse(json)

            assertNotNull(item, json)
            assertNull(item.teaser, json)
        }
    }

    @Test
    fun missingTeaserKey_isNull() {
        val item = parse(null)

        assertNotNull(item)
        assertNull(item.teaser)
    }

    @Test
    fun weightGateNotPassed_leavesStrengthOnly() {
        val item = parse("""{"ups":2,"total":null,"weight_change_kg":null}""")

        assertEquals(ReportTeaser(2, null, null), item?.teaser)
    }

    @Test
    fun allNullObject_isNoTeaser() {
        val item = parse("""{"ups":null,"total":null,"weight_change_kg":null}""")

        assertNotNull(item)
        assertNull(item.teaser)
    }

    @Test
    fun zeroUpsWithTotal_isKept() {
        val item = parse("""{"ups":0,"total":15,"weight_change_kg":null}""", kind = "block_review")

        assertEquals(ReportTeaser(0, 15, null), item?.teaser)
    }

    @Test
    fun wrongTypedField_becomesNullAndTheRowIsKept() {
        val item = parse("""{"ups":"many","total":null,"weight_change_kg":-0.5}""")

        assertEquals(ReportTeaser(null, null, -0.5), item?.teaser)
    }

    @Test
    fun builder_dropsTotalWithoutUps() {
        assertEquals(ReportTeaser(null, null, -1.0), reportTeaserOf(null, 15, -1.0))
        assertNull(reportTeaserOf(null, 15, null))
    }

    @Test
    fun builder_doesNotClampUpsAboveTotal() {
        assertEquals(ReportTeaser(16, 15, null), reportTeaserOf(16, 15, null))
    }

    @Test
    fun builder_treatsNonFiniteWeightAsNull() {
        assertEquals(ReportTeaser(1, null, null), reportTeaserOf(1, null, Double.NaN))
        assertNull(reportTeaserOf(null, null, Double.POSITIVE_INFINITY))
    }
}
