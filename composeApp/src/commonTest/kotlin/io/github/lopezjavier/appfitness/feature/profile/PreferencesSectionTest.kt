package io.github.lopezjavier.appfitness.feature.profile

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferencesSectionTest {
    private val base = PreferencesForm(
        name = "Ana",
        sex = Sex.Male,
        birthDate = LocalDate(2000, 3, 1),
        heightCm = 178,
        weightKg = 80.0,
        activity = ActivityLevel.Moderate,
        strategy = GoalStrategy.Deficit,
        adjustment = -400,
        split = MacroSplit.DEFAULT,
    )

    /** One form per section with only that section's fields changed. */
    private val changes = mapOf(
        PreferencesSection.PersonalData to base.copy(
            name = "Bea",
            sex = Sex.Female,
            birthDate = LocalDate(1990, 1, 1),
            heightCm = 160,
            weightKg = 60.0,
            activity = ActivityLevel.High,
        ),
        PreferencesSection.EnergyGoal to base.copy(strategy = GoalStrategy.Surplus, adjustment = 250),
        PreferencesSection.Macros to base.copy(split = MacroSplit(35, 42, 23)),
        PreferencesSection.Cooking to base.copy(foodPreferences = FoodPreferencesForm("1", "2", "3")),
        PreferencesSection.Training to base.copy(
            warmUpIncluded = true,
            previousValuesMode = PreviousValuesMode.Routine,
            calculators = CalculatorSettings(barWeightKg = 15.0),
            autoUpdateRoutine = true,
        ),
    )

    @Test
    fun sectionEquals_isFalseOnlyForTheSectionWhoseFieldsChanged() {
        for ((owner, changed) in changes) {
            for (section in PreferencesSection.entries) {
                assertEquals(section != owner, base.sectionEquals(section, changed), "edit of $owner seen by $section")
            }
        }
    }

    @Test
    fun withSectionFrom_movesOnlyTheFieldsTheSectionOwns() {
        for ((owner, changed) in changes) {
            assertEquals(changed, base.withSectionFrom(owner, changed), "$owner takes its own fields")
            for (other in PreferencesSection.entries.filter { it != owner }) {
                assertEquals(base, base.withSectionFrom(other, changed), "$other ignores the edit of $owner")
            }
        }
    }

    @Test
    fun personalData_ownsTheWeight() {
        val heavier = base.copy(weightKg = 85.0)

        assertFalse(base.sectionEquals(PreferencesSection.PersonalData, heavier))
        assertEquals(85.0, base.withSectionFrom(PreferencesSection.PersonalData, heavier).weightKg)
    }

    @Test
    fun fromKey_findsEachSection_andFallsBackToPersonalData() {
        PreferencesSection.entries.forEach { assertEquals(it, PreferencesSection.fromKey(it.key)) }
        assertEquals(PreferencesSection.PersonalData, PreferencesSection.fromKey(null))
        assertEquals(PreferencesSection.PersonalData, PreferencesSection.fromKey("nope"))
        assertTrue(PreferencesSection.entries.map { it.key }.toSet().size == PreferencesSection.entries.size)
    }
}
