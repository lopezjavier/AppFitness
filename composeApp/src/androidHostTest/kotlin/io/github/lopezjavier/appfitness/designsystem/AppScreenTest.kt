package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** US-56 same structure on every screen: `AppScreen` (bar, capped content, pinned footer). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AppScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var backs = 0

    private fun show(withFooter: Boolean = true, onBack: (() -> Unit)? = { backs++ }) {
        rule.setContent {
            AppTheme {
                AppScreen(
                    title = "Pantalla",
                    onBack = onBack,
                    footer = if (withFooter) {
                        (
                            {
                                Box(Modifier.fillMaxWidth().height(56.dp).testTag("footer"))
                            }
                            )
                    } else {
                        null
                    },
                    testTagPrefix = "sample",
                ) { padding ->
                    Box(Modifier.fillMaxWidth().height(100.dp).testTag("content_${padding.calculateTopPadding()}"))
                }
            }
        }
    }

    @Test
    fun showsTitleAsHeadingTagsAndBackButton() {
        show()
        rule.onNodeWithTag("sample").assertIsDisplayed()
        rule.onNodeWithTag("sample_title").assertIsDisplayed()
        rule.onNodeWithTag("sample_back").assertContentDescriptionEquals("Volver")
    }

    @Test
    fun backButton_click_callsOnBack() {
        show()
        rule.onNodeWithTag("sample_back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun withoutOnBack_hasNoBackButton() {
        show(onBack = null)
        rule.onNodeWithTag("sample_back").assertDoesNotExist()
    }

    @Test
    fun footer_isPinnedToTheBottomEdge() {
        show()
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        val footer = rule.onNodeWithTag("sample_footer").getUnclippedBoundsInRoot()
        assertTrue(abs((footer.bottom - root.bottom).value) < 1f, "footer ${footer.bottom} root ${root.bottom}")
    }

    @Test
    fun content_sitsBetweenBarAndFooter() {
        show()
        val bar = rule.onNodeWithTag("sample").getUnclippedBoundsInRoot()
        val footer = rule.onNodeWithTag("footer").getUnclippedBoundsInRoot()
        val content = rule.onNodeWithTag("content_0.0.dp").getUnclippedBoundsInRoot()
        assertTrue(content.top >= bar.bottom - 0.5.dp)
        assertTrue(content.bottom <= footer.top + 0.5.dp)
    }

    @Test
    @Config(qualifiers = "w840dp-h640dp-mdpi")
    fun wideWindow_capsContentAndFooterAndCentresThem() {
        show()
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        val content = rule.onNodeWithTag("content_0.0.dp").getUnclippedBoundsInRoot()
        val footer = rule.onNodeWithTag("footer").getUnclippedBoundsInRoot()
        val cap = WindowWidthClassDefaults.FormContentMaxWidth
        assertTrue(content.width <= cap + 0.5.dp, "content ${content.width}")
        assertTrue(footer.width <= cap + 0.5.dp, "footer ${footer.width}")
        assertTrue(abs((content.left - (root.width - content.width) / 2).value) < 1f, "content is centred")
    }

    @Test
    fun withoutFooter_contentFillsDownToTheBottom() {
        show(withFooter = false)
        rule.onNodeWithTag("footer").assertDoesNotExist()
    }

    @Test
    fun header_replacesTheBar_andTheBarTagIsAbsent() {
        rule.setContent {
            AppTheme {
                AppScreen(
                    title = "Pantalla",
                    header = { Box(Modifier.fillMaxWidth().height(48.dp).testTag("custom_header")) },
                    testTagPrefix = "sample",
                ) { Box(Modifier.testTag("body")) }
            }
        }
        rule.onNodeWithTag("custom_header").assertIsDisplayed()
        rule.onNodeWithTag("body").assertExists()
        rule.onNodeWithTag("sample").assertDoesNotExist()
        rule.onNodeWithTag("sample_title").assertDoesNotExist()
    }

    @Test
    fun defaultHeader_keepsTheBar() {
        show()
        rule.onNodeWithTag("sample").assertIsDisplayed()
    }
}
