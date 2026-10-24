package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeExerciseOptionsRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
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
import kotlin.time.Duration.Companion.seconds

/** US-30: the very first set of the first exercise asks for the RPE like every other working set (ADR-56). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@OptIn(ExperimentalCoroutinesApi::class)
class LiveSessionFirstSetRpeTest {
    @get:Rule
    val rule = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val clock = MutableClock()
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(SessionExercise("e1", 0, null, listOf(pendingSet(1), pendingSet(2)))),
    )
    private lateinit var viewModel: LiveSessionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        viewModel = newViewModel()
    }

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()): LiveSessionViewModel {
        val routine = Routine(
            id = "r1",
            name = "Torso",
            notes = null,
            archived = false,
            exercises = listOf(RoutineExercise("e1", 0, null, List(2) { PlannedSet(null, 8) }, 60)),
        )
        val weights = FakeWeightRepository()
        return LiveSessionViewModel(
            repository,
            FakeExerciseRepository(listOf(exerciseRow("e1", "ke1"))),
            FakeExerciseLibrary(mapOf("ke1" to exerciseInfo("ke1", "Press banca"))),
            FakeConnectivityObserver(),
            clock,
            FakeHaptics(),
            FakeRoutineRepository(listOf(routine)),
            FakeAlertSound(),
            FakeTrainingPreferencesRepository(),
            weights,
            FakeExerciseOptionsRepository(),
            SaveBodyMeasurement(weights, clock, TimeZone.UTC),
            TimeZone.UTC,
            handle,
        )
    }

    @After
    fun tearDown() {
        viewModel.onEvent(LiveSessionEvent.RestSkip)
        Dispatchers.resetMain()
    }

    private fun settle() {
        scheduler.runCurrent()
        rule.waitForIdle()
    }

    @Test
    fun firstSetOfFirstExercise_checkedWithoutRpe_opensThePickerAndPickingCompletesIt() {
        rule.setContent {
            AppTheme {
                val state by viewModel.uiState.collectAsState()
                LiveSessionScreen(state, 0, viewModel::onEvent, {}, onOpenExercise = {})
            }
        }
        settle()
        rule.onNodeWithTag("live_set_0_1_weight").performTextInput("60")
        rule.onNodeWithTag("live_set_0_1_reps").performTextInput("8")

        rule.onNodeWithTag("live_set_0_1_check").performClick()
        settle()

        rule.onNodeWithTag("rpe_picker").assertExists()
        rule.onNodeWithTag("live_set_0_1_check").assertIsOff()
        rule.onNodeWithTag("rpe_picker_option_8").performClick()
        settle()
        rule.onNodeWithTag("rpe_picker").assertDoesNotExist()
        rule.onNodeWithTag("live_set_0_1_check").assertIsOn()
    }

    @Test
    fun firstSetWithTheGhostFilledByATap_checkedWithoutRpe_opensThePicker() {
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 80.0, 10)))
        setContent()
        rule.onNodeWithTag("live_set_0_1_previous").performClick()
        settle()

        rule.onNodeWithTag("live_set_0_1_check").performClick()
        settle()

        rule.onNodeWithTag("rpe_picker").assertExists()
        rule.onNodeWithTag("live_set_0_1_check").assertIsOff()
    }

    @Test
    fun firstSetLeftBlank_checkedWithPlaceholders_opensThePicker() {
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 80.0, 10)))
        setContent()

        rule.onNodeWithTag("live_set_0_1_check").performClick()
        settle()

        rule.onNodeWithTag("rpe_picker").assertExists()
        rule.onNodeWithTag("live_set_0_1_check").assertIsOff()
    }

    @Test
    fun pausingFreezesTheRestChip_andCheckingASetWhilePausedResumesBoth() {
        setContent()
        rule.onNodeWithTag("live_set_0_1_weight").performTextInput("60")
        rule.onNodeWithTag("live_set_0_1_reps").performTextInput("8")
        rule.onNodeWithTag("live_set_0_1_check").performClick()
        settle()
        rule.onNodeWithTag("rpe_picker_option_8").performClick()
        settle()
        rule.onNodeWithTag("live_rest_countdown").assertTextContains("01:00")

        rule.onNodeWithTag("live_pause_toggle").performClick()
        settle()
        clock.advance(30.seconds)
        scheduler.advanceTimeBy(30_000)
        settle()

        rule.onNodeWithTag("live_rest_timer_paused").assertTextEquals("En pausa")
        rule.onNodeWithTag("live_rest_countdown").assertTextContains("01:00")
        rule.onNodeWithTag("live_pause_label").assertExists()

        rule.onNodeWithTag("live_set_0_2_weight").performTextInput("60")
        rule.onNodeWithTag("live_set_0_2_reps").performTextInput("8")
        rule.onNodeWithTag("live_set_0_2_check").performClick()
        settle()
        rule.onNodeWithTag("rpe_picker_option_8").performClick()
        settle()

        rule.onNodeWithTag("live_pause_label").assertDoesNotExist()
        rule.onNodeWithTag("live_rest_timer_paused").assertDoesNotExist()
    }

    @Test
    fun pausedRestCountdown_survivesLeavingTheApp() {
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
        rule.onNodeWithTag("live_set_0_1_weight").performTextInput("60")
        rule.onNodeWithTag("live_set_0_1_reps").performTextInput("8")
        rule.onNodeWithTag("live_set_0_1_check").performClick()
        settle()
        rule.onNodeWithTag("rpe_picker_option_8").performClick()
        settle()
        rule.onNodeWithTag("live_pause_toggle").performClick()
        settle()

        tester.emulateSavedInstanceStateRestore()
        settle()

        rule.onNodeWithTag("live_rest_timer_paused").assertExists()
        rule.onNodeWithTag("live_rest_countdown").assertTextContains("01:00")
    }

    private fun setContent() {
        rule.setContent {
            AppTheme {
                val state by viewModel.uiState.collectAsState()
                LiveSessionScreen(state, 0, viewModel::onEvent, {}, onOpenExercise = {})
            }
        }
        settle()
    }
}
