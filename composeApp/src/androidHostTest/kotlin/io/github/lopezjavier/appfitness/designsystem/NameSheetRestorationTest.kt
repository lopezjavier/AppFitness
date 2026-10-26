package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40 for [NameSheet]: the typed text and the duplicate error survive recreation (no ViewModel involved). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class NameSheetRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun typedDuplicateName_andItsError_surviveRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                NameSheet(
                    title = "Nueva carpeta",
                    label = "Nombre",
                    itemLabel = "una carpeta",
                    existingNames = listOf("Torso"),
                    onSave = {},
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithTag("name_sheet_input").performTextInput("torso")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("name_sheet_input").assertTextEquals("torso")
        rule.onNodeWithTag("name_sheet_input_error").assertTextEquals("Ya existe una carpeta con ese nombre")
        rule.onNodeWithTag("name_sheet_commit").assertIsNotEnabled()
    }
}
