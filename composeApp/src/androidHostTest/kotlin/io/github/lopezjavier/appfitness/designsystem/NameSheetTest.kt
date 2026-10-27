package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** US-56 Name sheet (R-15): focus at once, "Hecho" and the button save once, duplicates are blocked. */
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class NameSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val saved = mutableListOf<String>()
    private var dismissed = 0
    private var open by mutableStateOf(true)
    private var commit by mutableStateOf(SheetCommit.Save)

    private fun show(
        initialName: String = "",
        existing: List<String> = listOf("Torso"),
        initialNameIsOwn: Boolean = true,
    ) {
        rule.setContent {
            AppTheme {
                if (open) {
                    NameSheet(
                        title = "Nueva carpeta",
                        label = "Nombre",
                        itemLabel = "una carpeta",
                        existingNames = existing,
                        initialName = initialName,
                        initialNameIsOwn = initialNameIsOwn,
                        commit = commit,
                        onSave = { saved += it },
                        onDismiss = {
                            dismissed++
                            open = false
                        },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun aSuggestedNameThatIsTaken_blocksGuardar_whenItIsNotTheItemsOwnName() {
        show(initialName = "Torso", existing = listOf("Torso"), initialNameIsOwn = false)
        rule.onNodeWithTag("name_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun fieldHasFocusOnOpen() {
        show()
        rule.onNodeWithTag("name_sheet_input").assertIsFocused()
    }

    @Test
    fun guardar_isDisabledWhileEmpty() {
        show()
        rule.onNodeWithTag("name_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun validName_withKeyboardHecho_savesTheTrimmedNameOnce() {
        show()
        rule.onNodeWithTag("name_sheet_input").performTextInput("  Torso 2 ")
        rule.onNodeWithTag("name_sheet_input").performImeAction()
        rule.waitForIdle()
        assertEquals(listOf("Torso 2"), saved)
    }

    @Test
    fun validName_withTheButton_savesOnce() {
        show()
        rule.onNodeWithTag("name_sheet_input").performTextInput("Piernas")
        rule.onNodeWithTag("name_sheet_commit").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(listOf("Piernas"), saved)
    }

    @Test
    fun keyboardAndButtonInTheSameEdit_saveOnlyOnce() {
        show()
        rule.onNodeWithTag("name_sheet_input").performTextInput("Piernas")
        rule.onNodeWithTag("name_sheet_input").performImeAction()
        rule.onNodeWithTag("name_sheet_commit").performClick()
        rule.waitForIdle()
        assertEquals(listOf("Piernas"), saved)
    }

    @Test
    fun duplicate_ignoringCaseAndSpaces_showsTheErrorAndBlocksSaving() {
        show()
        rule.onNodeWithTag("name_sheet_input").performTextInput("  torso ")
        rule.onNodeWithTag("name_sheet_input_error").assertTextEquals("Ya existe una carpeta con ese nombre")
        rule.onNodeWithTag("name_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("name_sheet_input").performImeAction()
        rule.waitForIdle()
        assertEquals(emptyList(), saved)
    }

    @Test
    fun theError_isNotShownBeforeTheFirstEdit() {
        show(initialName = "Torso", existing = listOf("Torso"))
        rule.onNodeWithTag("name_sheet_input_error").assertDoesNotExist()
    }

    @Test
    fun renamingToTheSameName_isAllowed() {
        show(initialName = "Torso")
        rule.onNodeWithTag("name_sheet_input").performTextReplacement("torso")
        rule.onNodeWithTag("name_sheet_input_error").assertDoesNotExist()
        rule.onNodeWithTag("name_sheet_commit").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(listOf("torso"), saved)
    }

    @Test
    fun commitLabel_defaultsToGuardar_andFollowsTheCommitParameter() {
        show()
        rule.onNodeWithTag("name_sheet_commit").assertTextEquals("Guardar")
        commit = SheetCommit.Add
        rule.waitForIdle()

        rule.onNodeWithTag("name_sheet_commit").assertTextEquals("Añadir")
    }

    @Test
    fun back_doesNotSave() {
        show()
        rule.onNodeWithTag("name_sheet_input").performTextInput("Piernas")
        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(1, dismissed)
        assertEquals(emptyList(), saved)
    }

    @Test
    fun closeButton_doesNotSave() {
        show()
        rule.onNodeWithTag("name_sheet_input").performTextInput("Piernas")
        rule.onNodeWithTag("name_sheet_close").performClick()
        rule.waitForIdle()
        assertEquals(1, dismissed)
        assertEquals(emptyList(), saved)
    }
}
