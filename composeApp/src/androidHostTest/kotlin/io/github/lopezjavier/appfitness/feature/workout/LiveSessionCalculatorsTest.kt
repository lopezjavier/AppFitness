package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
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
import io.github.lopezjavier.appfitness.app.FakeExerciseOptionsRepository
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
            FakeExerciseOptionsRepository(),
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
    fun warmUp_100kgWith20kgBar_showsTheFourDefaultRows() {
        show()

        openWarmUp()

        val tag = "live_exercise_1_warm_up_calc"
        rule.onNodeWithTag("${tag}_weight_field").assertTextContains("100")
        rule.onNodeWithTag("${tag}_bar_20").assertIsSelected()
        rule.onNodeWithTag("${tag}_row_0").assertTextContains("20 kg × 10")
        rule.onNodeWithTag("${tag}_row_1").assertTextContains("40 kg × 5")
        rule.onNodeWithTag("${tag}_row_2").assertTextContains("60 kg × 3")
        rule.onNodeWithTag("${tag}_row_3").assertTextContains("80 kg × 2")
        rule.onNodeWithTag("${tag}_row_4").assertDoesNotExist()
        rule.onNodeWithTag("${tag}_row_0").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun warmUp_rowsSpeakTheirFullText() {
        show()

        openWarmUp()

        val config = rule.onNodeWithTag("live_exercise_1_warm_up_calc_row_0").fetchSemanticsNode().config
        val description = config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
        kotlin.test.assertEquals(listOf("Calentamiento 1: 20 kilos, 10 repeticiones"), description)
    }

    @Test
    fun warmUp_addSets_putsCSetsBeforeTheWorkingSets() {
        show()
        openWarmUp()

        tap("live_exercise_1_warm_up_calc_confirm")

        rule.onNodeWithTag("live_exercise_1_warm_up_calc_sheet").assertDoesNotExist()
        rule.onAllNodesWithText("C").assertCountEquals(4)
    }

    @Test
    fun warmUp_emptyWeight_disablesTheButtonAndExplainsWhy() {
        show()
        openWarmUp()

        type("live_exercise_1_warm_up_calc_weight_field", "")

        rule.onNodeWithTag("live_exercise_1_warm_up_calc_confirm").assertIsNotEnabled()
        rule.onNodeWithTag("live_exercise_1_warm_up_calc_hint").assertTextEquals("Introduce el peso de trabajo")
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
    fun plates_100kgWith20kgBar_shows25Plus15PerSide() {
        show()

        openPlates()

        val tag = "live_exercise_1_plate_calc"
        rule.onNodeWithTag("${tag}_plates").assertTextEquals("25 + 15")
        rule.onNodeWithTag("${tag}_bar_label").assertTextEquals("Barra 20 kg")
        rule.onNodeWithTag("${tag}_note").assertDoesNotExist()
        rule.onNodeWithTag("${tag}_close").assertExists()
    }

    @Test
    fun plates_theResultFollowsTheTypedWeight() {
        show()
        openPlates()

        type("live_exercise_1_plate_calc_weight_field", "102,5")

        rule.onNodeWithTag("live_exercise_1_plate_calc_plates").assertTextEquals("25 + 15 + 1,25")
    }

    @Test
    fun plates_smallPlatesAreAvailable() {
        show()
        openPlates()

        type("live_exercise_1_plate_calc_weight_field", "21")
        rule.onNodeWithTag("live_exercise_1_plate_calc_plates").assertTextEquals("0,5")
        type("live_exercise_1_plate_calc_weight_field", "22")
        rule.onNodeWithTag("live_exercise_1_plate_calc_plates").assertTextEquals("1")
        type("live_exercise_1_plate_calc_weight_field", "24")
        rule.onNodeWithTag("live_exercise_1_plate_calc_plates").assertTextEquals("2")
    }

    @Test
    fun plates_the7_5kgBar_changesTheResult() {
        show()
        openPlates()

        tap("live_exercise_1_plate_calc_bar_7_5")

        rule.onNodeWithTag("live_exercise_1_plate_calc_bar_7_5").assertIsSelected()
        rule.onNodeWithTag("live_exercise_1_plate_calc_plates").assertTextEquals("25 + 20 + 1,25")
        rule.onNodeWithTag("live_exercise_1_plate_calc_bar_label").assertTextEquals("Barra 7,5 kg")
    }

    @Test
    fun plates_impossibleTarget_showsTheClosestLowerWeight() {
        preferences.result = Result.success(TrainingPreferences(plateKg = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5)))
        show()
        openPlates()

        type("live_exercise_1_plate_calc_weight_field", "101")

        rule.onNodeWithTag("live_exercise_1_plate_calc_note")
            .assertTextEquals("No se puede lograr 101 kg; el más cercano es 100 kg")
        rule.onNodeWithTag("live_exercise_1_plate_calc_plates").assertTextEquals("25 + 15")
    }

    @Test
    fun plates_targetAtTheBar_showsOnlyTheBar() {
        show()
        openPlates()

        type("live_exercise_1_plate_calc_weight_field", "20")

        rule.onNodeWithTag("live_exercise_1_plate_calc_plates").assertTextEquals("Solo la barra")
        rule.onNodeWithTag("live_exercise_1_plate_calc_note").assertDoesNotExist()
    }

    @Test
    fun plates_closeButtonClosesTheSheet() {
        show()
        openPlates()

        tap("live_exercise_1_plate_calc_close")

        rule.onNodeWithTag("live_exercise_1_plate_calc_sheet").assertDoesNotExist()
    }

    @Test
    fun plateCalculator_isOnlyOfferedForBarbellExercises() {
        show()

        tap("live_exercise_0_menu")
        rule.onNodeWithTag("live_exercise_0_menu_plate_calc").assertDoesNotExist()
        rule.onNodeWithTag("live_exercise_0_menu_warm_up_calc").assertExists()
    }

    @Test
    fun savedPlates_reachTheCalculator() {
        preferences.result = Result.success(TrainingPreferences(plateKg = listOf(20.0, 10.0), barWeightKg = 15.0))
        show()

        openPlates()

        rule.onNodeWithTag("live_exercise_1_plate_calc_bar_15").assertIsSelected()
        rule.onNodeWithTag("live_exercise_1_plate_calc_plates").assertTextEquals("20 + 20")
        rule.onNodeWithTag("live_exercise_1_plate_calc_note").assertExists()
    }

    @Test
    fun sheetsAndTheirInputs_surviveLeavingTheApp() {
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
        type("live_exercise_1_plate_calc_weight_field", "102,5")
        tap("live_exercise_1_plate_calc_bar_15")

        tester.emulateSavedInstanceStateRestore()
        settle()

        rule.onNodeWithTag("live_exercise_1_plate_calc_sheet").assertExists()
        rule.onNodeWithTag("live_exercise_1_plate_calc_weight_field").assertTextContains("102,5")
        rule.onNodeWithTag("live_exercise_1_plate_calc_bar_15").assertIsSelected()
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
        tap("live_exercise_1_warm_up_calc_bar_10")

        tester.emulateSavedInstanceStateRestore()
        settle()

        rule.onNodeWithTag("live_exercise_1_warm_up_calc_weight_field").assertTextContains("90")
        rule.onNodeWithTag("live_exercise_1_warm_up_calc_bar_10").assertIsSelected()
    }
}
