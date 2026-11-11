package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-276 (US-134): the pencil of the week in progress, derived from the week and the block's pause. */
class ActiveWeekExtensionTest {
    private val weekStart = LocalDate(2026, 11, 9)
    private val weekEnd = LocalDate(2026, 11, 15)
    private val blockEnd = LocalDate(2026, 12, 6)

    private fun week(phase: WeekPhase = WeekPhase.Current, end: LocalDate = weekEnd) = PlanWeek(
        week = 3,
        startDate = weekStart,
        endDate = end,
        phase = phase,
        deload = false,
        planned = 4,
        completed = 1,
        adherencePct = 25,
        sessions = emptyList(),
    )

    private fun pause(
        end: LocalDate? = blockEnd,
        min: LocalDate? = blockEnd,
        max: LocalDate? = LocalDate(2026, 12, 20),
        extendable: Boolean = true,
    ) = BlockPause(false, 0, end, minEnd = min, maxEnd = max, extendable = extendable)

    private fun extension(weeks: List<PlanWeek> = listOf(week()), pause: BlockPause? = pause()) =
        ActiveWeekExtension.of(weeks, pause)

    @Test
    fun noCurrentWeek_isNull() {
        assertNull(extension(weeks = listOf(week(WeekPhase.Past), week(WeekPhase.Upcoming))))
        assertNull(extension(weeks = emptyList()))
    }

    @Test
    fun notExtendableOrNoPause_isNull() {
        assertNull(extension(pause = pause(extendable = false)))
        assertNull(extension(pause = null))
    }

    @Test
    fun missingLimitsOrBlockEnd_isNull() {
        assertNull(extension(pause = pause(min = null)))
        assertNull(extension(pause = pause(max = null)))
        assertNull(extension(pause = pause(end = null)))
    }

    @Test
    fun withoutEarlierExtension_rangeIsTheWeekEndUpToFourteenDaysLater() {
        val ext = assertNotNull(extension())

        assertEquals(weekEnd, ext.selected)
        assertEquals(LocalDate(2026, 11, 15), ext.minDay)
        assertEquals(LocalDate(2026, 11, 29), ext.maxDay)
        assertTrue(ext.contains(weekEnd))
        assertTrue(ext.contains(LocalDate(2026, 11, 29)))
        assertFalse(ext.contains(LocalDate(2026, 11, 14)))
        assertFalse(ext.contains(LocalDate(2026, 11, 30)))
    }

    @Test
    fun weekAlreadyExtended_minIsTheOriginalEndAndSelectedTheRealEnd() {
        // Original week end Nov 15, extended by 3: week ends Nov 18, block ends Dec 9; limits are the RPC's.
        val ext = assertNotNull(
            extension(
                weeks = listOf(week(end = LocalDate(2026, 11, 18))),
                pause = pause(
                    end = LocalDate(2026, 12, 9),
                    min = LocalDate(2026, 12, 6),
                    max = LocalDate(2026, 12, 20),
                ),
            ),
        )

        assertEquals(LocalDate(2026, 11, 18), ext.selected)
        assertEquals(LocalDate(2026, 11, 15), ext.minDay)
        assertEquals(LocalDate(2026, 11, 29), ext.maxDay)
        assertEquals(blockEnd, ext.blockEndFor(LocalDate(2026, 11, 15)))
    }

    @Test
    fun todayBeyondTheOriginalEnd_minIsToday() {
        // The RPC's minEnd is today's day when it is past the original block end; the week range follows.
        val ext = assertNotNull(extension(pause = pause(min = LocalDate(2026, 12, 8), max = LocalDate(2026, 12, 20))))

        assertEquals(LocalDate(2026, 11, 17), ext.minDay)
    }

    @Test
    fun earlierWeekExtension_shrinksTheMaximum() {
        val ext = assertNotNull(extension(pause = pause(max = LocalDate(2026, 12, 11))))

        assertEquals(LocalDate(2026, 11, 20), ext.maxDay)
    }

    @Test
    fun blockEndFor_addsTheWeekShiftToTheBlockEnd() {
        val ext = assertNotNull(extension())

        assertEquals(LocalDate(2026, 12, 9), ext.blockEndFor(LocalDate(2026, 11, 18)))
        assertEquals(blockEnd, ext.blockEndFor(weekEnd))
        assertEquals(LocalDate(2026, 12, 20), ext.blockEndFor(LocalDate(2026, 11, 29)))
    }
}
