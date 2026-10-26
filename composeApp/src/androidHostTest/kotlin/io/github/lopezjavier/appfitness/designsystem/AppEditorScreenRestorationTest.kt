package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * ADR-40 for `AppEditorScreen`: the discard dialog and the "already closed" latch survive recreation.
 * The template holds no ViewModel (the caller owns the edited state), so the ViewModel half of the restoration
 * rule is N/A here; the `AppMessages` queue lives in the shell and is deliberately not restored (a transient message
 * may be lost on process death).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AppEditorScreenRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private var closes = 0

    private fun show(tester: StateRestorationTester, saved: Boolean = false) {
        tester.setContent {
            AppTheme {
                AppEditorScreen(
                    title = "Editar",
                    isDirty = true,
                    isSaving = false,
                    isOnline = true,
                    saved = saved,
                    onSave = {},
                    onClose = { closes++ },
                    testTagPrefix = "ed",
                ) {}
            }
        }
    }

    @Test
    fun openDiscardDialog_staysOpenAfterRecreation() {
        val tester = StateRestorationTester(rule)
        show(tester)
        rule.onNodeWithTag("ed_back").performClick()
        rule.onNodeWithTag("ed_discard_dialog").assertIsDisplayed()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("ed_discard_dialog").assertIsDisplayed()
        assertEquals(0, closes)
    }

    @Test
    fun closedAfterSave_doesNotCloseAgainAfterRecreation() {
        val tester = StateRestorationTester(rule)
        show(tester, saved = true)
        rule.waitForIdle()
        assertEquals(1, closes)

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertEquals(1, closes)
    }
}
