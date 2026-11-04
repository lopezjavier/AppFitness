package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
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

/** US-95: the folder's actions menu (review the routines, rename) and its restoration (ADR-40). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class RoutinesFolderMenuTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeRoutineRepository(listOf(routineOf("r1", "Torso A", "a"))).apply {
        folders += RoutineFolder("f1", "Fuerza")
        folderOf["r1"] = "f1"
    }
    private val connectivity = FakeConnectivityObserver()
    private val saved = SavedStateHandle()
    private val reviewed = mutableListOf<String>()

    private fun newModel(handle: SavedStateHandle = saved) =
        RoutinesViewModel(repository, FakeWorkoutSessionRepository(), connectivity, handle)

    private var current by mutableStateOf(newModel())

    private fun show() {
        rule.setContent {
            AppTheme { RoutinesRoot({}, {}, {}, viewModel = current, onOpenFolderReview = { reviewed += it }) }
        }
        rule.waitForIdle()
    }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun theOverflowButton_opensTheFolderActions_withDeleteLast() {
        show()

        click("routines_folder_menu_f1")

        rule.onNodeWithText("Revisar tus rutinas").assertExists()
        rule.onNodeWithText("Renombrar").assertExists()
        rule.onNodeWithText("Borrar carpeta").assertExists()
        rule.onAllNodesWithTag("routines_folder_menu_delete").assertCountEquals(1)
        rule.onNodeWithTag("routines_folder_delete_f1").assertDoesNotExist()
    }

    @Test
    fun revisarConElEntrenador_opensTheReviewOfThatFolder() {
        show()
        click("routines_folder_menu_f1")

        click("routines_folder_menu_review")

        assertEquals(listOf("f1"), reviewed)
    }

    @Test
    fun offline_reviewStaysEnabledAndOpens_renameAndDeleteAreDisabledWithTheReason() {
        show()
        connectivity.state.value = false
        rule.waitForIdle()

        click("routines_folder_menu_f1")

        rule.onNodeWithTag("routines_folder_menu_review").assertIsEnabled()
        rule.onNodeWithTag("routines_folder_menu_rename").assertIsNotEnabled()
        rule.onNodeWithTag("routines_folder_menu_delete").assertIsNotEnabled()
        rule.onNodeWithTag("routines_folder_menu_offline_reason")
            .assertTextEquals("Sin conexión: no se puede renombrar ni borrar la carpeta")
        assertTrue(reviewed.isEmpty())

        click("routines_folder_menu_review")

        assertEquals(listOf("f1"), reviewed)
    }

    @Test
    fun theOpenMenu_survivesRecreation() {
        show()
        click("routines_folder_menu_f1")

        current = newModel()
        rule.waitForIdle()

        rule.onNodeWithTag("routines_folder_menu_review").assertExists()
        rule.onNodeWithTag("routines_folder_menu_delete").assertExists()
        assertTrue(reviewed.isEmpty())
    }
}
