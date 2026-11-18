package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R-124 / T-303: at font scale 2.0 and 360 dp the nutrient row wraps its label and keeps every target usable. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NutrientFieldLargeFontLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun longIndentedLabel_atScale2_wrapsAndKeepsInputAndHelpUsable() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AppTheme {
                    Column(Modifier.testTag("row")) {
                        NutrientField(
                            spec = NutrientSpec(
                                label = "de los cuales azúcares",
                                macro = null,
                                tag = "nutrient",
                                unit = "g",
                                indent = true,
                            ),
                            value = "12,5",
                            error = null,
                            onChange = {},
                            help = { HelpButton("Ayuda", "Azúcares", "Texto", "nutrient_help") },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("row").assertHeightIsAtLeast(56.dp)
        rule.onNodeWithTag("nutrient").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("nutrient_help").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("nutrient_label", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val label = rule.onNodeWithTag("nutrient_label", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val help = rule.onNodeWithTag("nutrient_help").getUnclippedBoundsInRoot()
        val input = rule.onNodeWithTag("nutrient").getUnclippedBoundsInRoot()
        assertTrue(
            "label wraps to two or more lines (label=$label help=$help input=$input size=${layouts.single().size})",
            layouts.single().lineCount >= 2,
        )
        assertTrue("label ends before the help icon", label.right <= help.left)
        assertTrue("help ends before the input", help.right <= input.left)
    }
}
