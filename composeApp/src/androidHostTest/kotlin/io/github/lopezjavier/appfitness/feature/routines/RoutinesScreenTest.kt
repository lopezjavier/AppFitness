package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-10 "Mis rutinas": the list, its states, the row menu and the archive confirmation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class RoutinesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository =
        FakeRoutineRepository(listOf(routineOf("r1", "Torso fuerza", "a", "b"), routineOf("r2", "Pierna", "c")))
    private val connectivity = FakeConnectivityObserver()
    private val sessions = FakeWorkoutSessionRepository()
    private val effects = mutableListOf<RoutinesEffect>()

    private fun show(): RoutinesViewModel {
        val vm = RoutinesViewModel(repository, sessions, connectivity)
        rule.setContent {
            AppTheme {
                val state by vm.uiState.collectAsState()
                LaunchedEffect(vm) { vm.effects.collect { effects += it } }
                RoutinesScreen(state, vm::onEvent)
            }
        }
        rule.waitForIdle()
        return vm
    }

    private fun openMenu(id: String) {
        rule.onNodeWithTag("routines_row_menu_$id").performClick()
        rule.waitForIdle()
    }

    @Test
    fun list_showsEveryRoutineWithItsExerciseCount() {
        show()

        rule.onNodeWithTag("routines_title").assertTextContains("Mis rutinas")
        rule.onNodeWithTag("routines_row_r1").assertTextContains("Torso fuerza")
        rule.onNodeWithTag("routines_row_r1").assertTextContains("2 ejercicios")
    }

    @Test
    fun aRoutineWithOneExercise_usesTheSingular() {
        show()

        rule.onNodeWithTag("routines_row_r2").assertTextContains("1 ejercicio")
        rule.onNodeWithText("1 ejercicios").assertDoesNotExist()
    }

    @Test
    fun newRoutineButton_isPinnedInTheContent_andOpensTheBuilder() {
        show()

        rule.onNodeWithTag("routines_new_button").assertTextContains("Nueva rutina")
        rule.onNodeWithTag("routines_new_button").performClick()
        rule.waitForIdle()

        assertEquals(listOf<RoutinesEffect>(RoutinesEffect.OpenBuilder(null)), effects)
    }

    @Test
    fun tappingARoutine_opensItInTheBuilder() {
        show()

        rule.onNodeWithTag("routines_row_r1").performClick()
        rule.waitForIdle()

        assertEquals(listOf<RoutinesEffect>(RoutinesEffect.OpenBuilder("r1")), effects)
    }

    @Test
    fun noRoutines_showsTheEmptyStateWithNuevaRutina() {
        repository.routines.clear()
        show()

        rule.onNodeWithTag("routines_empty").assertExists()
        rule.onNodeWithTag("routines_empty_action").assertTextContains("Nueva rutina")
        rule.onNodeWithTag("routines_new_button").assertDoesNotExist()
        rule.onNodeWithTag("routines_empty_action").performClick()
        rule.waitForIdle()
        assertEquals(listOf<RoutinesEffect>(RoutinesEffect.OpenBuilder(null)), effects)
    }

    @Test
    fun loading_showsSkeletonRows() {
        rule.mainClock.autoAdvance = false
        rule.setContent { AppTheme { RoutinesScreen(RoutinesUiState.Loading, {}) } }

        rule.onNodeWithTag("routines_loading").assertExists()
    }

    @Test
    fun loadFailure_showsTheErrorWithRetry_andRetryRecovers() {
        repository.failure = AppError.Network
        show()
        rule.onNodeWithTag("routines_error").assertExists()

        repository.failure = null
        rule.onNodeWithTag("routines_error_action").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("routines_row_r1").assertExists()
    }

    @Test
    fun rowMenu_offersEditarDuplicarAndArchivar() {
        show()

        openMenu("r1")

        rule.onNodeWithTag("routines_menu_edit_r1").assertTextContains("Editar")
        rule.onNodeWithTag("routines_menu_duplicate_r1").assertTextContains("Duplicar")
        rule.onNodeWithTag("routines_menu_archive_r1").assertTextContains("Archivar")
    }

    @Test
    fun menuEditar_opensTheBuilderForThatRoutine() {
        show()
        openMenu("r1")

        rule.onNodeWithTag("routines_menu_edit_r1").performClick()
        rule.waitForIdle()

        assertEquals(listOf<RoutinesEffect>(RoutinesEffect.OpenBuilder("r1")), effects)
    }

    @Test
    fun menuDuplicar_addsACopyToTheList() {
        show()
        openMenu("r1")

        rule.onNodeWithTag("routines_menu_duplicate_r1").performClick()
        rule.waitForIdle()

        assertEquals(listOf("r1"), repository.duplicated)
        rule.onNodeWithText("Torso fuerza (copia)").assertExists()
    }

    @Test
    fun archive_asksForConfirmation_andThenRemovesTheRoutine() {
        show()
        openMenu("r1")

        rule.onNodeWithTag("routines_menu_archive_r1").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("routines_archive").assertExists()
        assertTrue(repository.archivedIds.isEmpty())

        rule.onNodeWithTag("routines_archive_confirm").performClick()
        rule.waitForIdle()

        assertEquals(listOf("r1"), repository.archivedIds)
        rule.onNodeWithTag("routines_row_r1").assertDoesNotExist()
        rule.onNodeWithTag("routines_row_r2").assertExists()
    }

    @Test
    fun archive_cancelled_keepsTheRoutine() {
        show()
        openMenu("r1")
        rule.onNodeWithTag("routines_menu_archive_r1").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("routines_archive_cancel").performClick()
        rule.waitForIdle()

        assertTrue(repository.archivedIds.isEmpty())
        rule.onNodeWithTag("routines_row_r1").assertExists()
    }

    @Test
    fun offline_disablesDuplicarAndArchivar_butNotEditar() {
        show()
        connectivity.state.value = false
        rule.waitForIdle()

        openMenu("r1")

        rule.onNodeWithTag("routines_menu_edit_r1").assertIsEnabled()
        rule.onNodeWithTag("routines_menu_duplicate_r1").assertIsNotEnabled()
        rule.onNodeWithTag("routines_menu_archive_r1").assertIsNotEnabled()
        rule.onNodeWithTag("routines_menu_offline_reason").assertExists()
    }

    @Test
    fun everyRoutineHasAnEmpezarButton_andEmptyWorkoutIsOffered() {
        show()

        rule.onNodeWithTag("routines_start_r1").assertTextContains("Empezar")
        rule.onNodeWithTag("routines_start_r2").assertTextContains("Empezar")
        rule.onNodeWithTag("routines_start_empty").assertTextContains("Empezar entreno vacío")
        rule.onNodeWithTag("routines_start_offline_reason").assertDoesNotExist()
    }

    @Test
    fun empezar_startsTheRoutine_andOpensTheLiveSession() {
        show()

        rule.onNodeWithTag("routines_start_r1").performClick()
        rule.waitForIdle()

        assertEquals(listOf("r1"), sessions.startedRoutines)
        assertTrue(effects.contains(RoutinesEffect.OpenLiveSession))
    }

    @Test
    fun emptyWorkout_startsAnEmptySession() {
        show()

        rule.onNodeWithTag("routines_start_empty").performClick()
        rule.waitForIdle()

        assertEquals(1, sessions.startedEmpty)
        assertTrue(effects.contains(RoutinesEffect.OpenLiveSession))
    }

    @Test
    fun offline_disablesEveryStartButton_andExplainsWhy() {
        show()
        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("routines_start_r1").assertIsNotEnabled()
        rule.onNodeWithTag("routines_start_empty").assertIsNotEnabled()
        rule.onNodeWithTag("routines_start_offline_reason").assertExists()
    }

    @Test
    fun emptyList_stillOffersTheEmptyWorkout() {
        repository.routines.clear()
        show()

        rule.onNodeWithTag("routines_start_empty").assertExists()
    }

    @Test
    fun contentState_rendersWithoutAViewModel() {
        rule.setContent {
            AppTheme { RoutinesScreen(RoutinesUiState.Content(listOf(RoutineSummary("x", "Solo", 5))), {}) }
        }

        rule.onNodeWithTag("routines_row_x").assertTextContains("5 ejercicios")
    }
}
