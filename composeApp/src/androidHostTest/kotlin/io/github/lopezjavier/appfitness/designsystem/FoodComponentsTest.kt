package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [FavouriteToggle] and [PortionSelector] in isolation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class FoodComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    private val portions = listOf(PortionOption("egg", "1 huevo (55 g)"))

    @Test
    fun favouriteToggle_exposesItsStateAndFlipsOnTap() {
        rule.setContent {
            AppTheme {
                var selected by remember { mutableStateOf(false) }
                FavouriteToggle(
                    selected = selected,
                    onToggle = { selected = !selected },
                    contentDescription = "Añadir a favoritos",
                    modifier = Modifier.testTag("star"),
                )
            }
        }

        rule.onNodeWithTag("star").assertIsOff().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("star").assertIsOn()
    }

    @Test
    fun favouriteToggle_hasA48dpTouchTarget_andDisabledIsInert() {
        var taps = 0
        rule.setContent {
            AppTheme {
                FavouriteToggle(
                    selected = false,
                    onToggle = { taps++ },
                    contentDescription = "Añadir a favoritos",
                    modifier = Modifier.testTag("star"),
                    enabled = false,
                )
            }
        }

        val bounds = rule.onNodeWithTag("star").getUnclippedBoundsInRoot()
        assertEquals(48.dp, bounds.right - bounds.left)
        assertEquals(48.dp, bounds.bottom - bounds.top)
        rule.onNodeWithTag("star").assertIsNotEnabled().performClick()
        assertEquals(0, taps)
    }

    @Test
    fun portionSelector_withoutPortions_showsOnlyTheAmountField() {
        rule.setContent {
            AppTheme {
                PortionSelector("100", {}, "Cantidad (g)", "Gramos", emptyList(), null, {}, testTagPrefix = "p")
            }
        }

        rule.onNodeWithTag("p_amount").assertTextContains("100")
        rule.onNodeWithTag("p_portion").assertDoesNotExist()
    }

    @Test
    fun portionSelector_pickingAPortionAndTypingACount_reportsBoth() {
        val amounts = mutableListOf<String>()
        val picked = mutableListOf<String?>()
        rule.setContent {
            AppTheme {
                var portion by remember { mutableStateOf<String?>(null) }
                PortionSelector(
                    amountText = "100",
                    onAmountChange = { amounts += it },
                    amountLabel = "Cantidad (g)",
                    baseLabel = "Gramos",
                    portions = portions,
                    selectedPortionId = portion,
                    onPortionSelect = {
                        picked += it
                        portion = it
                    },
                    testTagPrefix = "p",
                )
            }
        }
        rule.onNodeWithTag("p_portion_option_base").assertIsSelected()

        rule.onNodeWithTag("p_portion_option_egg").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("p_portion_option_egg").assertIsSelected()
        rule.onNodeWithTag("p_amount").performTextReplacement("2")

        assertEquals(listOf<String?>("egg"), picked)
        assertEquals(listOf("2"), amounts)
    }

    @Test
    fun portionSelector_acceptsOneDecimalWithACommaAndRejectsTwo() {
        val amounts = mutableListOf<String>()
        rule.setContent {
            AppTheme {
                PortionSelector("", {
                    amounts += it
                }, "Cantidad (g)", "Gramos", emptyList(), null, {}, testTagPrefix = "p")
            }
        }

        rule.onNodeWithTag("p_amount").performTextReplacement("12,5")
        rule.onNodeWithTag("p_amount").performTextReplacement("12,55")

        assertEquals(listOf("12,5"), amounts)
    }

    @Test
    fun portionSelector_errorText_isShownBelowTheField() {
        rule.setContent {
            AppTheme {
                PortionSelector(
                    "0",
                    {},
                    "Cantidad (g)",
                    "Gramos",
                    emptyList(),
                    null,
                    {},
                    errorText = "Introduce una cantidad entre 0 y 5000",
                    testTagPrefix = "p",
                )
            }
        }

        rule.onNodeWithTag("p_error").assertTextEquals("Introduce una cantidad entre 0 y 5000")
        assertTrue(
            rule.onNodeWithTag("p_error").getUnclippedBoundsInRoot().top >=
                rule.onNodeWithTag("p_amount").getUnclippedBoundsInRoot().bottom,
        )
    }
}
