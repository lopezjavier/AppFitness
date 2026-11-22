package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class GroupReportsByBlockTest {
    private fun item(
        id: String,
        plan: String,
        kind: ReportKind = ReportKind.WeekSummary,
        teaser: ReportTeaser? = null,
    ) = ReportItem(
        id = id,
        kind = kind,
        planId = plan,
        week = if (kind == ReportKind.WeekSummary) 1 else null,
        paused = false,
        periodStart = LocalDate(2027, 1, 4),
        periodEnd = LocalDate(2027, 1, 10),
        createdAt = Instant.parse("2027-01-11T08:00:00Z"),
        phaseDecision = null,
        teaser = teaser,
    )

    private fun review(id: String, plan: String) = item(id, plan, ReportKind.BlockReview)

    @Test
    fun onePlanWithOnlyWeeks_isOneGroupNumberedOne() {
        val groups = groupReportsByBlock(listOf(item("w3", "p"), item("w2", "p")))

        assertEquals(1, groups.size)
        assertEquals(1, groups[0].number)
        assertEquals(listOf("w3", "w2"), groups[0].rows.map { it.id })
    }

    @Test
    fun twoPlans_newestFirstNumberedFromTheOldest() {
        val groups = groupReportsByBlock(listOf(item("a", "new"), item("b", "old")))

        assertEquals(listOf("new", "old"), groups.map { it.planId })
        assertEquals(listOf(2, 1), groups.map { it.number })
    }

    @Test
    fun reviewArrivingAfterItsWeeks_movesFirst() {
        val groups = groupReportsByBlock(listOf(item("w2", "p"), item("w1", "p"), review("r", "p")))

        assertEquals(listOf("r", "w2", "w1"), groups.single().rows.map { it.id })
    }

    @Test
    fun interleavedPlans_keepFirstAppearanceOrderAndPutTheOldReviewFirst() {
        val groups = groupReportsByBlock(
            listOf(item("n2", "new"), item("o2", "old"), item("n1", "new"), review("or", "old"), item("o1", "old")),
        )

        assertEquals(listOf("new", "old"), groups.map { it.planId })
        assertEquals(listOf("n2", "n1"), groups[0].rows.map { it.id })
        assertEquals(listOf("or", "o2", "o1"), groups[1].rows.map { it.id })
    }

    @Test
    fun emptyInput_isEmpty() {
        assertTrue(groupReportsByBlock(emptyList()).isEmpty())
    }

    @Test
    fun singleReviewWithoutWeeks_isItsOwnGroup() {
        val groups = groupReportsByBlock(listOf(review("r", "p")))

        assertEquals(listOf("r"), groups.single().rows.map { it.id })
    }

    @Test
    fun severalReviewsInOnePlan_keepServedOrder() {
        val groups = groupReportsByBlock(listOf(item("w", "p"), review("r2", "p"), review("r1", "p")))

        assertEquals(listOf("r2", "r1", "w"), groups.single().rows.map { it.id })
    }

    @Test
    fun teasers_areCarriedThrough() {
        val teaser = ReportTeaser(3, null, -0.3)

        val groups = groupReportsByBlock(listOf(item("w", "p", teaser = teaser)))

        assertEquals(teaser, groups.single().rows.single().teaser)
    }

    @Test
    fun sameInput_givesSameOutput() {
        val items = listOf(item("a", "x"), review("b", "y"), item("c", "x"))

        assertEquals(groupReportsByBlock(items), groupReportsByBlock(items))
    }
}
