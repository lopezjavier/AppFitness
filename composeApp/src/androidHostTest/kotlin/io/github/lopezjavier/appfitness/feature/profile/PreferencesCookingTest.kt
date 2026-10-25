package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeBodyMeasurementRepository
import io.github.lopezjavier.appfitness.app.FakeClock
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.SavePreferences
import io.github.lopezjavier.appfitness.feature.food.FakeFoodPreferencesRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** US-50 "Changing the preferences" and "Invalid amount": the "Cocina" card of Preferencias. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class PreferencesCookingTest {
    @get:Rule
    val rule = createComposeRule()

    private val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", null, true))).apply {
        biometrics = Result.success(SavedBiometrics(Sex.Male, LocalDate(2000, 3, 1), 178, ActivityLevel.Moderate))
    }

    private fun show() {
        val weights = FakeBodyMeasurementRepository(
            BodyMeasurement("w1", Instant.parse("2026-10-10T08:00:00Z"), 80.0, MeasurementSource.Manual),
        )
        val goals = FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Deficit, -400, 2000, MacroSplit.DEFAULT))
        val viewModel = PreferencesViewModel(
            profile,
            goals,
            weights,
            FakeTrainingPreferencesRepository(),
            FakeFoodPreferencesRepository(),
            SavePreferences(profile),
            FakeConnectivityObserver(),
            FakeClock(),
            TimeZone.UTC,
            SavedStateHandle(),
        )
        rule.setContent { AppTheme { PreferencesRoute(onOpenMeals = {}, viewModel = viewModel) } }
        rule.waitForIdle()
    }

    private fun field(tag: String) = rule.onNodeWithTag(tag).performScrollTo()

    @Test
    fun card_showsTheDefaultsAndWhatEachMethodRetains() {
        show()

        rule.onNodeWithTag("preferences_cooking_card").performScrollTo()
        field("preferences_cooking_griddle").assertTextContains("5")
        field("preferences_cooking_air_fryer").assertTextContains("5")
        field("preferences_cooking_fried").assertTextContains("50")
        rule.onNodeWithText("Aceite a la plancha (g)", ignoreCase = true).assertExists()
        rule.onNodeWithText("Aceite en air fryer (g)", ignoreCase = true).assertExists()
        rule.onNodeWithText("Aceite al freír (g)", ignoreCase = true).assertExists()
        rule.onNodeWithText("Se retiene el 50 %").assertExists()
        rule.onNodeWithText("Se retiene el 100 %").assertExists()
        rule.onNodeWithText("Se retiene el 10 %").assertExists()
        rule.onNodeWithText("El aceite que no se retiene no cuenta.").assertExists()
        rule.onNodeWithTag("preferences_save").assertIsNotEnabled()
    }

    @Test
    fun validEdit_enablesTheSingleGuardar_andSavesTheNewAmounts() {
        show()

        field("preferences_cooking_griddle").performTextClearance()
        field("preferences_cooking_griddle").performTextInput("8,5")
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_save").assertIsEnabled().performClick()
        rule.waitForIdle()

        assertEquals(1, profile.preferenceSaves.size)
        assertEquals(FoodPreferences(8.5, 5.0, 50.0), profile.preferenceSaves.single().foodPreferences)
    }

    @Test
    fun amountAbove500_showsTheError_andDisablesGuardar() {
        show()

        field("preferences_cooking_fried").performTextClearance()
        field("preferences_cooking_fried").performTextInput("501")
        rule.waitForIdle()

        field("preferences_cooking_fried_error").assertTextContains("Introduce un valor entre 0 y 500 g.")
        rule.onNodeWithTag("preferences_save").assertIsNotEnabled()
    }

    @Test
    fun blankAmount_showsTheError_andDisablesGuardar() {
        show()

        field("preferences_cooking_air_fryer").performTextClearance()
        rule.waitForIdle()

        field("preferences_cooking_air_fryer_error").assertTextContains("Introduce un valor", substring = true)
        rule.onNodeWithTag("preferences_save").assertIsNotEnabled()
    }

    @Test
    fun correctingAnInvalidAmount_clearsTheError_andEnablesGuardar() {
        show()
        field("preferences_cooking_fried").performTextClearance()
        field("preferences_cooking_fried").performTextInput("501")

        field("preferences_cooking_fried").performTextClearance()
        field("preferences_cooking_fried").performTextInput("20")
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_cooking_fried_support").assertExists()
        rule.onNodeWithTag("preferences_save").assertIsEnabled()
    }
}
