package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/**
 * Epic DoD for ghost data and the rest timer: a routine of four exercises with one superset is trained from the
 * screen with a test clock. Rests: bench 60 s; A1 0 s (no rest between the exercises); A2 90 s; curl 30 s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@OptIn(ExperimentalCoroutinesApi::class)
class FullSessionTest {
    @get:Rule
    val rule = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val clock = MutableClock()
    private val haptics = FakeHaptics()
    private val alertSound = FakeAlertSound()
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1), pendingSet(2))),
            SessionExercise("e2", 1, 1, listOf(pendingSet(1))),
            SessionExercise("e3", 2, 1, listOf(pendingSet(1))),
            SessionExercise("e4", 3, null, listOf(pendingSet(1))),
        ),
    ).apply { previous = mapOf("e1" to listOf(PreviousSet(1, 80.0, 10), PreviousSet(2, 85.0, 8))) }
    private lateinit var viewModel: LiveSessionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val routine = Routine(
            id = "r1",
            name = "Torso",
            notes = null,
            archived = false,
            exercises = listOf(
                RoutineExercise("e1", 0, null, List(2) { PlannedSet(null, 8) }, 60),
                RoutineExercise("e2", 1, 1, List(1) { PlannedSet(null, 8) }, 0),
                RoutineExercise("e3", 2, 1, List(1) { PlannedSet(null, 8) }, 90),
                RoutineExercise("e4", 3, null, List(1) { PlannedSet(null, 8) }, 30),
            ),
        )
        viewModel = LiveSessionViewModel(
            repository,
            FakeExerciseRepository(listOf("e1", "e2", "e3", "e4").map { exerciseRow(it, "k$it") }),
            FakeExerciseLibrary(
                listOf("e1", "e2", "e3", "e4").associate {
                    "k$it" to
                        exerciseInfo("k$it", "Ejercicio $it")
                },
            ),
            FakeConnectivityObserver(),
            clock,
            haptics,
            FakeRoutineRepository(listOf(routine)),
            alertSound,
            FakeTrainingPreferencesRepository(),
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

    private fun passTime(seconds: Int) {
        repeat(seconds) {
            clock.advance(1.seconds)
            scheduler.advanceTimeBy(1_000)
            scheduler.runCurrent()
        }
        rule.waitForIdle()
    }

    private fun complete(position: Int, index: Int, weight: String, reps: String) {
        rule.onNodeWithTag("live_set_${position}_${index}_weight").performTextInput(weight)
        rule.onNodeWithTag("live_set_${position}_${index}_reps").performTextInput(reps)
        rule.onNodeWithTag("live_set_${position}_${index}_check").performClick()
        settle()
    }

    @Test
    fun trainingTheWholeRoutine_showsTheGhostAndRunsEachRest() {
        rule.setContent {
            AppTheme {
                val state by viewModel.uiState.collectAsState()
                LiveSessionScreen(state, 0, viewModel::onEvent, {}, onOpenExercise = {})
            }
        }
        settle()

        // Ghost: last time's sets, and a tap fills the drafts.
        rule.onNodeWithTag("live_set_0_1_previous").assertTextContains("80 kg × 10")
        rule.onNodeWithTag("live_set_0_2_previous").assertTextContains("85 kg × 8")
        rule.onNodeWithTag("live_set_1_1_previous").assertTextContains("—")
        rule.onNodeWithTag("live_set_0_1_previous").performClick()
        settle()
        rule.onNodeWithTag("live_set_0_1_check").performClick()
        settle()

        // Bench: 60 s rest, ticking down from the clock, then the alert fires once and the bar goes away.
        rule.onNodeWithTag("live_rest_countdown").assertTextContains("01:00")
        passTime(20)
        rule.onNodeWithTag("live_rest_countdown").assertTextContains("00:40")
        rule.onNodeWithTag("live_rest_add").performClick()
        settle()
        rule.onNodeWithTag("live_rest_countdown").assertTextContains("01:10")
        passTime(70)
        rule.onNodeWithTag("live_rest_countdown").assertDoesNotExist()
        assertEquals(1, alertSound.plays)
        assertEquals(1, haptics.alerts)

        // Superset: A1 has no rest between the exercises, A2 starts the between-rounds rest.
        complete(1, 1, "40", "10")
        rule.onNodeWithTag("live_rest_countdown").assertDoesNotExist()
        complete(2, 1, "50", "12")
        rule.onNodeWithTag("live_rest_countdown").assertTextContains("01:30")
        rule.onNodeWithTag("live_rest_skip").performClick()
        settle()
        rule.onNodeWithTag("live_rest_countdown").assertDoesNotExist()
        assertEquals(1, alertSound.plays)

        // The last exercise restarts the timer with its own rest.
        complete(3, 1, "20", "12")
        rule.onNodeWithTag("live_rest_countdown").assertTextContains("00:30")
        assertEquals(4, repository.upserts.count { it.completedAt != null })
    }
}
