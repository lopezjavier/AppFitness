package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-29 "Toggling inclusion" on Preferencias: the Entrenamiento card (ADR-45: saved only by "Guardar"). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class PreferencesTrainingCardTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<PreferencesEvent>()

    private fun form(warmUp: Boolean) = PreferencesForm(
        name = "Ana",
        sex = Sex.Male,
        birthDate = LocalDate(2000, 3, 1),
        heightCm = 180,
        weightKg = 100.0,
        activity = ActivityLevel.Sedentary,
        strategy = GoalStrategy.Maintenance,
        adjustment = 0,
        split = MacroSplit.DEFAULT,
        warmUpIncluded = warmUp,
    )

    private fun loaded(warmUp: Boolean = false, saved: Boolean = false) = PreferencesUiState.Loaded(
        form = form(warmUp),
        saved = form(saved),
        today = LocalDate(2026, 10, 14),
    )

    @Test
    fun card_showsTitleLabelAndHelper_withTheSwitchOffByDefault() {
        rule.setContent { AppTheme { PreferencesScreen(loaded(), { events += it }) } }

        rule.onNodeWithTag("preferences_training_card").performScrollTo()
        rule.onNodeWithText("Entrenamiento").assertExists()
        rule.onNodeWithText("Incluir calentamiento").assertExists()
        rule.onNodeWithText("Las series de calentamiento cuentan en volumen, 1RM y récords.").assertExists()
        rule.onNodeWithTag("preferences_warm_up_switch").assertIsOff()
        rule.onNodeWithTag("preferences_save").assertIsNotEnabled()
    }

    @Test
    fun tappingTheRow_sendsTheToggle() {
        rule.setContent { AppTheme { PreferencesScreen(loaded(), { events += it }) } }

        rule.onNodeWithTag("preferences_warm_up_switch").performScrollTo().performClick()

        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.SetWarmUpIncluded(true)), events)
    }

    @Test
    fun rowHasTheTouchTarget() {
        rule.setContent { AppTheme { PreferencesScreen(loaded(), {}) } }

        rule.onNodeWithTag("preferences_warm_up_switch").performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun switchOn_enablesGuardar_andIsShownOn() {
        rule.setContent { AppTheme { PreferencesScreen(loaded(warmUp = true), {}) } }

        rule.onNodeWithTag("preferences_warm_up_switch").performScrollTo().assertIsOn()
        rule.onNodeWithTag("preferences_save").assertIsEnabled()
    }

    @Test
    fun switchState_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            var on by rememberSaveable { mutableStateOf(false) }
            AppTheme {
                PreferencesScreen(
                    state = loaded(warmUp = on),
                    onEvent = { if (it is PreferencesEvent.SetWarmUpIncluded) on = it.included },
                )
            }
        }
        rule.onNodeWithTag("preferences_warm_up_switch").performScrollTo().performClick()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_warm_up_switch").performScrollTo().assertIsOn()
    }
}
