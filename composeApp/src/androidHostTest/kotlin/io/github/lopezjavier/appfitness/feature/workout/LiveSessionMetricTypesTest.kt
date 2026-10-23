package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeExerciseOptionsRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SetEntry
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
import kotlin.time.Duration.Companion.seconds

private const val BODYWEIGHT = 0
private const val PLANK = 1
private const val TREADMILL = 2
private const val BENCH = 3

/** US-31: the live set row follows the exercise's metric type; the stopwatch counts with the clock. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class LiveSessionMetricTypesTest {
    @get:Rule
    val rule = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val clock = MutableClock()
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(
            SessionExercise("e0", BODYWEIGHT, null, listOf(pendingSet(1))),
            SessionExercise("e1", PLANK, null, listOf(pendingSet(1))),
            SessionExercise("e2", TREADMILL, null, listOf(pendingSet(1))),
            SessionExercise("e3", BENCH, null, listOf(pendingSet(1))),
        ),
    )
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
            exerciseRow("e1", null, equipment = Equipment.Bodyweight, name = "Plancha", custom = true)
                .copy(metricType = ExerciseMetricType.Duration),
            exerciseRow("e2", null, equipment = Equipment.Machine, name = "Cinta de correr", custom = true)
                .copy(metricType = ExerciseMetricType.DistanceDuration),
            exerciseRow("e3", null, equipment = Equipment.Barbell, name = "Press banca", custom = true),
        )
        val weights = FakeWeightRepository()
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
            FakeExerciseOptionsRepository(),
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
                LiveSessionScreen(state, 0, vm::onEvent, {}, onOpenExercise = {})
            }
        }
        settle()
    }

    private fun settle() {
        scheduler.runCurrent()
        rule.waitForIdle()
    }

    private fun passTime(seconds: Int) {
        repeat(seconds) {
            clock.advance(1.seconds)
            scheduler.advanceTimeBy(1_000)
            scheduler.runCurrent()
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        settle()
    }

    private fun lastEntry(position: Int) = repository.upserts.last { it.exercisePosition == position }.entry

    @Test
    fun bodyweightExercise_showsRepsAndAnAddedWeightField_andSavesBoth() {
        show()

        rule.onNodeWithTag("live_set_0_1_reps").assertExists()
        rule.onNodeWithTag("live_set_0_1_added_weight").assertExists()
        rule.onNodeWithTag("live_set_0_1_weight").assertDoesNotExist()
        rule.onNodeWithTag("live_set_0_1_added_weight").performTextInput("10")
        rule.onNodeWithTag("live_set_0_1_reps").performTextInput("8")
        tap("live_set_0_1_check")

        assertEquals(SetEntry.Bodyweight(10.0, 8), lastEntry(BODYWEIGHT))
    }

    @Test
    fun durationExercise_showsAStopwatchInsteadOfWeightAndReps() {
        show()

        rule.onNodeWithTag("live_set_1_1_stopwatch_toggle").assertExists()
        rule.onNodeWithTag("live_set_1_1_stopwatch_time").assertTextEquals("00:00")
        rule.onNodeWithTag("live_set_1_1_weight").assertDoesNotExist()
        rule.onNodeWithTag("live_set_1_1_reps").assertDoesNotExist()
    }

    @Test
    fun durationExercise_stopwatchCountsWithTheClock_andShows0130() {
        show()

        tap("live_set_1_1_stopwatch_toggle")
        passTime(90)

        rule.onNodeWithTag("live_set_1_1_stopwatch_time").assertTextEquals("01:30")
        tap("live_set_1_1_stopwatch_toggle")
        passTime(30)
        rule.onNodeWithTag("live_set_1_1_stopwatch_time").assertTextEquals("01:30")
        assertEquals(emptyList<Any>(), repository.upserts)

        tap("live_set_1_1_check")

        assertEquals(SetEntry.Duration(90), lastEntry(PLANK))
    }

    @Test
    fun durationExercise_aTimeTypedAsMmSs_isUsed() {
        show()

        rule.onNodeWithTag("live_set_1_1_stopwatch_time").performClick()
        rule.onNodeWithTag("live_set_1_1_stopwatch_input").performTextInput("1:30")
        rule.onNodeWithTag("live_set_1_1_stopwatch_input").performImeAction()
        settle()

        rule.onNodeWithTag("live_set_1_1_stopwatch_time").assertTextEquals("01:30")
        tap("live_set_1_1_check")
        assertEquals(SetEntry.Duration(90), lastEntry(PLANK))
    }

    @Test
    fun durationExercise_ghostShowsThePreviousTime() {
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 0.0, 0, durationS = 90)))
        show()

        rule.onNodeWithTag("live_set_1_1_previous").assertTextEquals("01:30")
    }

    @Test
    fun distanceExercise_showsDistanceAndDuration_andSavesMetresAndSeconds() {
        show()

        rule.onNodeWithTag("live_set_2_1_distance").assertExists()
        rule.onNodeWithTag("live_set_2_1_stopwatch_time").assertExists()
        rule.onNodeWithTag("live_set_2_1_distance").performTextInput("5")
        rule.onNodeWithTag("live_set_2_1_stopwatch_time").performClick()
        rule.onNodeWithTag("live_set_2_1_stopwatch_input").performTextInput("30:00")
        rule.onNodeWithTag("live_set_2_1_stopwatch_input").performImeAction()
        settle()
        tap("live_set_2_1_check")

        assertEquals(SetEntry.DistanceDuration(5000.0, 1800), lastEntry(TREADMILL))
    }

    @Test
    fun distanceExercise_ghostShowsKilometresAndTime() {
        repository.previous = mapOf("e2" to listOf(PreviousSet(1, 0.0, 0, durationS = 1800, distanceM = 5000.0)))
        show()

        rule.onNodeWithTag("live_set_2_1_previous").assertTextEquals("5,0 km · 30:00")
    }

    @Test
    fun barbellExercise_weightFieldHintsBarraYDiscos() {
        show()

        rule.onNodeWithTag("live_set_3_1_weight").assertContentDescriptionContains("barra + discos", substring = true)
    }

    @Test
    fun bodyweightExercise_addedLoadFieldHintsCargaExtra() {
        show()

        rule.onNodeWithTag("live_set_0_1_added_weight")
            .assertContentDescriptionContains("carga extra", substring = true)
    }

    @Test
    fun anIncompleteSet_isRefusedWithAMessage() {
        show()

        tap("live_set_1_1_check")

        rule.onNodeWithTag("live_set_1_1_invalid").assertTextEquals("Completa los datos de la serie")
        assertEquals(emptyList<Any>(), repository.upserts)
    }

    @Test
    fun runningStopwatchAndHalfTypedDistance_surviveLeavingTheApp_andCountTheTimeAway() {
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
        tap("live_set_1_1_stopwatch_toggle")
        rule.onNodeWithTag("live_set_2_1_distance").performTextInput("3,2")
        passTime(30)

        tester.emulateSavedInstanceStateRestore()
        settle()
        passTime(60)

        rule.onNodeWithTag("live_set_1_1_stopwatch_time").assertTextEquals("01:30")
        rule.onNodeWithTag("live_set_2_1_distance").assertTextContains("3,2")
    }
}
