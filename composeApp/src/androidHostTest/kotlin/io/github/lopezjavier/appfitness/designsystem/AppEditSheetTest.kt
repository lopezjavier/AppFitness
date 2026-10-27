package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** US-56 Edit archetype: Guardar/Listo, commit state and the closing paths that never apply (R-10). */
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AppEditSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private var commits = 0
    private var dismissed = 0
    private var open by mutableStateOf(true)

    private fun showEdit(
        commit: SheetCommit = SheetCommit.Save,
        commitEnabled: Boolean = true,
        saving: Boolean = false,
        commitHint: String? = null,
        secondary: (@Composable () -> Unit)? = null,
    ) {
        rule.setContent {
            AppTheme {
                if (open) {
                    AppEditSheet(
                        title = "Editar comida",
                        commit = commit,
                        onCommit = { commits++ },
                        onDismiss = {
                            dismissed++
                            open = false
                        },
                        commitEnabled = commitEnabled,
                        saving = saving,
                        commitHint = commitHint,
                        secondaryAction = secondary,
                        testTagPrefix = "sheet",
                    ) { Text("Contenido") }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun editSave_showsTheTitleAsAHeadingAndGuardar() {
        showEdit()
        rule.onNodeWithText("Editar comida").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithTag("sheet_commit").assertIsDisplayed()
        rule.onNodeWithText("Guardar").assertIsDisplayed()
    }

    @Test
    fun editFinish_showsFinalizar_andCommitsOnTap() {
        showEdit(commit = SheetCommit.Finish)
        rule.onNodeWithText("Finalizar").assertIsDisplayed()
        rule.onNodeWithText("Guardar").assertDoesNotExist()

        rule.onNodeWithTag("sheet_commit").performClick()

        assertEquals(1, commits)
    }

    @Test
    fun editApply_showsListo() {
        showEdit(commit = SheetCommit.Apply)
        rule.onNodeWithText("Listo").assertIsDisplayed()
        rule.onNodeWithText("Guardar").assertDoesNotExist()
    }

    @Test
    fun closeButton_isDescribedCerrarAndAtLeast48dp() {
        showEdit()
        rule.onNodeWithTag("sheet_close").assertContentDescriptionEquals("Cerrar")
        val bounds = rule.onNodeWithTag("sheet_close").getUnclippedBoundsInRoot()
        assertTrue(bounds.width >= 47.5.dp && bounds.height >= 47.5.dp, "X is $bounds")
    }

    @Test
    fun commit_isDisabledWhenNotEnabledOrSaving() {
        showEdit(commitEnabled = false)
        rule.onNodeWithTag("sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun commit_isDisabledWhileSaving() {
        showEdit(saving = true)
        rule.onNodeWithTag("sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun commit_callsOnCommitOnceAndNotOnDismiss() {
        showEdit()
        rule.onNodeWithTag("sheet_commit").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(1, commits)
        assertEquals(0, dismissed)
    }

    @Test
    fun commitHint_isShownUnderTheButton() {
        showEdit(commitEnabled = false, commitHint = offlineHint("guardar"))
        rule.onNodeWithTag("sheet_commit_hint").assertIsDisplayed()
        rule.onNodeWithText("Sin conexión: no se puede guardar").assertIsDisplayed()
    }

    @Test
    fun closeButton_dismissesWithoutCommitting() {
        showEdit()
        rule.onNodeWithTag("sheet_close").performClick()
        rule.waitForIdle()
        assertEquals(1, dismissed)
        assertEquals(0, commits)
    }

    @Test
    fun back_dismissesWithoutCommitting() {
        showEdit()
        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(1, dismissed)
        assertEquals(0, commits)
    }

    @Test
    fun scrim_isNotATapTarget_andTheSheetStaysOpen() {
        showEdit()
        rule.onNodeWithContentDescription("Close Sheet", ignoreCase = true).assertDoesNotExist()
        assertEquals(0, dismissed)
    }

    @Test
    fun swipeDown_doesNotDismiss() {
        showEdit()
        rule.onNodeWithTag("sheet").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(0, dismissed)
        rule.onNodeWithTag("sheet").assertIsDisplayed()
    }

    @Test
    fun secondaryAction_rendersUnderTheCommitButton() {
        showEdit(secondary = { Text("Eliminar", Modifier.testTag("sheet_secondary")) })
        val commitBottom = rule.onNodeWithTag("sheet_commit").getUnclippedBoundsInRoot().bottom
        val secondaryTop = rule.onNodeWithTag("sheet_secondary").getUnclippedBoundsInRoot().top
        assertTrue(secondaryTop >= commitBottom)
    }

    @Test
    fun secondaryAction_isAbsentByDefault() {
        showEdit()
        rule.onNodeWithTag("sheet_secondary").assertDoesNotExist()
    }
}
