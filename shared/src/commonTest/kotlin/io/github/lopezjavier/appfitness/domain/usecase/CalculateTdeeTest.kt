package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculateTdeeTest {
    private val today = LocalDate(2026, 10, 14)
    private val birthAt26 = LocalDate(2000, 3, 1)

    @Test
    fun maleExampleOfTheBusinessRules_is2681() {
        assertEquals(2681, CalculateTdee(Sex.Male, birthAt26, 178, 74.2, ActivityLevel.Moderate, today))
    }

    @Test
    fun femaleUsesTheMinus161Offset() {
        // BMR = 10*60 + 6.25*165 - 5*26 - 161 = 1340.25; x 1.375 = 1842.8
        assertEquals(1843, CalculateTdee(Sex.Female, birthAt26, 165, 60.0, ActivityLevel.Light, today))
    }

    @Test
    fun everyActivityFactorIsApplied() {
        // BMR of the male example = 1729.5
        val expected = mapOf(
            ActivityLevel.Sedentary to 2075,
            ActivityLevel.Light to 2378,
            ActivityLevel.Moderate to 2681,
            ActivityLevel.High to 2983,
            ActivityLevel.VeryHigh to 3286,
        )
        expected.forEach { (level, kcal) ->
            assertEquals(kcal, CalculateTdee(Sex.Male, birthAt26, 178, 74.2, level, today), level.name)
        }
    }

    @Test
    fun ageCountsFullYears() {
        val dayBefore = LocalDate(2000, 10, 15) // turns 26 tomorrow: still 25
        val onBirthday = LocalDate(2000, 10, 14)
        val at25 = CalculateTdee(Sex.Male, dayBefore, 178, 74.2, ActivityLevel.Moderate, today)
        val at26 = CalculateTdee(Sex.Male, onBirthday, 178, 74.2, ActivityLevel.Moderate, today)
        assertEquals(2681, at26)
        assertEquals(2688, at25) // BMR 1734.5 x 1.55 = 2688.5
    }
}
