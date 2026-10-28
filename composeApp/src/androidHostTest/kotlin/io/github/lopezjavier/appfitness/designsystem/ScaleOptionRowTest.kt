package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.lopezjavier.appfitness.domain.model.DiscomfortLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val LONG_LABEL = "Hinchazón, hormigueo o entumecimiento"

/** [ScaleOptionRow]: radio semantics, wrapping labels and the discomfort colour stops (R-45, R-41). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h800dp-xxhdpi")
class ScaleOptionRowTest {
    @get:Rule
    val rule = createComposeRule()

    private var clicks = 0

    private fun show(selected: Boolean = false, fontScale: Float = 1f) {
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    ScaleOptionRow(
                        label = LONG_LABEL,
                        color = AppTheme.colors.discomfortScale(DiscomfortLevel.SwellingOrNumbness),
                        selected = selected,
                        onClick = { clicks++ },
                        testTag = "row",
                        contentDescription = "$LONG_LABEL, nivel 4 de 5",
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun theRow_isARadioButtonOfAtLeast48dp() {
        show()

        val node = rule.onNodeWithTag("row")
        assertEquals(Role.RadioButton, node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        node.assertHeightIsAtLeast(Spacing.minTouchTarget)
    }

    @Test
    fun tapping_reportsTheClick() {
        show()

        rule.onNodeWithTag("row").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun selected_isExposedAsSelected() {
        show(selected = true)

        rule.onNodeWithTag("row").assertIsSelected()
    }

    @Test
    fun aLongLabelAt130PercentFontScale_wrapsWithoutEllipsis() {
        show(fontScale = 1.3f)

        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("row", useUnmergedTree = true).fetchSemanticsNode().children.forEach { child ->
            child.config.getOrNull(SemanticsActions.GetTextLayoutResult)
                ?.action?.invoke(layouts)
        }
        assertFalse("label must not overflow", layouts.single().hasVisualOverflow)
        assertFalse(layouts.single().isLineEllipsized(layouts.single().lineCount - 1))
    }

    @Test
    fun discomfortScale_hasFiveDistinctStopsAndNeverTheLimePrimary() {
        val colors = KineticDarkColors
        val stops = DiscomfortLevel.entries.map { colors.discomfortScale(it) }

        assertEquals(5, stops.toSet().size)
        stops.forEach { assertNotEquals(colors.neonLime, it) }
    }
}
