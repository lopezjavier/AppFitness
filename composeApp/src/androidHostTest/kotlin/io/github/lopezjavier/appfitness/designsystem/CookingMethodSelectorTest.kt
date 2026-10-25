package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [CookingMethodSelector] in isolation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class CookingMethodSelectorTest {
    @get:Rule
    val rule = createComposeRule()

    private val selections = mutableListOf<CookingMethod>()

    private fun show(fontScale: Float = 1f, initial: CookingMethod = CookingMethod.Raw) {
        selections.clear()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme {
                    var selected by remember { mutableStateOf(initial) }
                    CookingMethodSelector(
                        selected = selected,
                        onSelect = {
                            selections += it
                            selected = it
                        },
                        tagPrefix = "cook",
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun showsTheFourSpanishOptions_withRawSelected() {
        show()

        rule.onNodeWithTag("cook_raw").assertTextEquals("Crudo").assertIsSelected()
        rule.onNodeWithTag("cook_griddle").assertTextEquals("A la plancha").assertIsNotSelected()
        rule.onNodeWithTag("cook_air_fryer").assertTextEquals("Air fryer").assertIsNotSelected()
        rule.onNodeWithTag("cook_fried").assertTextEquals("Frito").assertIsNotSelected()
    }

    @Test
    fun options_areRadioButtons() {
        show()

        listOf("raw", "griddle", "air_fryer", "fried").forEach { wire ->
            rule.onNodeWithTag("cook_$wire")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
    }

    @Test
    fun tappingAnOption_selectsOnlyThatOne() {
        show()

        rule.onNodeWithTag("cook_air_fryer").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("cook_air_fryer").assertIsSelected()
        rule.onNodeWithTag("cook_raw").assertIsNotSelected()
        assertEquals(listOf(CookingMethod.AirFryer), selections)
    }

    @Test
    fun tappingTheSelectedOption_doesNotRepeatTheCallback() {
        show(initial = CookingMethod.Fried)

        rule.onNodeWithTag("cook_fried").performClick()

        assertTrue(selections.isEmpty())
    }

    @Test
    fun options_haveA48dpTouchTarget() {
        show()

        val bounds = rule.onNodeWithTag("cook_griddle").getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top >= 48.dp)
    }

    @Test
    fun atFontScale2_theOptionsFormA2x2Grid() {
        show(fontScale = 2f)

        val raw = rule.onNodeWithTag("cook_raw").getUnclippedBoundsInRoot()
        val griddle = rule.onNodeWithTag("cook_griddle").getUnclippedBoundsInRoot()
        val airFryer = rule.onNodeWithTag("cook_air_fryer").getUnclippedBoundsInRoot()
        val fried = rule.onNodeWithTag("cook_fried").getUnclippedBoundsInRoot()
        assertEquals(raw.top, griddle.top)
        assertEquals(airFryer.top, fried.top)
        assertTrue(airFryer.top > raw.top)
        assertEquals(raw.left, airFryer.left)
    }
}
