package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
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

/** The set-type badge of a [SetRow] is a button only when the caller handles the tap. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class SetRowTypeClickTest {
    @get:Rule
    val rule = createComposeRule()

    private val values = SetRowValues(1, null, "", "", completed = false)

    @Test
    fun withoutOnTypeClick_theBadgeIsNotClickable() {
        rule.setContent { AppTheme { SetRow(values, {}, {}, {}, testTagPrefix = "row") } }

        rule.onNodeWithTag("row_type").assertHasNoClickAction()
    }

    @Test
    fun withOnTypeClick_theBadgeIsA48DpButton_thatReportsTheTap() {
        var taps = 0
        rule.setContent {
            AppTheme {
                SetRow(
                    values,
                    {},
                    {},
                    {},
                    testTagPrefix = "row",
                    onTypeClick = { taps++ },
                    typeClickLabel = "Serie 1, tipo Normal",
                )
            }
        }

        rule.onNodeWithTag("row_type").assertHasClickAction().assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, taps)
    }
}
