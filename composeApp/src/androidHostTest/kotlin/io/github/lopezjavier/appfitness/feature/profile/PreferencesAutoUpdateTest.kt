package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/** ADR-70: the global "Actualizar automáticamente" switch in the "Entrenamiento" card (R-10: saved by Guardar). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class PreferencesAutoUpdateTest {
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
            SavedStateHandle(mapOf("section" to "training")),
        )
        rule.setContent { AppTheme { PreferencesSectionRoot(onClose = {}, viewModel = viewModel) } }
        rule.waitForIdle()
    }

    @Test
    fun theSwitch_isInTheTrainingCard_withItsHelper_andOffByDefault() {
        show()

        rule.onNodeWithTag("preferences_auto_update_switch").performScrollTo().assertIsOff()
        rule.onNodeWithText("Actualizar automáticamente").assertExists()
        rule.onNodeWithText("Al terminar un entreno de una rutina, la rutina se actualiza sin preguntar.")
            .assertExists()
        rule.onNodeWithTag("preferences_training_save").assertIsNotEnabled()
    }

    @Test
    fun turningItOn_enablesGuardar_andSavesTheFlag() {
        show()

        rule.onNodeWithTag("preferences_auto_update_switch").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_auto_update_switch").assertIsOn()
        rule.onNodeWithTag("preferences_training_save").assertIsEnabled().performClick()
        rule.waitForIdle()

        assertEquals(1, profile.preferenceSaves.size)
        assertEquals(true, profile.preferenceSaves.single().autoUpdateRoutine)
    }
}
