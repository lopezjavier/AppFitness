package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CURL = 0
private const val BENCH = 1

/** US-33: the warm-up and plate calculators of the exercise menu. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class LiveSessionCalculatorsTest {
    @get:Rule
    val rule = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val clock = MutableClock()
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(
            SessionExercise("e0", CURL, null, listOf(doneSet(1, 20.0, 10))),
            SessionExercise("e1", BENCH, null, listOf(doneSet(1, 100.0, 5))),
        ),
    )
    private val preferences = FakeTrainingPreferencesRepository()
    private val weights = FakeWeightRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()): LiveSessionViewModel {
        val rows = listOf(
            exerciseRow("e0", null, equipment = Equipment.Dumbbell, name = "Curl", custom = true),
            exerciseRow("e1", null, equipment = Equipment.Barbell, name = "Press banca", custom = true),
        )
        return LiveSessionViewModel(
            repository,
            FakeExerciseRepository(rows),
            FakeExerciseLibrary(),
            FakeConnectivityObserver(),
            clock,
            FakeHaptics(),
            FakeRoutineRepository(),
            FakeAlertSound(),
            preferences,
            weights,
            SaveBodyMeasurement(weights, clock, TimeZone.UTC),
            TimeZone.UTC,
            handle,
        )
    }

    private fun show() {
        val vm = newViewModel()
        rule.setContent {
            AppTheme {
                val state by vm.uiState.collectAsState()
                LiveSessionScreen(state, 0, vm::onEvent, {}, onOpenExercise = {})
            }
        }
        settle()
    }

    private fun settle() {
        scheduler.runCurrent()
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        settle()
    }

    private fun openWarmUp(position: Int = BENCH) {
        tap("live_exercise_${position}_menu")
        tap("live_exercise_${position}_menu_warm_up_calc")
    }

    private fun openPlates() {
        tap("live_exercise_${BENCH}_menu")
        tap("live_exercise_${BENCH}_menu_plate_calc")
    }

    private fun type(tag: String, text: String) {
        rule.onNodeWithTag(tag).performTextReplacement(text)
        settle()
    }

    @Test
    fun warmUpHelp_opensItsExplanation_andClosesWithoutLeavingTheCalculator() {
        show()
        openWarmUp()

        tap("warmup_calc_help")
        rule.onNodeWithTag("warmup_calc_help_sheet").assertExists()
        tap("warmup_calc_help_sheet_close")

        rule.onNodeWithTag("warmup_calc_help_sheet").assertDoesNotExist()
        rule.onNodeWithTag("live_exercise_1_warm_up_calc_weight_field").assertExists()
    }

    @Test
    fun warmUp_100kgWith20kgBar_showsOnlyTheSixtyPercentRowByDefault() {
        show()

        openWarmUp()

        val tag = "live_exercise_1_warm_up_calc"
        rule.onNodeWithTag("${tag}_weight_field").assertTextContains("100")
        rule.onNodeWithTag("${tag}_bar_label").assertTextEquals("Barra 20 kg")
        rule.onNodeWithTag("${tag}_bar").assertDoesNotExist()
        rule.onNodeWithTag("${tag}_row_0").assertTextContains("60 kg × 3")
        rule.onNodeWithTag("${tag}_row_1").assertDoesNotExist()
    }

    @Test
    fun warmUp_100kgWith20kgBar_withFourSets_showsTheFullScheme() {
        show()
        openWarmUp()
        repeat(3) { tap("warmup_sets_stepper_increment") }

        val tag = "live_exercise_1_warm_up_calc"
        rule.onNodeWithTag("${tag}_row_0").assertTextContains("20 kg × 10")
        rule.onNodeWithTag("${tag}_row_1").assertTextContains("40 kg × 5")
        rule.onNodeWithTag("${tag}_row_2").assertTextContains("60 kg × 3")
        rule.onNodeWithTag("${tag}_row_3").assertTextContains("80 kg × 2")
        rule.onNodeWithTag("${tag}_row_4").assertDoesNotExist()
        rule.onNodeWithTag("${tag}_row_0").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun warmUp_stepper_changesTheNumberOfRows_withinOneToFour() {
        show()
        openWarmUp()
        val tag = "live_exercise_1_warm_up_calc"
        rule.onNodeWithTag("warmup_sets_stepper_value").assertTextEquals("1")
        rule.onNodeWithTag("warmup_sets_stepper_decrement").assertIsNotEnabled()
        repeat(3) { tap("warmup_sets_stepper_increment") }
        rule.onNodeWithTag("warmup_sets_stepper_increment").assertIsNotEnabled()

        tap("warmup_sets_stepper_decrement")
        tap("warmup_sets_stepper_decrement")

        rule.onNodeWithTag("warmup_sets_stepper_value").assertTextEquals("2")
        rule.onNodeWithTag("${tag}_row_1").assertExists()
        rule.onNodeWithTag("${tag}_row_2").assertDoesNotExist()
        rule.onNodeWithTag("warmup_sets_stepper_decrement").assertHeightIsAtLeast(48.dp)

        tap("warmup_sets_stepper_decrement")
        rule.onNodeWithTag("warmup_sets_stepper_decrement").assertIsNotEnabled()
        rule.onNodeWithTag("${tag}_row_1").assertDoesNotExist()
        tap("warmup_sets_stepper_increment")
        rule.onNodeWithTag("${tag}_row_1").assertExists()
    }

    @Test
    fun warmUp_stepper_hasSpanishLabels() {
        show()
        openWarmUp()

        rule.onNodeWithTag(
            "warmup_sets_stepper_decrement",
        ).assertContentDescriptionEquals("Menos series de calentamiento")
        rule.onNodeWithTag(
            "warmup_sets_stepper_increment",
        ).assertContentDescriptionEquals("Más series de calentamiento")
        rule.onAllNodesWithText("Series de calentamiento").assertCountEquals(1)
    }

    @Test
    fun warmUp_rowsSpeakTheirFullText() {
        show()

        openWarmUp()

        val config = rule.onNodeWithTag("live_exercise_1_warm_up_calc_row_0").fetchSemanticsNode().config
        val description = config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
        kotlin.test.assertEquals(listOf("Calentamiento 1: 60 kilos, 3 repeticiones"), description)
    }

    @Test
    fun warmUp_addSets_putsCSetsBeforeTheWorkingSets() {
        show()
        openWarmUp()

        tap("live_exercise_1_warm_up_calc_confirm")

        rule.onNodeWithTag("live_exercise_1_warm_up_calc_sheet").assertDoesNotExist()
        rule.onAllNodesWithText("C").assertCountEquals(1)
    }

    @Test
    fun warmUp_emptyWeight_disablesTheButtonAndExplainsWhy() {
        show()
        openWarmUp()

        type("live_exercise_1_warm_up_calc_weight_field", "")

        rule.onNodeWithTag("live_exercise_1_warm_up_calc_confirm").assertIsNotEnabled()
        rule.onNodeWithTag("live_exercise_1_warm_up_calc_hint").assertTextEquals("Introduce el peso objetivo")
    }

    @Test
    fun warmUp_weightAtTheBar_showsNoRowsAndTheBarHint() {
        show()
        openWarmUp()

        type("live_exercise_1_warm_up_calc_weight_field", "20")

        rule.onNodeWithTag("live_exercise_1_warm_up_calc_row_0").assertDoesNotExist()
        rule.onNodeWithTag("live_exercise_1_warm_up_calc_confirm").assertIsNotEnabled()
        rule.onNodeWithTag("live_exercise_1_warm_up_calc_hint")
            .assertTextEquals("El peso es igual o inferior a la barra")
    }

    @Test
    fun warmUp_isOfferedForEveryExercise() {
        show()

        tap("live_exercise_0_menu")

        rule.onNodeWithTag("live_exercise_0_menu_warm_up_calc").assertExists().assertIsEnabled()
    }

    @Test
    fun discLoader_startsWithOnlyTheBar_andOffersEveryEnabledPlateHeaviestFirst() {
        show()

        openPlates()

        val tag = "live_exercise_1_disc_loader"
        rule.onNodeWithTag("${tag}_bar_label").assertTextEquals("Barra 20 kg")
        rule.onNodeWithTag("${tag}_total").assertTextEquals("Total: 20 kg")
        rule.onNodeWithTag("${tag}_per_side").assertTextEquals("Por lado: 0 kg")
        listOf("25", "20", "15", "10", "5", "2_5", "2", "1_25", "1", "0_5").forEach {
            rule.onNodeWithTag("${tag}_plate_$it").assertExists()
        }
        rule.onNodeWithTag("${tag}_plate_25_value").assertTextEquals("0")
        rule.onNodeWithTag("${tag}_plate_25_decrement").assertIsNotEnabled()
        rule.onNodeWithTag("${tag}_close").assertExists()
    }

    @Test
    fun discLoader_has_noWeightFieldAndNoBarSelector() {
        show()

        openPlates()

        val tag = "live_exercise_1_disc_loader"
        rule.onNodeWithTag("${tag}_weight_field").assertDoesNotExist()
        rule.onNodeWithTag("${tag}_bar").assertDoesNotExist()
        rule.onNodeWithTag("${tag}_bar_7_5").assertDoesNotExist()
    }

    @Test
    fun discLoader_25And15OnABar20_totals100kg() {
        show()
        openPlates()

        tap("live_exercise_1_disc_loader_plate_25_increment")
        tap("live_exercise_1_disc_loader_plate_15_increment")

        val tag = "live_exercise_1_disc_loader"
        rule.onNodeWithTag("${tag}_total").assertTextEquals("Total: 100 kg")
        rule.onNodeWithTag("${tag}_per_side").assertTextEquals("Por lado: 40 kg")
    }

    @Test
    fun discLoader_repeatedDiscsAndFractions_useTheSpanishDecimalFormat() {
        show()
        openPlates()

        repeat(2) { tap("live_exercise_1_disc_loader_plate_25_increment") }
        tap("live_exercise_1_disc_loader_plate_1_25_increment")

        val tag = "live_exercise_1_disc_loader"
        rule.onNodeWithTag("${tag}_plate_25_value").assertTextEquals("2")
        rule.onNodeWithTag("${tag}_per_side").assertTextEquals("Por lado: 51,25 kg")
        rule.onNodeWithTag("${tag}_total").assertTextEquals("Total: 122,5 kg")
    }

    @Test
    fun discLoader_removingADisc_updatesTheTotal_andNeverGoesBelowZero() {
        show()
        openPlates()
        tap("live_exercise_1_disc_loader_plate_10_increment")

        tap("live_exercise_1_disc_loader_plate_10_decrement")

        val tag = "live_exercise_1_disc_loader"
        rule.onNodeWithTag("${tag}_total").assertTextEquals("Total: 20 kg")
        rule.onNodeWithTag("${tag}_plate_10_decrement").assertIsNotEnabled()
    }

    @Test
    fun discLoader_capsEachWeightAtTenDiscsPerSide() {
        show()
        openPlates()

        repeat(10) { tap("live_exercise_1_disc_loader_plate_5_increment") }

        val tag = "live_exercise_1_disc_loader"
        rule.onNodeWithTag("${tag}_plate_5_value").assertTextEquals("10")
        rule.onNodeWithTag("${tag}_plate_5_increment").assertIsNotEnabled()
    }

    @Test
    fun discLoader_stepperButtons_are48dpAndSpeakTheDisc() {
        show()
        openPlates()
        val tag = "live_exercise_1_disc_loader_plate_25"

        rule.onNodeWithTag("${tag}_increment").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("${tag}_decrement").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("${tag}_increment").assertContentDescriptionEquals("Añadir disco de 25 kg")
        rule.onNodeWithTag("${tag}_decrement").assertContentDescriptionEquals("Quitar disco de 25 kg")
        tap("${tag}_increment")
        rule.onNode(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                "1 disco de 25 kg por lado",
            ),
        ).assertExists()
    }

    @Test
    fun discLoader_onlyOffersThePlatesEnabledInPreferencias() {
        preferences.result = Result.success(TrainingPreferences(plateKg = listOf(20.0, 10.0), barWeightKg = 15.0))
        show()

        openPlates()

        val tag = "live_exercise_1_disc_loader"
        rule.onNodeWithTag("${tag}_bar_label").assertTextEquals("Barra 15 kg")
        rule.onNodeWithTag("${tag}_plate_20").assertExists()
        rule.onNodeWithTag("${tag}_plate_10").assertExists()
        rule.onNodeWithTag("${tag}_plate_25").assertDoesNotExist()
        rule.onNodeWithTag("${tag}_plate_0_5").assertDoesNotExist()
    }

    @Test
    fun discLoader_dumbbellOneDisc10kgOnAOneKgHandle_totals21kg() {
        show()
        tap("live_exercise_0_menu")
        tap("live_exercise_0_menu_plate_calc")

        tap("live_exercise_0_disc_loader_plate_10_increment")

        val tag = "live_exercise_0_disc_loader"
        rule.onNodeWithTag("${tag}_handle_label").assertTextEquals("Mango: 1 kg")
        rule.onNodeWithTag("${tag}_bar_label").assertDoesNotExist()
        rule.onNodeWithTag("${tag}_total").assertTextEquals("Total: 21 kg")
    }

    @Test
    fun discLoader_theHandleShowsTheSavedValue() {
        preferences.result = Result.success(TrainingPreferences(dumbbellHandleKg = 2.5))
        show()
        tap("live_exercise_0_menu")
        tap("live_exercise_0_menu_plate_calc")

        rule.onNodeWithTag("live_exercise_0_disc_loader_handle_label").assertTextEquals("Mango: 2,5 kg")
        rule.onNodeWithTag("live_exercise_0_disc_loader_total").assertTextEquals("Total: 2,5 kg")
    }

    @Test
    fun discLoader_help_opensItsExplanation_andCloses() {
        show()
        openPlates()

        tap("live_exercise_1_disc_loader_help")
        rule.onNodeWithTag("live_exercise_1_disc_loader_help_sheet").assertExists()
        tap("live_exercise_1_disc_loader_help_sheet_close")

        rule.onNodeWithTag("live_exercise_1_disc_loader_help_sheet").assertDoesNotExist()
    }

    @Test
    fun discLoader_closeButtonClosesTheSheet() {
        show()
        openPlates()

        tap("live_exercise_1_disc_loader_close")

        rule.onNodeWithTag("live_exercise_1_disc_loader_sheet").assertDoesNotExist()
    }

    @Test
    fun menu_namesTheEntryCargadorDeDiscos() {
        show()

        tap("live_exercise_0_menu")

        rule.onAllNodesWithText("Cargador de discos").assertCountEquals(1)
        rule.onAllNodesWithText("Calculadora de discos").assertCountEquals(0)
    }

    @Test
    fun discLoader_isOfferedForBarbellAndDumbbellExercises() {
        show()

        tap("live_exercise_0_menu")

        rule.onNodeWithTag("live_exercise_0_menu_plate_calc").assertExists()
        rule.onNodeWithTag("live_exercise_0_menu_warm_up_calc").assertExists()
    }

    @Test
    fun warmUp_roundsByTheDerivedValue_afterTheSmallestPlateChanges() {
        preferences.result = Result.success(
            TrainingPreferences(plateKg = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)),
        )
        show()
        openWarmUp()
        type("live_exercise_1_warm_up_calc_weight_field", "103")

        // 60% of 103 kg is 61,8 kg; the derived rounding is 2 x 1,25 = 2,5 kg, so 62,5 kg (1 kg would give 62).
        rule.onNodeWithTag("live_exercise_1_warm_up_calc_row_0").assertTextContains("62,5 kg × 3")
    }

    @Test
    fun dumbbellWarmUp_neverGoesBelowTheHandle() {
        show()
        tap("live_exercise_0_menu")
        tap("live_exercise_0_menu_warm_up_calc")
        type("live_exercise_0_warm_up_calc_weight_field", "20")
        repeat(2) { tap("warmup_sets_stepper_increment") }

        val tag = "live_exercise_0_warm_up_calc"
        rule.onNodeWithTag("${tag}_bar").assertDoesNotExist()
        rule.onNodeWithTag("${tag}_row_0").assertExists()
        val texts = (0..2).map { i ->
            rule.onNodeWithTag("${tag}_row_$i").fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.ContentDescription,
            ].single()
        }
        kotlin.test.assertTrue(texts.none { it.contains(" 0 kilos") }, texts.toString())
    }

    @Test
    fun discLoaderAndItsCounts_surviveLeavingTheApp() {
        val handle = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                val vm = remember { newViewModel(handle) }
                val state by vm.uiState.collectAsState()
                LiveSessionScreen(state, 0, vm::onEvent, {}, onOpenExercise = {})
            }
        }
        settle()
        openPlates()
        repeat(2) { tap("live_exercise_1_disc_loader_plate_25_increment") }
        tap("live_exercise_1_disc_loader_plate_15_increment")

        tester.emulateSavedInstanceStateRestore()
        settle()

        rule.onNodeWithTag("live_exercise_1_disc_loader_sheet").assertExists()
        rule.onNodeWithTag("live_exercise_1_disc_loader_plate_25_value").assertTextEquals("2")
        rule.onNodeWithTag("live_exercise_1_disc_loader_plate_15_value").assertTextEquals("1")
        rule.onNodeWithTag("live_exercise_1_disc_loader_total").assertTextEquals("Total: 150 kg")
    }

    @Test
    fun aDraftSavedBeforeTheDiscLoader_restoresAsAnEmptyLoader() {
        val handle = SavedStateHandle()
        handle["live_calc_position"] = BENCH
        handle["live_calc_kind"] = "Plates"
        handle["live_calc_weight"] = "100"
        val vm = newViewModel(handle)
        rule.setContent {
            AppTheme {
                val state by vm.uiState.collectAsState()
                LiveSessionScreen(state, 0, vm::onEvent, {}, onOpenExercise = {})
            }
        }
        settle()

        rule.onNodeWithTag("live_exercise_1_disc_loader_total").assertTextEquals("Total: 20 kg")
    }

    @Test
    fun warmUpSheetAndItsInputs_surviveLeavingTheApp() {
        val handle = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                val vm = remember { newViewModel(handle) }
                val state by vm.uiState.collectAsState()
                LiveSessionScreen(state, 0, vm::onEvent, {}, onOpenExercise = {})
            }
        }
        settle()
        openWarmUp()
        type("live_exercise_1_warm_up_calc_weight_field", "90")

        tester.emulateSavedInstanceStateRestore()
        settle()

        rule.onNodeWithTag("live_exercise_1_warm_up_calc_weight_field").assertTextContains("90")
    }
}
