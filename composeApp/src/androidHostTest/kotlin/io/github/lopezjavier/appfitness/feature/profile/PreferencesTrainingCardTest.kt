package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
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
@Suppress("TooManyFunctions") // One method per scenario.
class PreferencesTrainingCardTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<PreferencesEvent>()

    private fun form(
        warmUp: Boolean,
        mode: PreviousValuesMode = PreviousValuesMode.Overall,
        calculators: CalculatorSettings = CalculatorSettings(),
    ) = PreferencesForm(
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
        previousValuesMode = mode,
        calculators = calculators,
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

    @Test
    fun previousValuesSelector_showsBothOptions_withOverallSelectedByDefault() {
        rule.setContent { AppTheme { PreferencesScreen(loaded(), {}) } }

        rule.onNodeWithTag("preferences_previous_mode").performScrollTo()
        rule.onNodeWithText("Valores anteriores").assertExists()
        rule.onNodeWithTag("preferences_previous_mode_routine").assertExists()
        rule.onNodeWithTag("preferences_previous_mode_overall").assertIsSelected()
    }

    @Test
    fun choosingTheRoutineMode_sendsItAndWaitsForGuardar() {
        rule.setContent { AppTheme { PreferencesScreen(loaded(), { events += it }) } }

        rule.onNodeWithTag("preferences_previous_mode_routine").performScrollTo().performClick()

        assertEquals(
            listOf<PreferencesEvent>(PreferencesEvent.SetPreviousValuesMode(PreviousValuesMode.Routine)),
            events,
        )
        rule.onNodeWithTag("preferences_save").assertIsNotEnabled()
    }

    @Test
    fun previousValuesMode_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            var routine by rememberSaveable { mutableStateOf(false) }
            AppTheme {
                PreferencesScreen(
                    state = PreferencesUiState.Loaded(
                        form = form(false, if (routine) PreviousValuesMode.Routine else PreviousValuesMode.Overall),
                        saved = form(false),
                        today = LocalDate(2026, 10, 14),
                    ),
                    onEvent = { if (it is PreferencesEvent.SetPreviousValuesMode) routine = true },
                )
            }
        }
        rule.onNodeWithTag("preferences_previous_mode_routine").performScrollTo().performClick()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_previous_mode_routine").performScrollTo().assertIsSelected()
    }

    private fun withCalculators(calculators: CalculatorSettings, platesError: Boolean = false) =
        PreferencesUiState.Loaded(
            form = form(false, calculators = calculators),
            saved = form(false),
            today = LocalDate(2026, 10, 14),
            platesError = platesError,
        )

    @Test
    fun roundingHelp_opensItsExplanation_andCloses() {
        rule.setContent { AppTheme { PreferencesScreen(loaded(), {}) } }

        rule.onNodeWithTag("rounding_help").performScrollTo().performClick()
        rule.onNodeWithTag("rounding_help_sheet").assertExists()
        rule.onNodeWithTag("rounding_help_sheet_close").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("rounding_help_sheet").assertDoesNotExist()
    }

    @Test
    fun previousModeHelp_opensItsExplanation_andCloses() {
        rule.setContent { AppTheme { PreferencesScreen(loaded(), {}) } }

        rule.onNodeWithTag("previous_mode_help").performScrollTo().performClick()
        rule.onNodeWithTag("previous_mode_help_sheet").assertExists()
        rule.onNodeWithTag("previous_mode_help_sheet_close").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("previous_mode_help_sheet").assertDoesNotExist()
    }

    @Test
    fun calculatorsBlock_showsTheDefaultsInsideTheTrainingCard() {
        rule.setContent { AppTheme { PreferencesScreen(loaded(), {}) } }

        rule.onNodeWithTag("preferences_plate_rounding_value").performScrollTo().assertTextEquals("2,5 kg")
        rule.onNodeWithTag("preferences_dumbbell_rounding_value").performScrollTo().assertTextEquals("2 kg")
        rule.onNodeWithText("Calculadoras").assertExists()
        rule.onNodeWithTag("preferences_bar_default_20").performScrollTo().assertIsSelected()
        listOf("25", "20", "15", "10", "5", "2_5", "2", "1_25", "1", "0_5").forEach {
            rule.onNodeWithTag("preferences_plate_$it").performScrollTo().assertIsOn()
        }
    }

    @Test
    fun roundingSteppers_sendTheNewStep_withTheirDescriptions() {
        rule.setContent { AppTheme { PreferencesScreen(loaded(), { events += it }) } }

        rule.onNodeWithTag("preferences_plate_rounding_increment").performScrollTo().performClick()
        rule.onNodeWithTag("preferences_dumbbell_rounding_decrement").performScrollTo().performClick()

        assertEquals(
            listOf<PreferencesEvent>(
                PreferencesEvent.SetPlateRounding(3.0),
                PreferencesEvent.SetDumbbellRounding(1.5),
            ),
            events,
        )
        val state = rule.onNodeWithTag("preferences_plate_rounding").fetchSemanticsNode().config
        assertEquals("Redondeo de discos: 2,5 kg", state[SemanticsProperties.StateDescription])
        val cd = rule.onNodeWithTag("preferences_plate_rounding_decrement").fetchSemanticsNode().config
        assertEquals(listOf("Reducir redondeo de discos"), cd[SemanticsProperties.ContentDescription])
        rule.onNodeWithTag("preferences_plate_rounding_increment").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun barSelector_sendsTheChosenBar() {
        rule.setContent { AppTheme { PreferencesScreen(loaded(), { events += it }) } }

        rule.onNodeWithTag("preferences_bar_default_7_5").performScrollTo().performClick()

        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.SetBarWeight(7.5)), events)
    }

    @Test
    fun plateChips_toggleIndependently_with48dpTargetsAndDescriptions() {
        rule.setContent { AppTheme { PreferencesScreen(loaded(), { events += it }) } }

        rule.onNodeWithTag("preferences_plate_2_5").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.SetPlateEnabled(2.5, false)), events)
        val cd = rule.onNodeWithTag("preferences_plate_2_5").fetchSemanticsNode().config
        assertEquals(listOf("Disco de 2,5 kg"), cd[SemanticsProperties.ContentDescription])
    }

    @Test
    fun aSwitchedOffPlate_isShownOff_andChangesEnableGuardar() {
        val settings = CalculatorSettings(plateKg = CalculatorSettings.PLATE_CATALOGUE - 2.5)
        rule.setContent { AppTheme { PreferencesScreen(withCalculators(settings), {}) } }

        rule.onNodeWithTag("preferences_plate_2_5").performScrollTo().assertIsOff()
        rule.onNodeWithTag("preferences_plate_5").performScrollTo().assertIsOn()
        rule.onNodeWithTag("preferences_save").assertIsEnabled()
    }

    @Test
    fun lastPlate_keepsItsStateAndShowsTheError_withGuardarOnlyForRealChanges() {
        val settings = CalculatorSettings(plateKg = listOf(25.0))
        rule.setContent { AppTheme { PreferencesScreen(withCalculators(settings, platesError = true), {}) } }

        rule.onNodeWithTag("preferences_plate_25").performScrollTo().assertIsOn()
        rule.onNodeWithTag("preferences_plates_error").performScrollTo().assertTextEquals("Deja al menos un disco")
    }

    @Test
    fun noError_whenPlatesAreFine() {
        rule.setContent { AppTheme { PreferencesScreen(loaded(), {}) } }

        rule.onNodeWithTag("preferences_plates_error").assertDoesNotExist()
    }

    @Test
    fun calculatorDraft_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            var bar by rememberSaveable { mutableDoubleStateOf(20.0) }
            AppTheme {
                PreferencesScreen(
                    state = withCalculators(CalculatorSettings(barWeightKg = bar)),
                    onEvent = { if (it is PreferencesEvent.SetBarWeight) bar = it.kg },
                )
            }
        }
        rule.onNodeWithTag("preferences_bar_default_10").performScrollTo().performClick()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_bar_default_10").performScrollTo().assertIsSelected()
    }
}
