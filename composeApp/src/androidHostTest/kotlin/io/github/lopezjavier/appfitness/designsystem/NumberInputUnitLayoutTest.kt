package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R-124: a unit suffix never overlaps its number, at any value length or font scale. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NumberInputUnitLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(value: String, unit: String, fontScale: Float, decimals: Int = 1) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme {
                    Row {
                        NumberInput(
                            value = value,
                            onValueChange = {},
                            label = "Calorías",
                            modifier = Modifier
                                .width(NumberInputDefaults.widthFor(FeatureMetrics.NutrientInputWidth))
                                .height(FeatureMetrics.NutrientInputHeight)
                                .testTag("field"),
                            maxDecimals = decimals,
                            showLabel = false,
                            unit = unit,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun assertNoOverlap(value: String, unit: String, fontScale: Float) {
        show(value, unit, fontScale)
        val field = rule.onNodeWithTag("field").getUnclippedBoundsInRoot()
        val area = rule.onNodeWithTag("number_input_value_area", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val unitNode = rule.onNode(hasText(unit), useUnmergedTree = true)
        val unitBounds = unitNode.getUnclippedBoundsInRoot()
        assertTrue("unit starts after the value area ($area vs $unitBounds)", unitBounds.left >= area.right)
        assertTrue("unit inside the field", unitBounds.right <= field.right && unitBounds.left >= field.left)
        val unitLayout = mutableListOf<TextLayoutResult>()
        unitNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(unitLayout)
        assertEquals("unit is single-line", 1, unitLayout.single().lineCount)
        val valueLayout = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("field").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
            .action?.invoke(valueLayout)
        val density = rule.density
        val glyphsRight = with(density) { valueLayout.single().getBoundingBox(value.length - 1).right.toDp() }
        assertTrue("the whole value fits its area", glyphsRight <= area.right - area.left)
        rule.onNodeWithTag("field").assertHeightIsAtLeast(FeatureMetrics.NutrientInputHeight)
    }

    @Test
    fun kcalWithThreeDigits_atScale1_doesNotOverlap() = assertNoOverlap("346", "kcal", 1f)

    @Test
    fun kcalWithFourDigitsAndDecimal_atScale1_doesNotOverlap() = assertNoOverlap("1234,5", "kcal", 1f)

    @Test
    fun kcalWithFourDigitsAndDecimal_atScale1_3_doesNotOverlap() = assertNoOverlap("1234,5", "kcal", 1.3f)

    @Test
    fun kcalWithFourDigitsAndDecimal_atScale2_doesNotOverlap() = assertNoOverlap("1234,5", "kcal", 2f)

    @Test
    fun gramsAndMilligrams_atScale2_doNotOverlap() {
        assertNoOverlap("18", "g", 2f)
    }

    @Test
    fun milligramsWithDecimal_atScale1_3_doNotOverlap() = assertNoOverlap("123,4", "mg", 1.3f)

    @Test
    fun widthFor_growsWithFontScale_andNeverShrinksBelowTheBase() {
        var at1 = 0.dp
        var at2 = 0.dp
        var atHalf = 0.dp
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                at1 = NumberInputDefaults.widthFor(96.dp)
            }
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                at2 = NumberInputDefaults.widthFor(96.dp)
            }
            CompositionLocalProvider(LocalDensity provides Density(density.density, 0.8f)) {
                atHalf = NumberInputDefaults.widthFor(96.dp)
            }
        }
        rule.waitForIdle()
        assertEquals(96.dp, at1)
        assertEquals(192.dp, at2)
        assertEquals(96.dp, atHalf)
    }
}
