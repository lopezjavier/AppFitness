package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-36 "Auto update": the "Actualizar automáticamente" switch of the routine builder (a draft until "Guardar"). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class RoutineBuilderAutoUpdateScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeRoutineRepository(listOf(routineOf("r1", "Torso", "a")))
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("a", "bench")))
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press de banca")))
    private val handle = SavedStateHandle()

    private fun newViewModel(routineId: String?) = RoutineBuilderViewModel(
        routineId,
        handle,
        repository,
        LoadExerciseCatalog(exercises, library),
        FakeConnectivityObserver(),
        FakeTrainingPreferencesRepository(),
    )

    private fun show(routineId: String?, vm: RoutineBuilderViewModel = newViewModel(routineId)) {
        rule.setContent {
            AppTheme {
                RoutineBuilderRoot(
                    routineId = routineId,
                    selectedExerciseId = null,
                    onConsumeSelection = {},
                    onAddExercise = {},
                    onOpenExercise = {},
                    onClose = {},
                    viewModel = vm,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun newRoutine_showsTheLabelAndHelper_withTheSwitchOff() {
        show(null)

        rule.onNodeWithText("Actualizar automáticamente").assertExists()
        rule.onNodeWithText("Al terminar un entreno de esta rutina, se guarda con los cambios sin preguntar.")
            .assertExists()
        rule.onNodeWithTag("routine_builder_auto_update_switch").assertIsOff()
    }

    @Test
    fun theSwitchRow_has48dpAndSaysItsState() {
        show(null)

        rule.onNodeWithTag("routine_builder_auto_update_switch").assertHeightIsAtLeast(48.dp)
        val config = rule.onNodeWithTag("routine_builder_auto_update_switch").fetchSemanticsNode().config
        assertEquals("Desactivado", config[SemanticsProperties.StateDescription])
    }

    @Test
    fun tappingTheSwitch_turnsItOn_andGuardarSavesIt() {
        show("r1")
        rule.onNodeWithTag("routine_builder_name_field").performTextInput("!")

        rule.onNodeWithTag("routine_builder_auto_update_switch").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_auto_update_switch").assertIsOn()
        rule.onNodeWithTag("routine_builder_save_button").performClick()
        rule.waitForIdle()

        assertTrue(repository.saved.single().autoUpdateRoutine)
    }

    @Test
    fun anExistingRoutineWithAutoUpdate_showsTheSwitchOn() {
        repository.routines[0] = repository.routines[0].copy(autoUpdateRoutine = true)

        show("r1")

        rule.onNodeWithTag("routine_builder_auto_update_switch").assertIsOn()
    }

    @Test
    fun theToggledSwitch_survivesRecreationWithoutSaving() {
        var current by mutableStateOf(newViewModel("r1"))
        rule.setContent {
            AppTheme { RoutineBuilderRoot("r1", null, {}, {}, {}, {}, viewModel = current) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_auto_update_switch").performClick()
        rule.waitForIdle()

        // A new ViewModel over the same SavedStateHandle, as after process death.
        current = newViewModel("r1")
        rule.waitForIdle()

        rule.onNodeWithTag("routine_builder_auto_update_switch").assertIsOn()
        assertTrue(repository.saved.isEmpty())
    }
}
