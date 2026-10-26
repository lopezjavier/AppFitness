package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** US-56 offline: a failed load shows the calm "Sin conexión" block and reloads by itself on reconnect (R-30). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class OfflineStateTest {
    @get:Rule
    val rule = createComposeRule()

    private val connectivity = MutableStateFlow(false)
    private var reloads = 0

    private fun show() {
        rule.setContent { AppTheme { OfflineState(isOnline = connectivity, onReload = { reloads++ }) } }
    }

    @Test
    fun showsTitleMessageAndNoButton() {
        show()
        rule.onNodeWithTag("offline_state").assertIsDisplayed()
        rule.onNodeWithText("Sin conexión").assertIsDisplayed()
        rule.onNodeWithText("Conéctate para ver estos datos.").assertIsDisplayed()
        rule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun isAPoliteLiveRegion() {
        show()
        rule.onNodeWithTag("offline_state").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
    }

    @Test
    fun reconnecting_reloadsExactlyOnce() {
        show()
        rule.waitForIdle()
        connectivity.value = true
        rule.waitForIdle()
        assertEquals(1, reloads)
    }

    @Test
    fun stillOffline_doesNotReload() {
        show()
        rule.waitForIdle()
        assertEquals(0, reloads)
    }

    @Test
    fun eachReconnection_reloadsAgain() {
        show()
        rule.waitForIdle()
        repeat(2) {
            connectivity.value = true
            rule.waitForIdle()
            connectivity.value = false
            rule.waitForIdle()
        }
        assertEquals(2, reloads)
    }
}
