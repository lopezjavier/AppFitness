package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppMessages
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HapticEvent
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.RecordingAppHaptics
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-56: results of the workout detail go through the app messages with one haptic (R-28, R-29). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class SessionDetailRootTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeWorkoutSessionRepository().apply { completed += completedSession("s1") }
    private val routines = FakeRoutineRepository()
    private val haptics = RecordingAppHaptics()
    private val messages = AppMessages(CoroutineScope(Job()))
    private var closed = 0
    private var opened = mutableListOf<String>()

    private fun show() {
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppHaptics provides haptics, LocalAppMessages provides messages) {
                    val model = remember {
                        SessionDetailViewModel(
                            "s1",
                            repository,
                            FakeExerciseRepository(listOf(exerciseRow("e1", "bench"))),
                            FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press banca"))),
                            FakeConnectivityObserver(),
                            MutableClock(),
                            FakeTrainingPreferencesRepository(),
                            FakeWeightRepository(),
                            routines,
                        )
                    }
                    SessionDetailRoot("s1", { closed++ }, { opened += it }, viewModel = model)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun openSaveAsRoutineAndConfirm() {
        rule.onNodeWithTag("session_detail_more").performClick()
        rule.onNodeWithText("Guardar como rutina").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("save_as_routine_sheet_commit").performClick()
        rule.waitForIdle()
    }

    @Test
    fun savingAsRoutine_showsRutinaGuardada_withOneConfirm() {
        show()

        openSaveAsRoutineAndConfirm()

        assertEquals("Rutina guardada", messages.current.value?.text)
        haptics.assertSingle(HapticEvent.Confirm)
        assertEquals(1, routines.createdFromSession.size)
        rule.onNodeWithTag("save_as_routine_sheet").assertDoesNotExist()
    }

    @Test
    fun aFailedSaveAsRoutine_showsTheMessage_withOneReject_andKeepsTheSheetOpen() {
        routines.createFromSessionFailure = AppError.Unknown
        show()

        openSaveAsRoutineAndConfirm()

        assertEquals("No se pudo guardar la rutina. Inténtalo de nuevo.", messages.current.value?.text)
        haptics.assertSingle(HapticEvent.Reject)
        rule.onNodeWithTag("save_as_routine_sheet").assertExists()
    }

    @Test
    fun deleting_closesTheDetail() {
        show()

        rule.onNodeWithTag("session_detail_delete").performClick()
        rule.waitForIdle()

        assertEquals(listOf("s1"), repository.deleted)
        assertEquals(1, closed)
    }

    @Test
    fun tappingAnExerciseName_opensItsDetails() {
        show()

        rule.onNodeWithTag("session_detail_exercise_0").performClick()

        assertEquals(1, opened.size)
    }
}
