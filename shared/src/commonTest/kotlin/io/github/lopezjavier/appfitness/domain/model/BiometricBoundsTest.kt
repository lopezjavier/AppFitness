package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BiometricBoundsTest {
    private val today = LocalDate(2026, 10, 14)

    @Test
    fun ageOn_beforeOnAndAfterTheBirthday() {
        val birth = LocalDate(1999, 10, 15)
        assertEquals(26, BiometricBounds.ageOn(birth, LocalDate(2026, 10, 14)))
        assertEquals(27, BiometricBounds.ageOn(birth, LocalDate(2026, 10, 15)))
        assertEquals(27, BiometricBounds.ageOn(birth, LocalDate(2026, 12, 31)))
        assertEquals(26, BiometricBounds.ageOn(birth, LocalDate(2026, 1, 1)))
    }

    @Test
    fun ageOn_leapDayBirthday_isReachedOnMarchFirstInANormalYear() {
        val birth = LocalDate(2000, 2, 29)
        assertEquals(24, BiometricBounds.ageOn(birth, LocalDate(2025, 2, 28)))
        assertEquals(25, BiometricBounds.ageOn(birth, LocalDate(2025, 3, 1)))
        assertEquals(24, BiometricBounds.ageOn(birth, LocalDate(2024, 2, 29)))
    }

    @Test
    fun birthDateRange_endsForAFixedToday() {
        val range = BiometricBounds.birthDateRange(today)
        assertEquals(LocalDate(1925, 10, 15), range.start)
        assertEquals(LocalDate(2012, 10, 14), range.endInclusive)
        assertEquals(100, BiometricBounds.ageOn(range.start, today))
        assertEquals(14, BiometricBounds.ageOn(range.endInclusive, today))
    }

    @Test
    fun ageEdges_13and14_100and101() {
        val range = BiometricBounds.birthDateRange(today)
        val age13 = LocalDate(2013, 10, 14)
        val age101 = LocalDate(1925, 10, 14)
        assertEquals(13, BiometricBounds.ageOn(age13, today))
        assertEquals(101, BiometricBounds.ageOn(age101, today))
        assertTrue(age13 !in range)
        assertTrue(age101 !in range)
    }

    @Test
    fun rangeValidation_ageTwelveClampsToFourteen() {
        val twelve = LocalDate(2014, 10, 14)
        assertEquals(12, BiometricBounds.ageOn(twelve, today))
        val clamped = BiometricBounds.clampBirthDate(twelve, today)
        assertEquals(14, BiometricBounds.ageOn(clamped, today))
    }

    @Test
    fun clampBirthDate_tooOldClampsToOldest_validIsKept() {
        assertEquals(LocalDate(1925, 10, 15), BiometricBounds.clampBirthDate(LocalDate(1900, 1, 1), today))
        assertEquals(LocalDate(1999, 6, 15), BiometricBounds.clampBirthDate(LocalDate(1999, 6, 15), today))
    }

    @Test
    fun clampHeight_edges() {
        assertEquals(100, BiometricBounds.clampHeight(99))
        assertEquals(100, BiometricBounds.clampHeight(100))
        assertEquals(250, BiometricBounds.clampHeight(250))
        assertEquals(250, BiometricBounds.clampHeight(251))
        assertEquals(250, BiometricBounds.clampHeight(260))
    }

    @Test
    fun clampWeightTenths_edges() {
        assertEquals(200, BiometricBounds.clampWeightTenths(199))
        assertEquals(200, BiometricBounds.clampWeightTenths(200))
        assertEquals(4000, BiometricBounds.clampWeightTenths(4000))
        assertEquals(4000, BiometricBounds.clampWeightTenths(4001))
        assertEquals(200, BiometricBounds.clampWeightTenths(150))
    }

    @Test
    fun daysIn_februaryLeapAndNormal_andOtherMonths() {
        assertEquals(29, BiometricBounds.daysIn(2000, 2))
        assertEquals(29, BiometricBounds.daysIn(2024, 2))
        assertEquals(28, BiometricBounds.daysIn(2025, 2))
        assertEquals(28, BiometricBounds.daysIn(1900, 2))
        assertEquals(30, BiometricBounds.daysIn(2025, 4))
        assertEquals(31, BiometricBounds.daysIn(2025, 12))
        assertFailsWith<IllegalArgumentException> { BiometricBounds.daysIn(2025, 13) }
    }

    @Test
    fun composeBirthDate_cutsTheDayToTheMonthAndClampsToTheRange() {
        assertEquals(LocalDate(2001, 2, 28), BiometricBounds.composeBirthDate(2001, 2, 31, today))
        assertEquals(LocalDate(2000, 2, 29), BiometricBounds.composeBirthDate(2000, 2, 31, today))
        assertEquals(LocalDate(2012, 10, 14), BiometricBounds.composeBirthDate(2013, 3, 1, today))
        assertEquals(LocalDate(1925, 10, 15), BiometricBounds.composeBirthDate(1925, 1, 1, today))
    }

    @Test
    fun defaults_areThirtyYearsAndInsideTheRanges() {
        assertEquals(LocalDate(1996, 10, 14), BiometricBounds.defaultBirthDate(today))
        assertEquals(30, BiometricBounds.ageOn(BiometricBounds.defaultBirthDate(today), today))
        assertTrue(BiometricBounds.DEFAULT_HEIGHT_CM in BiometricBounds.HEIGHT_CM)
        assertTrue(BiometricBounds.DEFAULT_WEIGHT_TENTHS in BiometricBounds.WEIGHT_TENTHS)
    }
}
