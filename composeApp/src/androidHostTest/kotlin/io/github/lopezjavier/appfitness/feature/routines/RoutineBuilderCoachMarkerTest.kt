package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFigures
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.trainer.FakeCoachChangesRepository
import io.github.lopezjavier.appfitness.feature.trainer.coachChange
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-97 / US-104 in the routine editor: the `?` reason of a changed exercise and the range focus (ADR-40). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class RoutineBuilderCoachMarkerTest {
    @get:Rule
    val rule = createComposeRule()

    private val exercises = FakeExerciseRepository(listOf(exerciseRow("a", "bench"), exerciseRow("b", "row")))
    private val library = FakeExerciseLibrary(
        mapOf("bench" to exerciseInfo("bench", "Press de banca"), "row" to exerciseInfo("row", "Remo con barra")),
    )
    private val repository = FakeRoutineRepository(listOf(routineOf("r1", "Torso", "a", "b")))
    private val changes = FakeCoachChangesRepository()

    private fun viewModel(handle: SavedStateHandle, focus: String? = null) = RoutineBuilderViewModel(
        "r1",
        handle,
        repository,
        LoadExerciseCatalog(exercises, library),
        FakeConnectivityObserver(),
        FakeTrainingPreferencesRepository(),
        focusExerciseId = focus,
        coachChanges = changes,
    )

    private fun show(handle: SavedStateHandle = SavedStateHandle(), focus: String? = null): StateRestorationTester {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            val vm = remember { viewModel(handle, focus) }
            val state by vm.uiState.collectAsState()
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    RoutineBuilderScreen(state, vm::onEvent, {}, {})
                }
            }
        }
        rule.waitForIdle()
        return tester
    }

    @Test
    fun aChangedExercise_showsTheQuestionMark_thatOpensTheReason() {
        changes.changes = Result.success(
            listOf(
                coachChange(
                    "c1",
                    exerciseId = "a",
                    routineId = "r1",
                    figures = CoachChangeFigures(weightFromKg = 60.0, weightToKg = 62.5),
                ),
            ),
        )
        show()

        rule.onNodeWithContentDescription("Por qué cambió el peso: Press de banca").assertExists()
        rule.onNodeWithTag("routine_builder_coach_marker_a").performClick()

        rule.onNodeWithText("Cambio del entrenador").assertExists()
        rule.onNodeWithText(
            "Subida de 60 kg a 62,5 kg: completaste todas las series en el máximo del rango.",
        ).assertExists()
    }

    @Test
    fun anExerciseWithoutChange_hasNoMarker() {
        changes.changes = Result.success(listOf(coachChange("c1", exerciseId = "a", routineId = "r1")))
        show()

        rule.onNodeWithTag("routine_builder_coach_marker_b").assertDoesNotExist()
    }

    @Test
    fun anUndoneChange_hasNoMarker() {
        changes.changes = Result.success(
            listOf(coachChange("c1", exerciseId = "a", routineId = "r1", status = CoachChangeStatus.Undone)),
        )
        show()

        rule.onNodeWithTag("routine_builder_coach_marker_a").assertDoesNotExist()
    }

    @Test
    fun aChangeOfAnotherRoutine_hasNoMarker() {
        changes.changes = Result.success(listOf(coachChange("c1", exerciseId = "a", routineId = "other")))
        show()

        rule.onNodeWithTag("routine_builder_coach_marker_a").assertDoesNotExist()
    }

    @Test
    fun aNoRangeFlag_showsItsReasonBehindTheMarker() {
        changes.changes = Result.success(
            listOf(
                coachChange(
                    "c1",
                    kind = CoachChangeKind.NoRangeFlag,
                    status = CoachChangeStatus.Pending,
                    exerciseId = "a",
                    routineId = "r1",
                ),
            ),
        )
        show(focus = "a")

        rule.onNodeWithTag("routine_builder_rep_min_0_increment").assertExists()

        rule.onNodeWithTag("routine_builder_coach_marker_a").performClick()

        rule.onNodeWithText(
            "Este ejercicio no tiene un rango de repeticiones, así que el entrenador no puede subirlo solo.",
        ).assertExists()
    }

    @Test
    fun theOpenReason_survivesRecreation() {
        changes.changes = Result.success(listOf(coachChange("c1", exerciseId = "a", routineId = "r1")))
        val tester = show()
        rule.onNodeWithTag("routine_builder_coach_marker_a").performClick()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithText("Cambio del entrenador").assertExists()
    }
}
