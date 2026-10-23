package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeExerciseOptionsRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private const val PULL_UPS = 0
private const val CURL = 1
private const val BENCH = 2

/** US-32: "Contar mancuerna doble" and "Actualizar peso corporal" from the exercise menu. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class LiveSessionVolumeOptionsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val clock = MutableClock()
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(
            SessionExercise("e0", PULL_UPS, null, listOf(doneBodyweight(10))),
            SessionExercise("e1", CURL, null, listOf(doneSet(1, 20.0, 10))),
            SessionExercise("e2", BENCH, null, listOf(doneSet(1, 60.0, 5))),
        ),
    )
    private val options = FakeExerciseOptionsRepository()
    private val weights = FakeWeightRepository(measurement("w1", "2026-10-10T08:00:00Z", MeasurementSource.Manual))
    private lateinit var viewModel: LiveSessionViewModel

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
            exerciseRow("e0", null, equipment = Equipment.Bodyweight, name = "Dominadas", custom = true)
                .copy(metricType = ExerciseMetricType.Bodyweight),
            exerciseRow("e1", null, equipment = Equipment.Dumbbell, name = "Curl con mancuernas", custom = true),
            exerciseRow("e2", null, equipment = Equipment.Barbell, name = "Press banca", custom = true),
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
            FakeTrainingPreferencesRepository(),
            weights,
            options,
            SaveBodyMeasurement(weights, clock, TimeZone.UTC),
            TimeZone.UTC,
            handle,
        ).also { viewModel = it }
    }

    private fun show() {
        val vm = newViewModel()
        rule.setContent {
            AppTheme {
                val state by vm.uiState.collectAsState()
                var optionSaveFailed by remember { mutableStateOf(false) }
                LaunchedEffect(vm) {
                    vm.effects.collect { if (it == LiveSessionEffect.OptionSaveFailed) optionSaveFailed = true }
                }
                LiveSessionScreen(
                    state,
                    0,
                    vm::onEvent,
                    {},
                    onOpenExercise = {},
                    optionSaveFailed = optionSaveFailed,
                )
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

    private fun openMenu(position: Int) = tap("live_exercise_${position}_menu")

    private fun finishAndShowSummary() {
        tap("live_menu_button")
        tap("live_menu_finish")
        tap("live_finish_sheet_confirm")
    }

    @Test
    fun doubleDumbbell_isOnlyOfferedForDumbbellExercises() {
        show()

        openMenu(CURL)
        rule.onNodeWithTag("live_exercise_1_menu_double_dumbbell").assertExists()
        rule.onNodeWithTag("live_exercise_1_menu_body_weight").assertDoesNotExist()
    }

    @Test
    fun menuOfABarbellExercise_hasNeitherVolumeOption() {
        show()

        openMenu(BENCH)
        rule.onNodeWithTag("live_exercise_2_menu_rest").assertExists()
        rule.onNodeWithTag("live_exercise_2_menu_double_dumbbell").assertDoesNotExist()
        rule.onNodeWithTag("live_exercise_2_menu_body_weight").assertDoesNotExist()
    }

    @Test
    fun doubleDumbbellOn_countsTwiceTheLoggedWeight() {
        show()

        openMenu(CURL)
        tap("live_exercise_1_menu_double_dumbbell")
        rule.onNodeWithTag("live_exercise_1_double_dumbbell_switch").assertIsOff()
        rule.onNodeWithTag("live_exercise_1_double_dumbbell_confirm").assertIsNotEnabled()
        tap("live_exercise_1_double_dumbbell_switch")
        rule.onNodeWithTag("live_exercise_1_double_dumbbell_switch").assertIsOn()
        assertEquals(emptyList<Pair<String, Boolean>>(), options.writes)
        tap("live_exercise_1_double_dumbbell_confirm")
        rule.onNodeWithTag("live_exercise_1_double_dumbbell_sheet").assertDoesNotExist()
        finishAndShowSummary()

        assertEquals(listOf("e1" to true), options.writes)
        rule.onNodeWithTag("summary_volume_1").assertTextContains("400 kg")
    }

    @Test
    fun doubleDumbbellOff_countsTheLoggedWeightOnce() {
        options.ids = setOf("e1")
        show()

        openMenu(CURL)
        tap("live_exercise_1_menu_double_dumbbell")
        rule.onNodeWithTag("live_exercise_1_double_dumbbell_switch").assertIsOn()
        tap("live_exercise_1_double_dumbbell_switch")
        tap("live_exercise_1_double_dumbbell_confirm")
        finishAndShowSummary()

        rule.onNodeWithTag("summary_volume_1").assertTextContains("200 kg")
    }

    @Test
    fun doubleDumbbell_aFailedSaveKeepsTheSheetOpenWithAMessage() {
        options.failWrite = true
        show()

        openMenu(CURL)
        tap("live_exercise_1_menu_double_dumbbell")
        tap("live_exercise_1_double_dumbbell_switch")
        tap("live_exercise_1_double_dumbbell_confirm")

        rule.onNodeWithTag("live_exercise_1_double_dumbbell_sheet").assertExists()
        rule.onNodeWithTag("live_exercise_1_double_dumbbell_switch").assertIsOn()
        rule.onNodeWithTag(
            "live_exercise_1_double_dumbbell_error",
        ).assertTextEquals("No se pudo guardar. Inténtalo de nuevo.")
    }

    @Test
    fun bodyWeight_isOfferedForBodyweightExercises_prefilledAndSavedForToday() {
        show()

        openMenu(PULL_UPS)
        rule.onNodeWithTag("live_exercise_0_menu_double_dumbbell").assertDoesNotExist()
        tap("live_exercise_0_menu_body_weight")
        rule.onNodeWithTag("live_exercise_0_body_weight_field").assertTextContains("74,0")
        tap("live_exercise_0_body_weight_confirm")
        rule.onNodeWithTag("live_exercise_0_body_weight_sheet").assertDoesNotExist()
        finishAndShowSummary()

        assertEquals(74.0, weights.saves.single().weightKg, 0.0)
        rule.onNodeWithTag("summary_volume_0").assertTextContains("740 kg")
    }

    @Test
    fun bodyWeight_aNewWeightIsUsedForTheVolume() {
        show()

        openMenu(PULL_UPS)
        tap("live_exercise_0_menu_body_weight")
        rule.onNodeWithTag("live_exercise_0_body_weight_field").performTextReplacement("75,5")
        tap("live_exercise_0_body_weight_confirm")
        finishAndShowSummary()

        assertEquals(75.5, weights.saves.single().weightKg, 0.0)
        rule.onNodeWithTag("summary_volume_0").assertTextContains("755 kg")
    }

    @Test
    fun bodyWeight_emptyFieldDisablesGuardar() {
        show()

        openMenu(PULL_UPS)
        tap("live_exercise_0_menu_body_weight")
        rule.onNodeWithTag("live_exercise_0_body_weight_field").performTextReplacement("")
        rule.onNodeWithTag("live_exercise_0_body_weight_confirm").assertIsNotEnabled()
        rule.onNodeWithTag("live_exercise_0_body_weight_field").performTextInput("80")
        rule.onNodeWithTag("live_exercise_0_body_weight_confirm").assertIsEnabled()
    }

    @Test
    fun bodyWeight_outOfRangeShowsAnErrorAndKeepsTheSheet() {
        show()

        openMenu(PULL_UPS)
        tap("live_exercise_0_menu_body_weight")
        rule.onNodeWithTag("live_exercise_0_body_weight_field").performTextReplacement("5")
        tap("live_exercise_0_body_weight_confirm")

        rule.onNodeWithTag("live_exercise_0_body_weight_error").assertTextEquals("Introduce un peso válido.")
        rule.onNodeWithTag("live_exercise_0_body_weight_sheet").assertExists()
        assertEquals(emptyList<Any>(), weights.saves)
    }

    @Test
    fun bodyWeight_withAHealthConnectWeightToday_isDisabledWithTheReason() {
        weights.rows += measurement("hc1", "2026-10-19T07:00:00Z", MeasurementSource.HealthConnect)
        show()

        openMenu(PULL_UPS)

        rule.onNodeWithTag("live_exercise_0_menu_body_weight").assertIsNotEnabled()
        rule.onNodeWithTag("live_exercise_0_body_weight_hc_note").assertTextEquals("Sincronizado desde Health Connect")
    }

    @Test
    fun doubleDumbbellSheet_andItsDraft_surviveLeavingTheApp() {
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
        openMenu(CURL)
        tap("live_exercise_1_menu_double_dumbbell")
        tap("live_exercise_1_double_dumbbell_switch")

        tester.emulateSavedInstanceStateRestore()
        settle()

        rule.onNodeWithTag("live_exercise_1_double_dumbbell_sheet").assertExists()
        rule.onNodeWithTag("live_exercise_1_double_dumbbell_switch").assertIsOn()
        assertEquals(emptyList<Pair<String, Boolean>>(), options.writes)
    }

    @Test
    fun bodyWeightSheet_andTheTypedWeight_surviveLeavingTheApp() {
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
        openMenu(PULL_UPS)
        tap("live_exercise_0_menu_body_weight")
        rule.onNodeWithTag("live_exercise_0_body_weight_field").performTextReplacement("75,5")

        tester.emulateSavedInstanceStateRestore()
        settle()

        rule.onNodeWithTag("live_exercise_0_body_weight_sheet").assertExists()
        rule.onNodeWithTag("live_exercise_0_body_weight_field").assertTextContains("75,5")
    }

    private fun measurement(id: String, at: String, source: MeasurementSource) =
        BodyMeasurement(id, Instant.parse(at), 74.0, source)
}

private fun doneBodyweight(reps: Int) = WorkoutSet(1, SetType.Working, SetEntry.Bodyweight(0.0, reps), SESSION_START)
