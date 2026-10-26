package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.designsystem.AppMessages
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The catalog samples of the T-118 templates (US-56). No ViewModel is involved, so the ViewModel half of the
 * restoration rule (ADR-40) is N/A; the typed sample text is `rememberSaveable` and checked with a recreation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class CatalogTemplatesTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var messages: AppMessages

    private fun scrollTo(tag: String) {
        rule.onNodeWithTag("catalog_screen").performScrollToNode(hasTestTag(tag))
    }

    private fun setCatalog(tester: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            AppTheme {
                val scope = rememberCoroutineScope()
                messages = remember { AppMessages(scope) }
                CompositionLocalProvider(LocalAppMessages provides messages) {
                    ComponentCatalogScreen(Modifier.fillMaxSize())
                }
            }
        }
        if (tester != null) tester.setContent(content) else rule.setContent(content)
    }

    @Test
    fun editorSample_saveClosesItAndShowsAMessage() {
        setCatalog()
        scrollTo("catalog_editor_sample")
        rule.onNodeWithTag("catalog_editor_field").performTextInput("Pollo")
        rule.onNodeWithTag("catalog_editor_save").performClick()
        rule.waitForIdle()
        assertEquals("Editor cerrado", messages.current.value?.text)
        rule.onNodeWithTag("catalog_editor_save").assertIsNotEnabled()
    }

    @Test
    fun editorSample_dirtyBackAsksToDiscard() {
        setCatalog()
        scrollTo("catalog_editor_sample")
        rule.onNodeWithTag("catalog_editor_field").performTextInput("Pollo")
        rule.onNodeWithTag("catalog_editor_back").performClick()
        rule.onNodeWithTag("catalog_editor_discard_dialog").assertIsDisplayed()
    }

    @Test
    fun editorSample_offlineChipShowsTheHint() {
        setCatalog()
        scrollTo("catalog_editor_offline_chip")
        rule.onNodeWithTag("catalog_editor_offline_chip").performClick()
        scrollTo("catalog_editor_sample")
        rule.onNodeWithTag("offline_hint").assertTextEquals("Sin conexión: no se puede guardar")
    }

    @Test
    fun editorSample_typedTextSurvivesRecreation() {
        val tester = StateRestorationTester(rule)
        setCatalog(tester)
        scrollTo("catalog_editor_sample")
        rule.onNodeWithTag("catalog_editor_field").performTextInput("Pollo")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        scrollTo("catalog_editor_sample")
        rule.onNodeWithTag("catalog_editor_field").assertTextContains("Pollo")
    }

    @Test
    fun messagesSample_queuesTheSampleMessage() {
        setCatalog()
        scrollTo("catalog_messages_show")
        rule.onNodeWithTag("catalog_messages_show").performClick()
        assertEquals("Guardado", messages.current.value?.text)
    }

    @Test
    fun offlineStateSample_reconnectingReloadsOnce() {
        setCatalog()
        scrollTo("catalog_offline_state_flip")
        rule.onNodeWithTag("catalog_offline_state_flip").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("catalog_offline_state_reloads").assertTextEquals("Recargas: 1")
    }

    @Test
    fun actionSheetSample_opens() {
        setCatalog()
        scrollTo("catalog_action_sheet_open")
        rule.onNodeWithTag("catalog_action_sheet_open").performClick()
        rule.onNodeWithTag("action_sheet").assertIsDisplayed()
    }
}
