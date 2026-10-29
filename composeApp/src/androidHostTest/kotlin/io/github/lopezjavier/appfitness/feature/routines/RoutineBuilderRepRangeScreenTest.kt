package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
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

/** US-70 builder part: the "Reps mín." / "Reps máx." steppers, their help and their restoration (ADR-40). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class RoutineBuilderRepRangeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val exercises = FakeExerciseRepository(listOf(exerciseRow("a", "bench")))
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press de banca")))
    private val repository = FakeRoutineRepository()

    private fun viewModel(handle: SavedStateHandle) = RoutineBuilderViewModel(
        null,
        handle,
        repository,
        LoadExerciseCatalog(exercises, library),
        FakeConnectivityObserver(),
        FakeTrainingPreferencesRepository(),
    )

    private fun show(handle: SavedStateHandle = SavedStateHandle()): StateRestorationTester {
        val tester = StateRestorationTester(rule)
        lateinit var current: RoutineBuilderViewModel
        tester.setContent {
            val vm = remember { viewModel(handle).also { current = it } }
            val state by vm.uiState.collectAsState()
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    RoutineBuilderScreen(state, vm::onEvent, {}, {})
                }
            }
        }
        rule.waitForIdle()
        current.onEvent(RoutineBuilderEvent.AddExercise("a"))
        rule.waitForIdle()
        return tester
    }

    private fun text(tag: String) = rule.onNodeWithTag("${tag}_value", useUnmergedTree = true)

    @Test
    fun withoutRange_bothSteppersShowSinDefinir() {
        show()

        text("routine_builder_rep_min_0").assertTextEquals("Sin definir")
        text("routine_builder_rep_max_0").assertTextEquals("Sin definir")
        rule.onNodeWithTag("routine_builder_rep_min_0_decrement").assertIsNotEnabled()
    }

    @Test
    fun firstStepUp_setsEightToTwelve() {
        show()

        rule.onNodeWithTag("routine_builder_rep_min_0_increment").performClick()
        rule.waitForIdle()

        text("routine_builder_rep_min_0").assertTextEquals("8")
        text("routine_builder_rep_max_0").assertTextEquals("12")
    }

    @Test
    fun steppingMinDownFromOne_clearsTheRange() {
        show()
        rule.onNodeWithTag("routine_builder_rep_max_0_increment").performClick()
        repeat(MIN_STEPS_TO_ONE) { rule.onNodeWithTag("routine_builder_rep_min_0_decrement").performClick() }
        rule.waitForIdle()

        text("routine_builder_rep_min_0").assertTextEquals("Sin definir")
        text("routine_builder_rep_max_0").assertTextEquals("Sin definir")
    }

    @Test
    fun theHelp_opensItsSheet() {
        show()

        rule.onNodeWithTag("routine_builder_rep_range_help_0").performClick()

        rule.onNodeWithTag("routine_builder_rep_range_help_0_sheet").assertExists()
    }

    @Test
    fun anUnsavedRange_survivesRecreation() {
        val tester = show()
        rule.onNodeWithTag("routine_builder_rep_min_0_increment").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        text("routine_builder_rep_min_0").assertTextEquals("8")
        text("routine_builder_rep_max_0").assertTextEquals("12")
    }

    private companion object {
        /** From 8 down to 1 is seven steps, the eighth clears the range. */
        const val MIN_STEPS_TO_ONE = 8
    }
}
