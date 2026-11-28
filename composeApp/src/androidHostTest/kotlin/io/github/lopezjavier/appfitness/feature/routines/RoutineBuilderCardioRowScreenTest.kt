package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-151 scenario 1: a cardio exercise in the routine editor is one target in minutes and nothing else. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class RoutineBuilderCardioRowScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val exercises = FakeExerciseRepository(
        listOf(exerciseRow("a", "bench"), exerciseRow("bike", "bike", pattern = MovementPattern.Cardio)),
    )
    private val library = FakeExerciseLibrary(
        mapOf("bench" to exerciseInfo("bench", "Press de banca"), "bike" to exerciseInfo("bike", "Bicicleta")),
    )

    private fun show(vararg ids: String) {
        lateinit var current: RoutineBuilderViewModel
        rule.setContent {
            val vm = remember {
                RoutineBuilderViewModel(
                    null,
                    SavedStateHandle(),
                    FakeRoutineRepository(),
                    LoadExerciseCatalog(exercises, library),
                    FakeConnectivityObserver(),
                    FakeTrainingPreferencesRepository(),
                ).also { current = it }
            }
            val state by vm.uiState.collectAsState()
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    RoutineBuilderScreen(state, vm::onEvent, {}, {})
                }
            }
        }
        rule.waitForIdle()
        ids.forEach { current.onEvent(RoutineBuilderEvent.AddExercise(it)) }
        rule.waitForIdle()
    }

    @Test
    fun aCardioRow_showsOnlyTheMinutesStepper() {
        show("bike")

        rule.onNodeWithTag("routine_set_minutes_picker_0_0_value", useUnmergedTree = true)
            .assertTextEquals("Sin definir")
        rule.onNodeWithTag("routine_builder_add_set_0").assertDoesNotExist()
        rule.onNodeWithTag("routine_builder_rest_0_value", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("routine_builder_target_rpe_0_value", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("routine_builder_rep_min_0_value", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theCardioStepper_stepsInFiveMinutes() {
        show("bike")

        rule.onNodeWithTag("routine_set_minutes_picker_0_0_increment").performClick()
        rule.onNodeWithTag("routine_set_minutes_picker_0_0_increment").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("routine_set_minutes_picker_0_0_value", useUnmergedTree = true).assertTextEquals("35 min")
    }

    @Test
    fun aStrengthRow_keepsItsSeriesRestAndAddSet() {
        show("a")

        rule.onNodeWithTag("routine_builder_add_set_0").assertExists()
        rule.onNodeWithTag("routine_builder_rest_0_value", useUnmergedTree = true).assertExists()
    }
}
