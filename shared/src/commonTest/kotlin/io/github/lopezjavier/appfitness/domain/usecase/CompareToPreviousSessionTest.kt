package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ComparableSession
import io.github.lopezjavier.appfitness.domain.model.ComparisonSession
import io.github.lopezjavier.appfitness.domain.model.ComparisonSet
import io.github.lopezjavier.appfitness.domain.model.ProgressState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompareToPreviousSessionTest {
    private val day0 = LocalDate(2026, 3, 2)

    /** A set: kg, reps and an optional RPE. */
    private fun set(kg: Double, reps: Int, rpe: Double? = null) = Triple(kg, reps, rpe)

    private fun session(
        day: Int,
        vararg sets: Triple<Double, Int, Double?>,
        rmin: Int? = null,
        rmax: Int? = null,
        deload: Boolean = false,
    ): ComparableSession = assertNotNull(
        ComparableSession.of(
            ComparisonSession(
                sessionId = "s$day",
                date = day0.plus(day, DateTimeUnit.DAY),
                deload = deload,
                sets = sets.map { (kg, reps, rpe) -> ComparisonSet(kg, reps, rpe, rmin, rmax) },
            ),
        ),
    )

    private fun sets(n: Int, kg: Double, reps: Int, rpe: Double? = null) = Array(n) { set(kg, reps, rpe) }

    private fun compare(
        now: ComparableSession,
        before: ComparableSession?,
        inCut: Boolean = false,
        openDiscomfort: Boolean = false,
    ) = CompareToPreviousSession("ex", now, before, inCut, openDiscomfort)

    private fun stateOf(now: ComparableSession, before: ComparableSession?, openDiscomfort: Boolean = false) =
        compare(now, before, openDiscomfort = openDiscomfort).state

    @Test
    fun moreLoadWithEveryRangedSetAtOrAboveRepMin_isUp() {
        val before = session(0, *sets(3, 80.0, 8), rmin = 6, rmax = 10)
        val now = session(12, *sets(3, 82.5, 6), rmin = 6, rmax = 10)

        assertEquals(ProgressState.Up, stateOf(now, before))
    }

    @Test
    fun moreLoadWithoutARange_isUpWhenTheLowestRepsHold() {
        val before = session(0, set(80.0, 8), set(80.0, 7), set(80.0, 6))
        val now = session(12, *sets(3, 82.5, 6))

        assertEquals(ProgressState.Up, stateOf(now, before))
    }

    @Test
    fun sameLoadWithMoreRepsOverTheSharedSets_isUp() {
        val before = session(0, *sets(4, 80.0, 8), rmin = 6, rmax = 10)
        val now = session(12, set(80.0, 9), set(80.0, 9), set(80.0, 7), rmin = 6, rmax = 10)

        assertEquals(ProgressState.Up, stateOf(now, before))
    }

    @Test
    fun sameLoadAndReps_isSameAndNotHoldOutsideACut() {
        val before = session(0, *sets(3, 80.0, 8))
        val now = session(12, *sets(3, 80.0, 8))

        val result = compare(now, before)

        assertEquals(ProgressState.Same, result.state)
        assertFalse(result.hold)
    }

    @Test
    fun sameInACut_isHold() {
        val before = session(0, *sets(3, 80.0, 8))
        val now = session(12, *sets(3, 80.0, 8))

        assertTrue(compare(now, before, inCut = true).hold)
    }

    @Test
    fun upInACut_isNotHold() {
        val before = session(0, *sets(3, 80.0, 8))
        val now = session(12, *sets(3, 82.5, 8))

        assertFalse(compare(now, before, inCut = true).hold)
    }

    @Test
    fun lessLoad_isDown() {
        val before = session(0, *sets(3, 80.0, 8), rmin = 6, rmax = 10)
        val now = session(12, *sets(3, 77.5, 10), rmin = 6, rmax = 10)

        assertEquals(ProgressState.Down, stateOf(now, before))
    }

    @Test
    fun sameLoadWithFewerReps_isDown() {
        val before = session(0, *sets(3, 80.0, 8))
        val now = session(12, set(80.0, 8), set(80.0, 8), set(80.0, 7))

        assertEquals(ProgressState.Down, stateOf(now, before))
    }

    @Test
    fun loadIncreaseAtRpePlusTwo_isSame() {
        val before = session(0, *sets(2, 80.0, 8, 7.0))
        val now = session(12, *sets(2, 82.5, 8, 9.0))

        assertEquals(ProgressState.Same, stateOf(now, before))
    }

    @Test
    fun loadIncreaseAtRpePlusOne_isStillUp() {
        val before = session(0, *sets(2, 80.0, 8, 7.0))
        val now = session(12, *sets(2, 82.5, 8, 8.0))

        assertEquals(ProgressState.Up, stateOf(now, before))
    }

    @Test
    fun rpeMissingOnOneSide_isJudgedOnLoadAndReps() {
        val before = session(0, *sets(2, 80.0, 8))
        val now = session(12, *sets(2, 82.5, 8, 10.0))

        assertEquals(ProgressState.Up, stateOf(now, before))
    }

    @Test
    fun moreRepsAtRpePlusTwo_isSame() {
        val before = session(0, *sets(3, 80.0, 8, 7.0))
        val now = session(12, *sets(3, 80.0, 9, 9.0))

        assertEquals(ProgressState.Same, stateOf(now, before))
    }

    @Test
    fun rpeIsTheHighestAtTheTopLoad() {
        val s = session(0, set(80.0, 8, 6.0), set(80.0, 8, 8.5), set(60.0, 12, 10.0))

        assertEquals(8.5, s.rpeAtTop)
    }

    @Test
    fun rangeChange_isUncompared() {
        val before = session(0, *sets(1, 70.0, 12), rmin = 8, rmax = 12)
        val now = session(7, *sets(1, 72.5, 10), rmin = 6, rmax = 10)

        assertEquals(ProgressState.Uncompared, stateOf(now, before))
    }

    @Test
    fun rangeAppearing_isUncompared() {
        val before = session(0, *sets(1, 80.0, 8))
        val now = session(7, *sets(1, 82.5, 8), rmin = 6, rmax = 10)

        assertEquals(ProgressState.Uncompared, stateOf(now, before))
    }

    @Test
    fun gapOf13Days_isCompared() {
        val before = session(0, *sets(1, 80.0, 8))
        val now = session(13, *sets(1, 70.0, 8))

        assertEquals(ProgressState.Down, stateOf(now, before))
    }

    @Test
    fun gapOf14Days_isUncompared() {
        val before = session(0, *sets(1, 80.0, 8))
        val now = session(14, *sets(1, 70.0, 8))

        assertEquals(ProgressState.Uncompared, stateOf(now, before))
    }

    @Test
    fun deloadThisSession_isUncompared() {
        val before = session(0, *sets(1, 80.0, 8))
        val now = session(7, *sets(1, 60.0, 8), deload = true)

        assertEquals(ProgressState.Uncompared, stateOf(now, before))
    }

    @Test
    fun deloadPreviousSession_isUncompared() {
        val before = session(0, *sets(1, 60.0, 8), deload = true)
        val now = session(7, *sets(1, 80.0, 8))

        assertEquals(ProgressState.Uncompared, stateOf(now, before))
    }

    @Test
    fun noPreviousSession_isUncomparedAndKeepsTheCurrentOne() {
        val now = session(7, *sets(1, 80.0, 8))

        val result = compare(now, null)

        assertEquals(ProgressState.Uncompared, result.state)
        assertNull(result.before)
        assertEquals(now, result.now)
    }

    @Test
    fun sameLoadWithADifferentNumberOfSets_comparesOnlyTheSharedOnes() {
        val before = session(0, *sets(4, 80.0, 8))
        val now = session(7, *sets(2, 80.0, 8))

        assertEquals(ProgressState.Same, stateOf(now, before))
    }

    @Test
    fun noSharedSets_isSame() {
        val before = session(0, set(80.0, 8))
        val now = session(7, set(80.0, 8)).copy(repsAtTop = emptyList())

        assertEquals(ProgressState.Same, stateOf(now, before))
    }

    @Test
    fun openDiscomfort_turnsSameAndDownIntoPaused_butUpStaysUp() {
        val before = session(0, *sets(3, 80.0, 8))

        assertEquals(ProgressState.Paused, stateOf(session(7, *sets(3, 80.0, 8)), before, openDiscomfort = true))
        assertEquals(ProgressState.Paused, stateOf(session(7, *sets(3, 77.5, 8)), before, openDiscomfort = true))
        assertEquals(ProgressState.Up, stateOf(session(7, *sets(3, 82.5, 8)), before, openDiscomfort = true))
    }

    @Test
    fun pausedIsNeverHold() {
        val before = session(0, *sets(3, 80.0, 8))
        val now = session(7, *sets(3, 80.0, 8))

        assertFalse(compare(now, before, inCut = true, openDiscomfort = true).hold)
    }

    @Test
    fun theTopLoadAndItsRepsComeFromTheHeaviestSets() {
        val s = session(0, set(100.0, 5), set(80.0, 8), set(100.0, 4), rmin = 3, rmax = 6)

        assertEquals(100.0, s.topLoadKg)
        assertEquals(listOf(5, 4), s.repsAtTop)
        assertEquals(1, s.margin)
        assertTrue(s.ranged)
    }

    @Test
    fun aSessionWithoutSets_hasNothingToCompare() {
        assertNull(ComparableSession.of(ComparisonSession("s", day0, false, emptyList())))
    }

    // ---- Parity with supabase/tests/coach_week_progress.test.sql (strength states, T-313) ----------------------

    private class Case(
        val name: String,
        val previous: ComparableSession?,
        val current: ComparableSession,
        val openDiscomfort: Boolean,
        val expected: ProgressState,
    )

    @Suppress("LongMethod") // One row per fixture of the SQL file.
    private fun parityTable(): List<Case> = listOf(
        // lines 194-204: iso(1)..iso(11); baseline day 3, compared session day 15.
        Case(
            "iso1 more load, every set at or above rep_min",
            session(3, *sets(3, 80.0, 8), rmin = 6, rmax = 10),
            session(15, *sets(3, 82.5, 6), rmin = 6, rmax = 10),
            false,
            ProgressState.Up,
        ),
        Case(
            "iso2 more load, a set under rep_min",
            session(3, *sets(3, 80.0, 8), rmin = 6, rmax = 10),
            session(15, set(82.5, 6), set(82.5, 6), set(82.5, 5), rmin = 6, rmax = 10),
            false,
            ProgressState.Same,
        ),
        Case(
            "iso3 no range, more load at the lowest reps",
            session(3, set(80.0, 8), set(80.0, 7), set(80.0, 6)),
            session(15, *sets(3, 82.5, 6)),
            false,
            ProgressState.Up,
        ),
        Case(
            "iso4 no range, more load under the lowest reps",
            session(3, set(80.0, 8), set(80.0, 7), set(80.0, 6)),
            session(15, set(82.5, 6), set(82.5, 5)),
            false,
            ProgressState.Same,
        ),
        Case(
            "iso5 same load, 25 > 24 over 3 shared sets",
            session(3, *sets(4, 80.0, 8), rmin = 6, rmax = 10),
            session(15, set(80.0, 9), set(80.0, 9), set(80.0, 7), rmin = 6, rmax = 10),
            false,
            ProgressState.Up,
        ),
        Case(
            "iso6 less load",
            session(3, *sets(3, 80.0, 8), rmin = 6, rmax = 10),
            session(15, *sets(3, 77.5, 10), rmin = 6, rmax = 10),
            false,
            ProgressState.Down,
        ),
        Case(
            "iso7 same load, fewer reps",
            session(3, *sets(3, 80.0, 8), rmin = 6, rmax = 10),
            session(15, set(80.0, 8), set(80.0, 8), set(80.0, 7), rmin = 6, rmax = 10),
            false,
            ProgressState.Down,
        ),
        Case(
            "iso8 same load and reps",
            session(3, *sets(3, 80.0, 8), rmin = 6, rmax = 10),
            session(15, *sets(3, 80.0, 8), rmin = 6, rmax = 10),
            false,
            ProgressState.Same,
        ),
        Case(
            "iso9 load increase at RPE +2",
            session(3, *sets(2, 80.0, 8, 7.0), rmin = 6, rmax = 10),
            session(15, *sets(2, 82.5, 8, 9.0), rmin = 6, rmax = 10),
            false,
            ProgressState.Same,
        ),
        Case(
            "iso10 load increase at RPE +1",
            session(3, *sets(2, 80.0, 8, 7.0), rmin = 6, rmax = 10),
            session(15, *sets(2, 82.5, 8, 8.0), rmin = 6, rmax = 10),
            false,
            ProgressState.Up,
        ),
        Case(
            "iso11 RPE missing on the baseline",
            session(3, *sets(2, 80.0, 8), rmin = 6, rmax = 10),
            session(15, *sets(2, 82.5, 8, 10.0), rmin = 6, rmax = 10),
            false,
            ProgressState.Up,
        ),
        // lines 216-232: iso(13)..iso(19).
        Case(
            "iso13 range change reset the baseline to day 8, compared with it",
            session(8, set(70.0, 12), rmin = 8, rmax = 12),
            session(15, set(72.5, 10), rmin = 8, rmax = 12),
            false,
            ProgressState.Up,
        ),
        Case(
            "iso13 range change: the day-3 session with another range is not compared",
            session(3, set(80.0, 8), rmin = 6, rmax = 10),
            session(8, set(70.0, 12), rmin = 8, rmax = 12),
            false,
            ProgressState.Uncompared,
        ),
        Case(
            "iso14 a gap of exactly 14 days",
            session(1, set(80.0, 8)),
            session(15, set(70.0, 8)),
            false,
            ProgressState.Uncompared,
        ),
        Case(
            "iso15 a gap of 13 days keeps the baseline",
            session(2, set(80.0, 8)),
            session(15, set(70.0, 8)),
            false,
            ProgressState.Down,
        ),
        Case(
            "iso16 swapped in on day 8, own baseline",
            session(8, set(50.0, 10), rmin = 8, rmax = 12),
            session(15, set(52.5, 10), rmin = 8, rmax = 12),
            false,
            ProgressState.Up,
        ),
        Case(
            "iso17 warm-up, drop and failure sets ignored (the RPC returns working sets only)",
            session(3, *sets(3, 80.0, 8), rmin = 6, rmax = 10),
            session(15, *sets(3, 80.0, 8), rmin = 6, rmax = 10),
            false,
            ProgressState.Same,
        ),
        Case(
            "iso18 one session",
            null,
            session(15, set(60.0, 10)),
            false,
            ProgressState.Uncompared,
        ),
        Case(
            "iso19 carried comparison on the latest session",
            session(3, set(80.0, 8)),
            session(9, set(82.5, 8)),
            false,
            ProgressState.Up,
        ),
        // lines 246-261: paused rows run with an open discomfort (Stalled rows excluded).
        Case(
            "main4 flat with an open discomfort",
            session(9, set(100.0, 5)),
            session(15, set(100.0, 5)),
            true,
            ProgressState.Paused,
        ),
        Case(
            "iso20 same plus a level 2 within 7 days",
            session(3, *sets(3, 80.0, 8), rmin = 6, rmax = 10),
            session(15, *sets(3, 80.0, 8), rmin = 6, rmax = 10),
            true,
            ProgressState.Paused,
        ),
        Case(
            "iso21 down plus an unresolved level 5",
            session(3, *sets(3, 80.0, 8), rmin = 6, rmax = 10),
            session(15, *sets(3, 77.5, 8), rmin = 6, rmax = 10),
            true,
            ProgressState.Paused,
        ),
        Case(
            "iso22 up plus a level 2 stays up",
            session(3, *sets(3, 80.0, 8), rmin = 6, rmax = 10),
            session(15, *sets(3, 82.5, 6), rmin = 6, rmax = 10),
            true,
            ProgressState.Up,
        ),
        Case(
            "iso23 a level 2 exactly 7 days old is no longer open",
            session(3, set(80.0, 8), rmin = 6, rmax = 10),
            session(15, set(80.0, 8), rmin = 6, rmax = 10),
            false,
            ProgressState.Same,
        ),
        Case(
            "iso24 resolved after the week's end is still open",
            session(3, set(80.0, 8), rmin = 6, rmax = 10),
            session(15, set(80.0, 8), rmin = 6, rmax = 10),
            true,
            ProgressState.Paused,
        ),
        Case(
            "iso26 one session plus a level 1",
            null,
            session(15, set(60.0, 10)),
            true,
            ProgressState.Paused,
        ),
    )

    @Test
    fun matchesTheWeeklyReportStrengthStates() {
        val failures = parityTable().mapNotNull { case ->
            val actual = stateOf(case.current, case.previous, case.openDiscomfort)
            "${case.name}: expected ${case.expected} but was $actual".takeIf { actual != case.expected }
        }

        assertEquals(emptyList(), failures)
    }
}
