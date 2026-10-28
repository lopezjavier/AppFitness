package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppMessages
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/**
 * US-64 scenarios at the screen: the rest alert outside the app is a scheduled alarm (the platform part is covered by
 * the Robolectric platform tests), so these assert what the screen and view model schedule, cancel, play and say.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One test per Gherkin scenario plus the helpers.
class RestAlertScenariosTest {
    @get:Rule
    val rule = createComposeRule()

    private val testScheduler = TestCoroutineScheduler()
    private val clock = MutableClock()
    private val alertSound = FakeAlertSound()
    private val alarm = FakeRestAlertScheduler()
    private val permission = FakeNotificationPermission(granted = true)
    private val preferences = FakeRestAlertPreferences(explained = false)
    private val messages = AppMessages(CoroutineScope(Job()))
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(SessionExercise("e1", 0, null, listOf(pendingSet(1), pendingSet(2)))),
    )
    private lateinit var viewModel: LiveSessionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val routine = Routine(
            id = "r1",
            name = "Torso",
            notes = null,
            archived = false,
            exercises = listOf(RoutineExercise("e1", 0, null, List(2) { PlannedSet(null, 8) }, REST_SECONDS)),
        )
        val weights = FakeWeightRepository()
        viewModel = LiveSessionViewModel(
            repository,
            FakeExerciseRepository(listOf(exerciseRow("e1", "ke1"))),
            FakeExerciseLibrary(mapOf("ke1" to exerciseInfo("ke1", "Ejercicio e1"))),
            FakeConnectivityObserver(),
            clock,
            FakeHaptics(),
            FakeRoutineRepository(listOf(routine)),
            alertSound,
            FakeTrainingPreferencesRepository(),
            weights,
            SaveBodyMeasurement(weights, clock, TimeZone.UTC),
            TimeZone.UTC,
            restAlertScheduler = alarm,
            notificationPermission = permission,
            restAlertPreferences = preferences,
        )
    }

    @After
    fun tearDown() {
        viewModel.onEvent(LiveSessionEvent.RestSkip)
        Dispatchers.resetMain()
    }

    private fun settle() {
        testScheduler.runCurrent()
        rule.waitForIdle()
    }

    private fun passTime(seconds: Int) {
        repeat(seconds) {
            clock.advance(1.seconds)
            testScheduler.advanceTimeBy(1_000)
            testScheduler.runCurrent()
        }
        rule.waitForIdle()
    }

    private fun show() {
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppMessages provides messages) {
                    LiveSessionRoot(
                        selectedExerciseId = null,
                        onConsumeSelection = {},
                        onAddExercise = {},
                        onOpenExercise = {},
                        onClose = {},
                        viewModel = viewModel,
                    )
                }
            }
        }
        settle()
    }

    private fun completeFirstSet() {
        rule.onNodeWithTag("live_set_0_1_weight").performTextInput("40")
        rule.onNodeWithTag("live_set_0_1_reps").performTextInput("10")
        rule.onNodeWithTag("live_set_0_1_check").performClick()
        settle()
        rule.onNodeWithTag("rpe_picker_option_8").performClick()
        settle()
    }

    @Test
    fun screenOff_whenTheRestEnds_theAlarmIsScheduledForTheEndAndNoInAppAlertPlays() {
        show()
        val started = clock.now()
        completeFirstSet()

        viewModel.onEvent(LiveSessionEvent.Pause)
        passTime(REST_SECONDS + 1)

        assertEquals(started + REST_SECONDS.seconds, alarm.scheduled)
        assertEquals(0, alertSound.plays)
    }

    @Test
    fun appInTheBackground_theAlarmStaysScheduledAcrossPauseAndResume() {
        show()
        completeFirstSet()
        val scheduled = alarm.scheduled

        viewModel.onEvent(LiveSessionEvent.Pause)
        passTime(10)
        viewModel.onEvent(LiveSessionEvent.Resume)
        settle()

        assertNotNull(scheduled)
        assertEquals(scheduled, alarm.scheduled)
    }

    @Test
    fun appInTheForeground_theInAppAlertPlaysAndNothingIsLeftScheduled() {
        show()
        completeFirstSet()
        assertNotNull(alarm.scheduled)

        passTime(REST_SECONDS + 1)

        assertEquals(1, alertSound.plays)
        assertNull(alarm.scheduled)
    }

    @Test
    fun skippingTheRest_cancelsTheAlarm() {
        show()
        completeFirstSet()

        rule.onNodeWithTag("live_rest_skip").performClick()
        settle()

        assertNull(alarm.scheduled)
        assertEquals(0, alertSound.plays)
    }

    @Test
    fun finishingTheSession_cancelsTheAlarm() {
        show()
        completeFirstSet()

        viewModel.onEvent(LiveSessionEvent.RequestFinish)
        viewModel.onEvent(LiveSessionEvent.ConfirmFinish)
        settle()

        assertNull(alarm.scheduled)
    }

    @Test
    fun notificationsDenied_theRestStartsAndTheUserIsToldOnceInSpanish() {
        permission.granted = false
        show()

        completeFirstSet()

        rule.onNodeWithTag("live_rest_countdown").assertExists()
        assertEquals(
            "Para avisarte al acabar el descanso con la pantalla apagada, " +
                "activa las notificaciones de AppFitness en Ajustes.",
            messages.current.value?.text,
        )
        assertEquals(1, preferences.explanationMarks)
    }

    private companion object {
        const val REST_SECONDS = 60
    }
}
