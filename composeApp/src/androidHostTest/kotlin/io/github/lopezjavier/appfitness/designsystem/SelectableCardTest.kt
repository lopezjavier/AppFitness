package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class SelectableCardTest {
    @get:Rule
    val rule = createComposeRule()

    private var clicks = 0

    private fun show(selected: Boolean) {
        clicks = 0
        rule.setContent {
            AppTheme {
                Column(Modifier.selectableGroup()) {
                    SelectableCard(
                        title = "Moderado",
                        description = "Ejercicio 3–5 días por semana",
                        selected = selected,
                        onClick = { clicks++ },
                        modifier = Modifier.testTag("card"),
                    )
                }
            }
        }
    }

    @Test
    fun selectedCard_isSelectedRadioButton() {
        show(selected = true)
        rule.onNodeWithTag("card").assertIsSelected()
        rule.onNodeWithTag("card").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun unselectedCard_isNotSelected_andTapCallsBack() {
        show(selected = false)
        rule.onNodeWithTag("card").assertIsNotSelected().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun card_isAtLeast48dpTall_andShowsTitleAndDescription() {
        show(selected = false)
        rule.onNodeWithTag("card").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("card").assertTextContains("Moderado", substring = true)
        rule.onNodeWithTag("card").assertTextContains("Ejercicio 3–5 días por semana", substring = true)
    }

    @Test
    fun selectedBorder_isThickerAndLime_thanTheUnselectedOne() {
        val selected = SelectableCardDefaults.colors(true, KineticDarkColors)
        val unselected = SelectableCardDefaults.colors(false, KineticDarkColors)
        assertEquals(KineticDarkColors.neonLime, selected.border)
        assertEquals(KineticDarkColors.borderSubtle, unselected.border)
        assertEquals(AppBorder.focus, SelectableCardDefaults.borderWidth(true))
        assertEquals(AppBorder.thin, SelectableCardDefaults.borderWidth(false))
    }
}
