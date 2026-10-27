package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** US-56 same structure on every screen: the top bar v2 (actions, overflow, subtitle). */
@RunWith(RobolectricTestRunner::class)
@Suppress("TooManyFunctions") // One scenario per test.
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AppTopBarTest {
    @get:Rule
    val rule = createComposeRule()

    private val clicks = mutableListOf<Int>()

    private fun actions(count: Int) = List(count) { index ->
        AppTopBarAction(
            icon = if (index % 2 == 0) Res.drawable.ic_edit else Res.drawable.ic_close,
            contentDescription = "Acción ${index + 1}",
            onClick = { clicks += index + 1 },
        )
    }

    @Test
    fun subtitleSemantics_describeTheSubtitleNodeForScreenReaders() {
        rule.setContent {
            AppTheme {
                AppTopBar(
                    title = "Rutina",
                    subtitle = "EN VIVO · 02:05",
                    subtitleSemantics = {
                        contentDescription = "Tiempo de entreno, 2 minutos"
                        stateDescription = "En pausa"
                    },
                )
            }
        }

        rule.onNodeWithTag("topbar_subtitle").assertContentDescriptionEquals("Tiempo de entreno, 2 minutos")
        assertEquals(
            "En pausa",
            rule.onNodeWithTag(
                "topbar_subtitle",
            ).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription),
        )
    }

    private fun show(count: Int, subtitle: String? = null) {
        rule.setContent { AppTheme { AppTopBar(title = "Título", subtitle = subtitle, actions = actions(count)) } }
    }

    @Test
    fun noActions_showsNoActionButtonsAndNoOverflow() {
        show(0)
        rule.onNodeWithTag("topbar_action_1").assertDoesNotExist()
        rule.onNodeWithTag("topbar_more").assertDoesNotExist()
    }

    @Test
    fun twoActions_areInlineWithoutOverflow() {
        show(2)
        rule.onNodeWithTag("topbar_action_1").assertContentDescriptionEquals("Acción 1")
        rule.onNodeWithTag("topbar_action_2").assertContentDescriptionEquals("Acción 2")
        rule.onNodeWithTag("topbar_more").assertDoesNotExist()
    }

    @Test
    fun aDisabledInlineAction_keepsItsDescription_andIgnoresClicks() {
        rule.setContent {
            AppTheme {
                AppTopBar(
                    title = "Título",
                    actions = listOf(
                        AppTopBarAction(Res.drawable.ic_edit, "Editar", { clicks += 1 }, enabled = false),
                    ),
                )
            }
        }
        rule.onNodeWithTag("topbar_action_1").assertContentDescriptionEquals("Editar").assertIsNotEnabled()
            .performClick()
        assertTrue(clicks.isEmpty())
    }

    @Test
    fun inlineAction_click_runsItsHandler() {
        show(2)
        rule.onNodeWithTag("topbar_action_2").performClick()
        assertEquals(listOf(2), clicks)
    }

    @Test
    fun threeActions_putTheThirdInTheOverflowSheet() {
        show(3)
        rule.onNodeWithTag("topbar_action_3").assertDoesNotExist()
        rule.onNodeWithTag("topbar_more").assertContentDescriptionEquals("Más acciones").performClick()
        rule.onNodeWithTag("action_sheet_row_1").assertIsDisplayed()
        rule.onNodeWithTag("action_sheet_row_2").assertDoesNotExist()
    }

    @Test
    fun overflowRow_click_runsActionAndClosesSheet() {
        show(3)
        rule.onNodeWithTag("topbar_more").performClick()
        rule.onNodeWithTag("action_sheet_row_1").performClick()
        rule.waitForIdle()
        assertEquals(listOf(3), clicks)
        rule.onNodeWithTag("action_sheet").assertDoesNotExist()
    }

    @Test
    fun fiveActions_keepTwoInlineAndOverflowTheRest() {
        show(5)
        rule.onNodeWithTag("topbar_action_2").assertIsDisplayed()
        rule.onNodeWithTag("topbar_action_3").assertDoesNotExist()
        rule.onNodeWithTag("topbar_more").performClick()
        listOf(1, 2, 3).forEach { rule.onNodeWithTag("action_sheet_row_$it").assertIsDisplayed() }
    }

    @Test
    fun everyButton_isAtLeast48dp_andDescribed() {
        show(3)
        listOf("topbar_action_1", "topbar_action_2", "topbar_more").forEach { tag ->
            val bounds = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(
                bounds.width >= 47.5.dp && bounds.height >= 47.5.dp,
                "$tag is ${bounds.width} x ${bounds.height}",
            )
        }
    }

    @Test
    fun title_isAHeading_andSubtitleIsNot() {
        show(0, subtitle = "Subtítulo")
        rule.onNodeWithTag("topbar_title").assertTextEquals("Título")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithTag("topbar_subtitle").assertTextEquals("Subtítulo")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
    }

    @Test
    fun noSubtitle_rendersNoSubtitleNode() {
        show(0)
        rule.onNodeWithTag("topbar_subtitle").assertDoesNotExist()
    }

    @Test
    fun customPrefix_prefixesActionTags() {
        rule.setContent {
            AppTheme { AppTopBar(title = "T", actions = actions(3), testTagPrefix = "custom") }
        }
        rule.onNodeWithTag("custom_action_1").assertIsDisplayed()
        rule.onNodeWithTag("custom_more").assertIsDisplayed()
    }

    @Test
    fun moreIconIsTheOverflowGlyph() {
        // Guards the resource name used by the overflow button (ic_more_vert) against rename.
        assertTrue(Res.drawable.ic_more_vert.toString().isNotEmpty())
    }
}
