package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SummarizeCircumferencesTest {
    private val summarize = SummarizeCircumferences()
    private val today = LocalDate(2026, 10, 20)

    private fun row(
        id: String,
        site: CircumferenceSite,
        cm: Double,
        daysAgo: Int = 0,
        hour: Int = 8,
        created: String = "2026-10-20T08:00:00Z",
    ) = BodyCircumference(
        id = id,
        site = site,
        valueCm = cm,
        measuredAt = today.minus(daysAgo, DateTimeUnit.DAY).atTime(hour, 0).toInstant(TimeZone.UTC),
        createdAt = Instant.parse(created),
    )

    private fun single(vararg rows: BodyCircumference) = summarize(rows.toList(), TimeZone.UTC).single()

    private fun compared(vararg rows: BodyCircumference) = assertIs<CircumferenceChange.Compared>(single(*rows).change)

    @Test
    fun noRows_giveNoSites() {
        assertEquals(emptyList(), summarize(emptyList(), TimeZone.UTC))
    }

    @Test
    fun oneRow_hasNoPairAndOnePointOnItsDate() {
        val summary = single(row("w", CircumferenceSite.Waist, 84.5, daysAgo = 2))

        assertEquals(CircumferenceChange.NoPair, summary.change)
        assertEquals(listOf(CircumferencePoint(LocalDate(2026, 10, 18), 84.5)), summary.history)
        assertEquals(LocalDate(2026, 10, 18), summary.latestDate)
        assertEquals(84.5, summary.latestCm)
    }

    @Test
    fun rows27DaysApart_haveNoPair() {
        val summary = single(
            row("old", CircumferenceSite.Waist, 86.5, daysAgo = 27),
            row("new", CircumferenceSite.Waist, 84.5),
        )

        assertEquals(CircumferenceChange.NoPair, summary.change)
        assertEquals(2, summary.history.size)
    }

    @Test
    fun rowsExactly28DaysApart_areCompared() {
        val change = compared(
            row("old", CircumferenceSite.Waist, 86.5, daysAgo = 28),
            row("new", CircumferenceSite.Waist, 84.5),
        )

        assertEquals(-2.0, change.deltaCm)
        assertEquals(86.5, change.refCm)
        assertEquals(LocalDate(2026, 9, 22), change.refDate)
        assertEquals(3.0, change.marginCm)
    }

    @Test
    fun theReference_isTheLatestPointAtLeast28DaysOlder() {
        val summary = single(
            row("a", CircumferenceSite.Waist, 90.0, daysAgo = 60),
            row("b", CircumferenceSite.Waist, 88.0, daysAgo = 35),
            row("c", CircumferenceSite.Waist, 86.0, daysAgo = 10),
            row("d", CircumferenceSite.Waist, 85.0),
        )
        val change = assertIs<CircumferenceChange.Compared>(summary.change)

        assertEquals(88.0, change.refCm)
        assertEquals(-3.0, change.deltaCm)
        assertEquals(listOf(90.0, 88.0, 86.0, 85.0), summary.history.map { it.cm })
    }

    @Test
    fun waist_isWithinTheMarginBelow3Cm() {
        assertEquals(true, waistChange(86.5, 84.5).withinMargin)
        assertEquals(false, waistChange(87.5, 84.5).withinMargin)
        assertEquals(false, waistChange(84.5, 87.6).withinMargin)
    }

    @Test
    fun hip_minus2IsNotWithinAndMinus1Point9Is() {
        assertEquals(false, siteChange(CircumferenceSite.Hip, 100.0, 98.0).withinMargin)
        assertEquals(true, siteChange(CircumferenceSite.Hip, 100.0, 98.1).withinMargin)
    }

    @Test
    fun hip_theBoundaryOnTheRoundedValueShowsNoLabel() {
        // 98.04 − 100.0 is −1.96 raw, shown as −2,0: at the margin, so no label (`07` §5 "hip −2,0 = no label").
        val change = siteChange(CircumferenceSite.Hip, 100.0, 98.04)

        assertEquals(-2.0, change.deltaCm)
        assertEquals(false, change.withinMargin)
    }

    @Test
    fun aHalfTenth_roundsAwayFromZeroAsTheShownValue() {
        // −0.25 is exact in binary; the row shows "−0,3", so the change is −0.3, not the half-even −0.2.
        assertEquals(-0.3, waistChange(84.75, 84.5).deltaCm)
        assertEquals(0.3, waistChange(84.5, 84.75).deltaCm)
    }

    @Test
    fun arm_1Point5IsNotWithinDespiteFloatingPointAnd1Point4Is() {
        // 31.4 − 29.9 = 1.5000000000000036 in floating point.
        val atMargin = siteChange(CircumferenceSite.Arm, 31.4, 29.9)

        assertEquals(-1.5, atMargin.deltaCm)
        assertEquals(false, atMargin.withinMargin)
        assertEquals(true, siteChange(CircumferenceSite.Arm, 31.4, 30.0).withinMargin)
    }

    @Test
    fun thigh_2Point7IsNotWithinAnd2Point6Is() {
        assertEquals(false, siteChange(CircumferenceSite.Thigh, 55.0, 57.7).withinMargin)
        assertEquals(true, siteChange(CircumferenceSite.Thigh, 55.0, 57.6).withinMargin)
    }

    @Test
    fun neckChestAndCalf_haveNoMarginAndNoWithinFlag() {
        listOf(CircumferenceSite.Neck, CircumferenceSite.Chest, CircumferenceSite.Calf).forEach { site ->
            val change = siteChange(site, 40.0, 40.2)

            assertNull(CircumferenceDisplayRule.marginCm(site))
            assertNull(change.marginCm)
            assertNull(change.withinMargin)
            assertEquals(0.2, change.deltaCm)
        }
    }

    @Test
    fun theRuleFigures_matchTheTable() {
        assertEquals(28, CircumferenceDisplayRule.WINDOW_DAYS)
        assertEquals(3.0, CircumferenceDisplayRule.marginCm(CircumferenceSite.Waist))
        assertEquals(2.0, CircumferenceDisplayRule.marginCm(CircumferenceSite.Hip))
        assertEquals(1.5, CircumferenceDisplayRule.marginCm(CircumferenceSite.Arm))
        assertEquals(2.7, CircumferenceDisplayRule.marginCm(CircumferenceSite.Thigh))
    }

    @Test
    fun twoRowsOnTheSameDay_giveOnePointWithTheLaterMeasuredAt() {
        val summary = single(
            row("late", CircumferenceSite.Waist, 84.0, hour = 20),
            row("early", CircumferenceSite.Waist, 85.0, hour = 7),
        )

        assertEquals(listOf(CircumferencePoint(today, 84.0)), summary.history)
        assertEquals(84.0, summary.latestCm)
    }

    @Test
    fun equalInstants_areBrokenByCreatedAtThenId() {
        val olderCreated = row("z", CircumferenceSite.Waist, 80.0, created = "2026-10-20T09:00:00Z")
        val newerCreated = row("a", CircumferenceSite.Waist, 90.0, created = "2026-10-20T10:00:00Z")
        val idA = row("a", CircumferenceSite.Hip, 99.0)
        val idB = row("b", CircumferenceSite.Hip, 98.0)

        listOf(listOf(olderCreated, newerCreated, idA, idB), listOf(newerCreated, olderCreated, idB, idA)).forEach {
            val (waist, hip) = summarize(it, TimeZone.UTC)
            assertEquals(90.0, waist.latestCm)
            assertEquals(98.0, hip.latestCm)
        }
    }

    @Test
    fun theLocalDay_isTheUsersTimeZone() {
        val madrid = TimeZone.of("Europe/Madrid")
        val rows = listOf(
            BodyCircumference("a", CircumferenceSite.Waist, 85.0, Instant.parse("2026-10-19T12:00:00Z"), EPOCH),
            BodyCircumference("b", CircumferenceSite.Waist, 84.0, Instant.parse("2026-10-19T23:30:00Z"), EPOCH),
        )

        val summary = summarize(rows, madrid).single()

        assertEquals(
            listOf(
                CircumferencePoint(LocalDate(2026, 10, 19), 85.0),
                CircumferencePoint(LocalDate(2026, 10, 20), 84.0),
            ),
            summary.history,
        )
        assertEquals(LocalDate(2026, 10, 20), summary.latestDate)
    }

    @Test
    fun sites_comeInSiteOrderWithOnlyTheSitesThatHaveRows() {
        val rows = listOf(
            row("c", CircumferenceSite.Calf, 38.0),
            row("w", CircumferenceSite.Waist, 84.5),
            row("n", CircumferenceSite.Neck, 39.0),
        )

        val sites = summarize(rows, TimeZone.UTC).map { it.site }

        assertEquals(listOf(CircumferenceSite.Neck, CircumferenceSite.Waist, CircumferenceSite.Calf), sites)
    }

    @Test
    fun theHistory_isOldestFirstWithOnePointPerDay() {
        val summary = single(
            row("c", CircumferenceSite.Waist, 84.0),
            row("a", CircumferenceSite.Waist, 86.0, daysAgo = 5),
            row("b", CircumferenceSite.Waist, 85.0, daysAgo = 5, hour = 9),
        )

        assertEquals(listOf(85.0, 84.0), summary.history.map { it.cm })
        assertTrue(summary.history.zipWithNext().all { (a, b) -> a.date < b.date })
    }

    private fun waistChange(refCm: Double, latestCm: Double) = siteChange(CircumferenceSite.Waist, refCm, latestCm)

    private fun siteChange(site: CircumferenceSite, refCm: Double, latestCm: Double) = compared(
        row("ref", site, refCm, daysAgo = 28),
        row("latest", site, latestCm),
    )

    private companion object {
        val EPOCH = Instant.parse("2026-01-01T00:00:00Z")
    }
}
