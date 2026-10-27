package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HapticEvent
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.RecordingAppHaptics
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

/** US-10 / US-56 on the routines list: the cards, their states, the actions sheet and the immediate delete. */
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
    private val haptics = RecordingAppHaptics()
    private val messages = RecordingMessages()

    private fun show(): RoutinesViewModel {
        val vm = RoutinesViewModel(repository, sessions, connectivity)
        rule.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics, LocalAppMessages provides messages) {
                AppTheme {
                    val state by vm.uiState.collectAsState()
                    LaunchedEffect(vm) { vm.effects.collect { effects += it } }
                    RoutinesScreen(state, vm::onEvent)
                }
            }
        }
        rule.waitForIdle()
        return vm
    }

    private fun openMenu(id: String) {
        rule.onNodeWithTag("routines_row_menu_$id").performClick()
        rule.waitForIdle()
    }

    /** Width of a card in px: the list minus its horizontal content padding. */
    private fun cardWidthPx(): Float {
        val list = rule.onNodeWithTag("routines_list").fetchSemanticsNode().size.width
        return list - 2 * with(rule.density) { 16.dp.toPx() }
    }

    /** Drags the name block of [id] left by [fraction] of the card width. */
    private fun swipeCard(id: String, fraction: Float, release: Boolean = true) {
        val distance = cardWidthPx() * fraction
        rule.onNodeWithTag("routines_row_$id").performTouchInput {
            down(center)
            moveBy(Offset(-distance, 0f))
            if (release) up()
        }
        rule.waitForIdle()
    }

    @Test
    fun list_showsEveryRoutineWithItsExerciseCount_andNoTitleOfItsOwn() {
        show()

        rule.onNodeWithTag("routines_title").assertDoesNotExist()
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
    fun añadirRutina_closesTheList_andOpensTheBuilder() {
        show()

        rule.onNodeWithTag("routines_add_routine").assertTextContains("Añadir rutina")
        rule.onNodeWithTag("routines_add_folder").assertTextContains("Añadir carpeta")
        rule.onNodeWithTag("routines_add_routine").performClick()
        rule.waitForIdle()

        assertEquals(listOf<RoutinesEffect>(RoutinesEffect.OpenBuilder(null)), effects)
    }

    @Test
    fun theOldPinnedButtons_areGone() {
        show()

        rule.onNodeWithTag("routines_new_button").assertDoesNotExist()
        rule.onNodeWithTag("routines_new_folder_button").assertDoesNotExist()
        rule.onAllNodesWithText("Nueva rutina").assertCountEquals(0)
    }

    @Test
    fun tappingARoutine_opensItsDetails() {
        show()

        rule.onNodeWithTag("routines_row_r1").performClick()
        rule.waitForIdle()

        assertEquals(listOf<RoutinesEffect>(RoutinesEffect.OpenDetails("r1")), effects)
    }

    @Test
    fun pencil_opensTheBuilderForThatRoutine() {
        show()

        rule.onNodeWithTag("routines_edit_r1").performClick()
        rule.waitForIdle()

        assertEquals(listOf<RoutinesEffect>(RoutinesEffect.OpenBuilder("r1")), effects)
    }

    @Test
    fun noRoutines_showsTheEmptyStateWithOneActionAñadirRutina() {
        repository.routines.clear()
        show()

        rule.onNodeWithTag("routines_empty").assertExists()
        rule.onNodeWithTag("routines_empty_add").assertTextContains("Añadir rutina")
        rule.onNodeWithTag("routines_add_routine").assertDoesNotExist()
        rule.onNodeWithTag("routines_add_folder").assertDoesNotExist()
        rule.onNodeWithTag("routines_empty_add").performClick()
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
    fun actionsSheet_offersEditarDuplicarMoverSiguienteAndEliminar_withoutArchivar() {
        show()

        openMenu("r1")

        rule.onNodeWithText("Editar").assertExists()
        rule.onNodeWithText("Duplicar").assertExists()
        rule.onNodeWithText("Mover a carpeta").assertExists()
        rule.onNodeWithText("Siguiente entreno").assertExists()
        rule.onNodeWithText("Eliminar").assertExists()
        rule.onAllNodesWithText("Archivar").assertCountEquals(0)
    }

    @Test
    fun menuEditar_opensTheBuilderForThatRoutine() {
        show()
        openMenu("r1")

        rule.onNodeWithText("Editar").performClick()
        rule.waitForIdle()

        assertEquals(listOf<RoutinesEffect>(RoutinesEffect.OpenBuilder("r1")), effects)
    }

    @Test
    fun menuDuplicar_addsACopyToTheList() {
        show()
        openMenu("r1")

        rule.onNodeWithText("Duplicar").performClick()
        rule.waitForIdle()

        assertEquals(listOf("r1"), repository.duplicated)
        rule.onNodeWithText("Torso fuerza (copia)").assertExists()
    }

    @Test
    fun menuEliminar_removesTheRoutineAtOnce_withoutADialog() {
        show()
        openMenu("r1")

        rule.onNodeWithText("Eliminar").performClick()
        rule.waitForIdle()

        assertEquals(listOf("r1"), repository.deletedIds)
        rule.onNodeWithTag("routines_row_r1").assertDoesNotExist()
        rule.onNodeWithTag("routines_row_r2").assertExists()
        rule.onNodeWithTag("routines_folder_delete_dialog").assertDoesNotExist()
    }

    @Test
    fun offline_theSheetOnlyOffersEditarAndEliminar_withTheReason() {
        show()
        connectivity.state.value = false
        rule.waitForIdle()

        openMenu("r1")

        rule.onNodeWithText("Editar").assertExists()
        rule.onNodeWithText("Eliminar").assertExists()
        rule.onAllNodesWithText("Duplicar").assertCountEquals(0)
        rule.onNodeWithTag("routines_menu_offline_reason").assertExists()
    }

    @Test
    fun everyRoutineHasAnEmpezarButton_andNoEmptyWorkoutIsOffered() {
        show()

        rule.onNodeWithTag("routines_start_r1").assertTextContains("Empezar")
        rule.onNodeWithTag("routines_start_r2").assertTextContains("Empezar")
        rule.onNodeWithTag("routines_start_empty").assertDoesNotExist()
        rule.onNodeWithTag("offline_hint").assertDoesNotExist()
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
    fun empezar_whileASessionIsInProgress_showsTheMessage_andOpensNothing() {
        sessions.startFailure = AppError.SessionAlreadyInProgress
        var openedLive = 0
        val vm = RoutinesViewModel(repository, sessions, connectivity)
        rule.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics, LocalAppMessages provides messages) {
                AppTheme { RoutinesRoot({}, {}, { openedLive++ }, viewModel = vm) }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("routines_start_r1").performClick()
        rule.waitForIdle()

        assertEquals(listOf("Ya hay un entreno en curso"), messages.shown)
        assertEquals(listOf(HapticEvent.Reject), haptics.calls)
        assertEquals(0, openedLive)
        assertEquals(listOf("r1"), sessions.startedRoutines)
    }

    @Test
    fun offline_disablesEveryStartButton_andShowsTheOfflineHint() {
        show()
        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("routines_start_r1").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede empezar").assertExists()
    }

    @Test
    fun contentState_rendersWithoutAViewModel() {
        rule.setContent {
            AppTheme { RoutinesScreen(RoutinesUiState.Content(listOf(RoutineSummary("x", "Solo", 5))), {}) }
        }

        rule.onNodeWithTag("routines_row_x").assertTextContains("5 ejercicios")
    }

    // ---- Swipe to delete (R-20, R-21) ----

    @Test
    fun swipingTheCardPastHalf_deletesAtOnce_withoutADialog() {
        show()

        swipeCard("r1", 0.7f)

        assertEquals(listOf("r1"), repository.deletedIds)
        rule.onNodeWithTag("routines_row_r1").assertDoesNotExist()
        rule.onNodeWithTag("routines_row_r2").assertExists()
        assertTrue(haptics.calls.contains(HapticEvent.Confirm))
    }

    @Test
    fun swipingTheCardBelowHalf_springsBack_andDeletesNothing() {
        show()

        swipeCard("r1", 0.3f)

        assertTrue(repository.deletedIds.isEmpty())
        rule.onNodeWithTag("routines_row_r1").assertIsDisplayed()
        assertTrue(haptics.calls.isEmpty())
    }

    @Test
    fun aFailedDelete_putsTheRoutineBack_andShowsTheMessage() {
        repository.saveFailure = AppError.Network
        val vm = RoutinesViewModel(repository, sessions, connectivity)
        rule.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics, LocalAppMessages provides messages) {
                AppTheme { RoutinesRoot({}, {}, {}, viewModel = vm) }
            }
        }
        rule.waitForIdle()

        swipeCard("r1", 0.7f)
        rule.waitForIdle()

        rule.onNodeWithTag("routines_row_r1").assertExists()
        assertEquals(listOf("No se pudo completar la acción. Inténtalo de nuevo."), messages.shown)
        assertTrue(haptics.calls.contains(HapticEvent.Reject))
    }

    // ---- C2: handle versus swipe, targets, descriptions ----

    @Test
    fun draggingTheHandleSideways_neverArmsTheSwipe() {
        show()

        rule.onNodeWithTag("routines_drag_r1").performTouchInput {
            down(center)
            moveBy(Offset(-cardWidthPx() * 0.8f, 0f))
            up()
        }
        rule.waitForIdle()

        rule.onNodeWithTag("routines_swipe_bg_r1").assertDoesNotExist()
        assertTrue(repository.deletedIds.isEmpty())
        assertTrue(haptics.calls.none { it == HapticEvent.Armed })
    }

    @Test
    fun draggingTheHandleDown_reordersTheRoutines_andNeverArmsTheSwipe() {
        show()

        rule.onNodeWithTag("routines_drag_r1").performTouchInput {
            down(center)
            repeat(DRAG_STEPS) { moveBy(Offset(0f, DRAG_STEP_PX)) }
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf(null to listOf("r2", "r1")), repository.savedOrders)
        rule.onNodeWithTag("routines_swipe_bg_r1").assertDoesNotExist()
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun swipingTheCardBody_neverReorders() {
        show()

        swipeCard("r1", 0.3f)

        assertTrue(repository.savedOrders.isEmpty())
    }

    @Test
    fun everyControlOfTheCard_isAtLeast48dp_andDescribedByName() {
        show()

        rule.onNodeWithTag("routines_edit_r1").assertContentDescriptionEquals("Editar Torso fuerza")
        rule.onNodeWithTag("routines_row_menu_r1").assertContentDescriptionEquals("Más acciones de Torso fuerza")
        rule.onNodeWithTag("routines_drag_r1").assertContentDescriptionEquals("Mover Torso fuerza")
        rule.onNodeWithTag("routines_start_r1").assertContentDescriptionEquals("Empezar Torso fuerza")
        listOf("routines_edit_r1", "routines_row_menu_r1", "routines_drag_r1", "routines_start_r1", "routines_row_r1")
            .forEach { tag ->
                rule.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                rule.onNodeWithTag(tag).assertIsDisplayed()
            }
    }

    @Test
    fun theCard_exposesSubirBajarAndEliminarToTalkBack() {
        show()

        val custom = customActionLabels(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertTrue("Bajar" in custom)
        assertTrue("Subir" in custom)
        assertTrue("Eliminar Torso fuerza" in custom)
        assertTrue("Eliminar Pierna" in custom)
    }

    private fun customActionLabels(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label } +
            node.children.flatMap { customActionLabels(it) }
}

private const val DRAG_STEPS = 8
private const val DRAG_STEP_PX = 50f
