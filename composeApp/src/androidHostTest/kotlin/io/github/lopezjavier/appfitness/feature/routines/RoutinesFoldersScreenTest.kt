package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-35 on "Mis rutinas": folders, next workout and manual order. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class RoutinesFoldersScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository =
        FakeRoutineRepository(
            listOf(
                routineOf("r1", "Torso A", "a"),
                routineOf("r2", "Torso B", "a"),
                routineOf("r3", "Torso C", "a"),
                routineOf("r4", "Pierna", "a"),
            ),
        ).apply {
            folders += RoutineFolder("f1", "Fuerza")
            folders += RoutineFolder("f2", "Vacía")
            folderOf["r1"] = "f1"
            folderOf["r2"] = "f1"
            folderOf["r3"] = "f1"
        }
    private val connectivity = FakeConnectivityObserver()

    private fun show() {
        val vm = RoutinesViewModel(repository, FakeWorkoutSessionRepository(), connectivity)
        rule.setContent {
            AppTheme {
                val state by vm.uiState.collectAsState()
                RoutinesScreen(state, vm::onEvent)
            }
        }
        rule.waitForIdle()
    }

    private fun scrollToAddFolder() {
        rule.onNodeWithTag("routines_list").performScrollToNode(hasTestTag("routines_add_folder"))
        rule.waitForIdle()
    }

    private fun addFolder() {
        scrollToAddFolder()
        click("routines_add_folder")
    }

    private fun clickText(text: String) {
        rule.onNodeWithText(text).performClick()
        rule.waitForIdle()
    }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun openMenu(id: String) = click("routines_row_menu_$id")

    @Test
    fun routines_areGroupedUnderTheirFolder_andTheNoFolderGroupIsTitled() {
        show()

        rule.onNodeWithTag("routines_folder_f1").assertTextContains("Fuerza")
        rule.onNodeWithTag("routines_folder_f1").assertTextContains("3 rutinas")
        rule.onNodeWithTag("routines_row_r1").assertTextContains("Torso A")
        rule.onNodeWithTag("routines_group_none").assertTextContains("Sin carpeta")
        rule.onNodeWithTag("routines_row_r4").assertTextContains("Pierna")
    }

    @Test
    fun withoutFolders_theNoFolderGroupHasNoHeader() {
        repository.folders.clear()
        repository.folderOf.clear()
        show()

        rule.onNodeWithTag("routines_group_none").assertDoesNotExist()
        rule.onNodeWithTag("routines_row_r4").assertExists()
    }

    @Test
    fun anExpandedEmptyFolder_showsCarpetaVacia() {
        show()

        rule.onNodeWithTag("routines_folder_empty_f2").assertTextEquals("Carpeta vacía")
    }

    @Test
    fun foldersOnly_noRoutines_showsTheFoldersNotTheEmptyState() {
        repository.routines.clear()
        show()

        rule.onNodeWithTag("routines_empty").assertDoesNotExist()
        rule.onNodeWithTag("routines_folder_f1").assertExists()
    }

    @Test
    fun folderHeader_hasDescribedFortyEightDpControls() {
        show()

        rule.onNodeWithTag("routines_folder_toggle_f1").assertContentDescriptionEquals("Contraer carpeta Fuerza")
        rule.onNodeWithTag("routines_folder_toggle_f1").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        rule.onNodeWithTag("routines_folder_rename_f1").assertContentDescriptionEquals("Renombrar carpeta Fuerza")
        rule.onNodeWithTag("routines_folder_rename_f1").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        rule.onNodeWithTag("routines_folder_delete_f1").assertContentDescriptionEquals("Eliminar carpeta Fuerza")
        rule.onNodeWithTag("routines_folder_delete_f1").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        rule.onNodeWithTag("routines_folder_toggle_f1").assert(stateIs("Expandida"))
    }

    @Test
    fun toggle_collapsesTheFolder_andExpandsItAgain() {
        show()

        click("routines_folder_toggle_f1")

        rule.onNodeWithTag("routines_row_r1").assertDoesNotExist()
        rule.onNodeWithTag("routines_folder_toggle_f1").assertContentDescriptionEquals("Expandir carpeta Fuerza")
        rule.onNodeWithTag("routines_folder_toggle_f1").assert(stateIs("Contraída"))
        click("routines_folder_toggle_f1")
        rule.onNodeWithTag("routines_row_r1").assertExists()
    }

    @Test
    fun creatingAFolder_savesItAndShowsItInTheList() {
        show()
        addFolder()
        rule.onNodeWithTag("folder_sheet_commit").assertIsNotEnabled()

        rule.onNodeWithTag("folder_sheet_input").performTextInput("Piernas")
        rule.waitForIdle()
        click("folder_sheet_commit")

        assertEquals(listOf("Piernas"), repository.createdFolders)
        rule.onNodeWithTag("folder_sheet_commit").assertDoesNotExist()
        rule.onNodeWithText("Piernas").assertExists()
    }

    @Test
    fun creatingAFolderWithAnExistingName_showsTheErrorAndDisablesGuardar() {
        show()
        addFolder()

        rule.onNodeWithTag("folder_sheet_input").performTextInput(" fuerza ")
        rule.waitForIdle()

        rule.onNodeWithText("Ya existe una carpeta con ese nombre").assertExists()
        rule.onNodeWithTag("folder_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun renamingAFolderToAnotherFoldersName_showsTheErrorAndDisablesGuardar() {
        show()
        click("routines_folder_rename_f2")
        rule.onNodeWithTag("folder_sheet_input").performTextClearance()
        rule.onNodeWithTag("folder_sheet_input").performTextInput("Fuerza")
        rule.waitForIdle()

        rule.onNodeWithText("Ya existe una carpeta con ese nombre").assertExists()
        rule.onNodeWithTag("folder_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun closingTheFolderSheetWithoutGuardar_savesNothing() {
        show()
        addFolder()
        rule.onNodeWithTag("folder_sheet_input").performTextInput("Piernas")
        rule.waitForIdle()

        click("folder_sheet_close")

        assertTrue(repository.createdFolders.isEmpty())
        rule.onNodeWithTag("folder_sheet_commit").assertDoesNotExist()
    }

    @Test
    fun renamingAFolder_prefillsTheNameAndSavesTheNewOne() {
        show()
        click("routines_folder_rename_f1")
        rule.onNodeWithTag("folder_sheet_input").assertTextContains("Fuerza")

        rule.onNodeWithTag("folder_sheet_input").performTextInput(" 2")
        rule.waitForIdle()
        click("folder_sheet_commit")

        assertEquals(listOf("f1" to "Fuerza 2"), repository.renamedFolders)
    }

    @Test
    fun deletingAFolderWithThreeRoutines_asksWithThePluralCount_andDeletesThem() {
        show()
        click("routines_folder_toggle_f1")

        click("routines_folder_delete_f1")

        rule.onNodeWithText("¿Eliminar la carpeta «Fuerza»?").assertExists()
        rule.onNodeWithText(
            "Se eliminarán la carpeta y sus 3 rutinas. Los entrenos que ya hiciste no cambian.",
        ).assertExists()
        click("routines_folder_delete_confirm")

        assertEquals(listOf("f1"), repository.deletedFolders)
        rule.onNodeWithTag("routines_folder_f1").assertDoesNotExist()
        rule.onNodeWithTag("routines_row_r4").assertExists()
    }

    @Test
    fun deletingAFolderWithOneRoutine_usesTheSingular() {
        repository.folderOf["r2"] = null
        repository.folderOf["r3"] = null
        show()

        click("routines_folder_delete_f1")

        rule.onNodeWithText(
            "Se eliminarán la carpeta y su rutina. Los entrenos que ya hiciste no cambian.",
        ).assertExists()
    }

    @Test
    fun cancellingTheFolderDeletion_keepsEverything() {
        show()
        click("routines_folder_delete_f1")

        click("routines_folder_delete_cancel")

        assertTrue(repository.deletedFolders.isEmpty())
        rule.onNodeWithTag("routines_folder_delete_dialog").assertDoesNotExist()
        rule.onNodeWithTag("routines_row_r1").assertExists()
    }

    @Test
    fun anEmptyFolder_isDeletedAtOnceWithoutDialog() {
        show()

        click("routines_folder_delete_f2")

        rule.onNodeWithTag("routines_folder_delete_dialog").assertDoesNotExist()
        assertEquals(listOf("f2"), repository.deletedFolders)
        rule.onNodeWithTag("routines_folder_f2").assertDoesNotExist()
    }

    @Test
    fun nextWorkout_isHighlighted_andMovesToAnotherRoutine() {
        show()
        rule.onNodeWithTag("routines_next_badge_r1").assertDoesNotExist()

        openMenu("r1")
        rule.onNodeWithText("Siguiente entreno").assertExists()
        clickText("Siguiente entreno")
        rule.onNodeWithTag("routines_next_badge_r1").assertTextEquals("Siguiente")

        openMenu("r4")
        clickText("Siguiente entreno")
        rule.onNodeWithTag("routines_next_badge_r4").assertExists()
        rule.onNodeWithTag("routines_next_badge_r1").assertDoesNotExist()
    }

    @Test
    fun nextWorkout_canBeClearedFromTheMenu() {
        repository.nextId = "r1"
        show()

        openMenu("r1")
        rule.onNodeWithText("Quitar como siguiente entreno").assertExists()
        clickText("Quitar como siguiente entreno")

        rule.onNodeWithTag("routines_next_badge_r1").assertDoesNotExist()
    }

    @Test
    fun everyRoutineHasADescribedDragHandle() {
        show()

        rule.onNodeWithTag("routines_drag_r1").assertContentDescriptionEquals("Mover Torso A")
        rule.onNodeWithTag("routines_drag_r1").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        rule.onNodeWithTag("routines_drag_r4").assertExists()
    }

    @Test
    fun talkBackBajar_reordersTheRoutineWithinItsFolder() {
        show()

        val bajar = customActions(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()).first {
            it.label == "Bajar"
        }
        rule.runOnUiThread { bajar.action.invoke() }
        rule.waitForIdle()

        assertEquals("f1" to listOf("r2", "r1", "r3"), repository.savedOrders.single())
    }

    private fun customActions(node: SemanticsNode): List<CustomAccessibilityAction> =
        node.config.getOrNull(SemanticsActions.CustomActions).orEmpty() + node.children.flatMap { customActions(it) }

    @Test
    fun moveToFolder_picksATarget_andMovesTheRoutine() {
        show()
        openMenu("r4")
        clickText("Mover a carpeta")
        rule.onNodeWithTag("routines_move_target_none").assertIsNotEnabled()
        rule.onNodeWithTag("routines_move_target_none").assertTextContains("Sin carpeta")

        click("routines_move_target_f2")

        assertEquals(listOf("r4" to "f2"), repository.movedToFolder)
        rule.onNodeWithTag("routines_move_target_f2").assertDoesNotExist()
        rule.onNodeWithTag("routines_folder_empty_f2").assertDoesNotExist()
    }

    @Test
    fun closingTheMoveSheet_movesNothing() {
        show()
        openMenu("r4")
        clickText("Mover a carpeta")

        click("routines_move_sheet_close")

        assertTrue(repository.movedToFolder.isEmpty())
        rule.onNodeWithTag("routines_move_target_f1").assertDoesNotExist()
    }

    @Test
    fun offline_disablesFolderWritesAndOrganisationRows() {
        show()
        connectivity.state.value = false
        rule.waitForIdle()

        scrollToAddFolder()
        rule.onNodeWithTag("routines_add_folder").assertIsNotEnabled()
        rule.onNodeWithTag("routines_folder_rename_f1").assertIsNotEnabled()
        rule.onNodeWithTag("routines_folder_delete_f1").assertIsNotEnabled()
        click("routines_folder_toggle_f1")
        openMenu("r4")
        rule.onAllNodesWithText("Mover a carpeta").assertCountEquals(0)
        rule.onAllNodesWithText("Siguiente entreno").assertCountEquals(0)
    }

    private fun stateIs(expected: String) = SemanticsMatcher("state description is $expected") {
        SemanticsProperties.StateDescription in it.config && it.config[SemanticsProperties.StateDescription] == expected
    }
}
