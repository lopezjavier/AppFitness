package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [WeekdaySelector] in isolation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class WeekdaySelectorTest {
    @get:Rule
    val rule = createComposeRule()

    private val toggles = mutableListOf<Int>()

    private fun show(
        initial: Set<Int> = setOf(1, 3),
        disabled: Map<Int, String> = emptyMap(),
        fontScale: Float = 1f,
        width: Int? = null,
        prefix: String = "weekday_selector",
    ) {
        toggles.clear()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme {
                    @Suppress("MutableStateAutoboxing") // A set of ISO days; fine for a test host.
                    var selected by remember { mutableStateOf(initial) }
                    WeekdaySelector(
                        selected = selected,
                        disabled = disabled,
                        onToggle = {
                            toggles += it
                            selected = if (it in selected) selected - it else selected + it
                        },
                        modifier = if (width != null) Modifier.width(width.dp) else Modifier,
                        testTagPrefix = prefix,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun day(n: Int, prefix: String = "weekday_selector") = rule.onNodeWithTag("${prefix}_day_$n")

    @Test
    fun showsTheSevenInitialsInIsoOrder() {
        show()
        val letters = (1..7).map { n ->
            day(n).assertTextEquals(listOf("L", "M", "X", "J", "V", "S", "D")[n - 1])
        }
        assertEquals(7, letters.size)
    }

    @Test
    fun selectedDaysAreOnAndTheOthersOff() {
        show(initial = setOf(1, 3))
        day(1).assertIsOn()
        day(3).assertIsOn()
        day(2).assertIsOff()
    }

    @Test
    fun tappingADay_reportsItsIsoNumberAndFlipsIt() {
        show(initial = setOf(1))
        day(5).performClick()
        assertEquals(listOf(5), toggles)
        day(5).assertIsOn()
    }

    @Test
    fun everyChipIsACheckboxWithItsFullNameAsDescription() {
        show()
        day(3).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        day(3).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Miércoles")))
    }

    @Test
    fun aDisabledChip_isInertAndNamesTheComidaInItsState() {
        show(initial = setOf(2), disabled = mapOf(1 to "Desayuno A"))
        day(1).assertIsNotEnabled()
        day(1).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ya asignado a Desayuno A"),
        )
        day(1).performClick()
        assertTrue(toggles.isEmpty())
    }

    @Test
    fun chipsAreAtLeast48dpAndNeverWrap() {
        show(width = 200)
        val first = day(1).getUnclippedBoundsInRoot()
        assertTrue(first.height >= 48.dp)
        assertTrue(first.width >= 48.dp)
        val last = day(7)
        last.performScrollTo()
        assertEquals(first.top, last.getUnclippedBoundsInRoot().top)
    }

    @Test
    fun atALargeFontScale_everyDayStaysReachableOnOneRow() {
        show(fontScale = 2f, width = 300)
        day(7).performScrollTo()
        day(7).performClick()
        assertEquals(listOf(7), toggles)
        assertEquals(day(1).getUnclippedBoundsInRoot().height, day(7).getUnclippedBoundsInRoot().height)
    }

    @Test
    fun theTagPrefixIsPropagatedToTheChips() {
        show(prefix = "assign_default")
        day(2, "assign_default").performClick()
        assertEquals(listOf(2), toggles)
    }
}
