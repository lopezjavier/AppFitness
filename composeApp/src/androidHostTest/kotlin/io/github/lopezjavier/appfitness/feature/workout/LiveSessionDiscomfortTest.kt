package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeDiscomfortRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.rules.StrongPainTerms
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.days

/** US-67: logging, showing and resolving the discomfort of "Press banca" in the live session. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class LiveSessionDiscomfortTest {
    @get:Rule
    val rule = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val clock = MutableClock()
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(SessionExercise("e1", 0, null, listOf(pendingSet(1)))),
    )
    private val connectivity = FakeConnectivityObserver()
    private val discomforts = FakeDiscomfortRepository(now = { clock.current })
    private val weights = FakeWeightRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        repository.history = mapOf("e1" to emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun show() {
        val vm = LiveSessionViewModel(
            repository,
            FakeExerciseRepository(listOf(exerciseRow("e1", null, name = "Press banca", custom = true))),
            FakeExerciseLibrary(),
            connectivity,
            clock,
            FakeHaptics(),
            FakeRoutineRepository(),
            FakeAlertSound(),
            FakeTrainingPreferencesRepository(),
            weights,
            SaveBodyMeasurement(weights, clock, TimeZone.UTC),
            TimeZone.UTC,
            SavedStateHandle(),
            discomfortRepository = discomforts,
        )
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

    private fun openSheet() {
        tap("live_exercise_0_menu")
        tap("live_exercise_0_menu_discomfort")
    }

    private fun log(note: String) {
        openSheet()
        if (note.isNotEmpty()) rule.onNodeWithTag("live_exercise_0_discomfort_note").performTextInput(note)
        tap("live_exercise_0_discomfort_sheet_commit")
    }

    private fun stored(ageDays: Int) = Discomfort("old", "e1", clock.current - ageDays.days, null, severe = false)

    @Test
    fun logging_showsMolestiaRegistradaOnTheExercise() {
        show()
        rule.onNodeWithTag("live_exercise_0_discomfort").assertDoesNotExist()

        log("rodilla")

        rule.onNodeWithTag("live_exercise_0_discomfort").assertTextContains("Molestia registrada")
        rule.onNodeWithTag("live_exercise_0_discomfort_severe").assertDoesNotExist()
    }

    @Test
    fun loggingWithoutANote_isAllowed() {
        show()
        openSheet()

        rule.onNodeWithTag("live_exercise_0_discomfort_sheet_commit").assertIsEnabled()
        tap("live_exercise_0_discomfort_sheet_commit")

        rule.onNodeWithTag("live_exercise_0_discomfort").assertExists()
    }

    @Test
    fun strongPainNote_showsTheFixedMessageAndStoresSevere() {
        show()

        log("dolor fuerte")

        rule.onNodeWithTag("live_exercise_0_discomfort_severe").assertTextContains(StrongPainTerms.MESSAGE)
        assertTrue(discomforts.rows.single().severe)
    }

    @Test
    fun plainDolor_showsNoMessage() {
        show()

        log("dolor")

        rule.onNodeWithTag("live_exercise_0_discomfort").assertExists()
        rule.onNodeWithTag("live_exercise_0_discomfort_severe").assertDoesNotExist()
    }

    @Test
    fun resolving_removesTheLabel() {
        discomforts.rows = listOf(stored(ageDays = 1))
        show()
        rule.onNodeWithTag("live_exercise_0_discomfort").assertExists()

        tap("live_exercise_0_discomfort_resolve")

        rule.onNodeWithTag("live_exercise_0_discomfort").assertDoesNotExist()
        assertEquals(clock.current, discomforts.rows.single().resolvedAt)
    }

    @Test
    fun anEightDayOldDiscomfort_isNotShown() {
        discomforts.rows = listOf(stored(ageDays = 8))
        show()

        rule.onNodeWithTag("live_exercise_0_discomfort").assertDoesNotExist()
    }

    @Test
    fun aSixDayOldDiscomfort_isShown() {
        discomforts.rows = listOf(stored(ageDays = 6))
        show()

        rule.onNodeWithTag("live_exercise_0_discomfort").assertExists()
    }

    @Test
    fun noteOver200Characters_showsTheLimitAndDisablesGuardar() {
        show()
        openSheet()

        rule.onNodeWithTag("live_exercise_0_discomfort_note").performTextInput("a".repeat(201))
        settle()

        rule.onNodeWithTag("live_exercise_0_discomfort_note_error").assertTextContains("Máximo 200 caracteres")
        rule.onNodeWithTag("live_exercise_0_discomfort_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun noteOf200Characters_canBeSaved() {
        show()
        openSheet()

        rule.onNodeWithTag("live_exercise_0_discomfort_note").performTextInput("a".repeat(200))
        settle()

        rule.onNodeWithTag("live_exercise_0_discomfort_note_error").assertDoesNotExist()
        rule.onNodeWithTag("live_exercise_0_discomfort_sheet_commit").assertIsEnabled()
    }

    @Test
    fun offline_theMenuActionIsDisabled() {
        connectivity.state.value = false
        show()

        tap("live_exercise_0_menu")

        rule.onNodeWithTag("live_exercise_0_menu_discomfort").assertIsNotEnabled()
    }

    @Test
    fun offline_resolveIsDisabledWithItsHint() {
        discomforts.rows = listOf(stored(ageDays = 1))
        connectivity.state.value = false
        show()

        rule.onNodeWithTag("live_exercise_0_discomfort_resolve").assertIsNotEnabled()
        rule.onNodeWithTag("live_exercise_0_discomfort_resolve_hint")
            .assertTextContains("Sin conexión: no se puede actualizar la molestia")
    }
}
