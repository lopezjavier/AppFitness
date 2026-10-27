package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the details screen stores nothing; a view model recreated with the same routine id reloads it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class RoutineDetailsRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val exercises = FakeExerciseRepository(listOf(exerciseRow("a", "bench")))
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press de banca")))
    private val repository = FakeRoutineRepository(listOf(routineOf("r1", "Torso", "a")))

    @Test
    fun recreation_reloadsTheRoutineFromItsId() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            val vm = remember {
                RoutineDetailsViewModel(
                    "r1",
                    repository,
                    FakeWorkoutSessionRepository(),
                    LoadExerciseCatalog(exercises, library),
                    FakeConnectivityObserver(),
                )
            }
            val state by vm.uiState.collectAsState()
            AppTheme { RoutineDetailsScreen(state, vm::onEvent, {}, {}, {}) }
        }
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("routine_details_title").assertTextEquals("Torso")
    }
}
