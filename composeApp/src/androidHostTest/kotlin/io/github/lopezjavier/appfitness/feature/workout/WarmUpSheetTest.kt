package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeExerciseOptionsRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
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

private const val TAG = "live_exercise_0_warm_up_calc"

/** US-33 (ADR-56): the warm-up sheet asks for "Peso objetivo (kg)", prefilled, with the Preferencias bar. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@OptIn(ExperimentalCoroutinesApi::class)
class WarmUpSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val clock = MutableClock()
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(SessionExercise("e1", 0, null, listOf(doneSet(1, 100.0, 5), pendingSet(2)))),
    )
    private val weights = FakeWeightRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun show() {
        val vm = LiveSessionViewModel(
            repository,
            FakeExerciseRepository(listOf(exerciseRow("e1", null, equipment = Equipment.Barbell, name = "Banca"))),
            FakeExerciseLibrary(),
            FakeConnectivityObserver(),
            clock,
            FakeHaptics(),
            FakeRoutineRepository(),
            FakeAlertSound(),
            FakeTrainingPreferencesRepository(),
            weights,
            FakeExerciseOptionsRepository(),
            SaveBodyMeasurement(weights, clock, TimeZone.UTC),
            TimeZone.UTC,
        )
        rule.setContent {
            AppTheme {
                val state by vm.uiState.collectAsState()
                LiveSessionScreen(state, 0, vm::onEvent, {}, onOpenExercise = {})
            }
        }
        settle()
        tap("live_exercise_0_menu")
        tap("live_exercise_0_menu_warm_up_calc")
    }

    private fun settle() {
        scheduler.runCurrent()
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        settle()
    }

    @Test
    fun theWeightField_isLabelledPesoObjetivo() {
        show()

        rule.onNodeWithText("PESO OBJETIVO (KG)", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theWeightField_isPrefilledWithTheBestPastWeight() {
        repository.sessionRecords =
            mapOf("e1" to listOf(ExerciseSessionRecords("old", SESSION_START, heaviestLift = 110.0)))
        show()

        rule.onNodeWithTag("${TAG}_weight_field").assertTextContains("110")
    }

    @Test
    fun withoutHistory_theWeightFallsBackToThePlannedWeight() {
        show()

        rule.onNodeWithTag("${TAG}_weight_field").assertTextContains("100")
    }

    @Test
    fun theBarComesFromPreferencias_andHasNoSelector() {
        show()

        rule.onNodeWithTag("${TAG}_bar_label").assertTextEquals("Barra 20 kg")
        rule.onNodeWithTag("${TAG}_bar").assertDoesNotExist()
        rule.onNodeWithTag("${TAG}_bar_20").assertDoesNotExist()
    }

    @Test
    fun hundredKgWithATwentyKgBar_showsTheDefaultSchemeAndAddSetsInsertsThem() {
        show()
        rule.onNodeWithTag("${TAG}_weight_field").performTextReplacement("100")
        settle()
        tap("warmup_sets_stepper_increment")
        tap("warmup_sets_stepper_increment")
        tap("warmup_sets_stepper_increment")

        rule.onNodeWithTag("${TAG}_row_0").assertTextContains("20 kg × 10")
        rule.onNodeWithTag("${TAG}_row_1").assertTextContains("40 kg × 5")
        rule.onNodeWithTag("${TAG}_row_2").assertTextContains("60 kg × 3")
        rule.onNodeWithTag("${TAG}_row_3").assertTextContains("80 kg × 2")
        tap("${TAG}_confirm")
        rule.onNodeWithTag("${TAG}_sheet").assertDoesNotExist()
        rule.onAllNodesWithText("C").assertCountEquals(4)
    }
}
