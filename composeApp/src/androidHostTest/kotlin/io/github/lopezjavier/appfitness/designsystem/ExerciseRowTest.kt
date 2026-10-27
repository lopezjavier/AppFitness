package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R-16 / R-18: the shared exercise row (thumbnail + name opens the details, optional supporting line). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ExerciseRowTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tappingTheRow_opensTheExercise() {
        var opened = 0
        rule.setContent {
            AppTheme { ExerciseRow(name = "Press banca", supporting = null, onOpen = { opened++ }, tagPrefix = "row") }
        }

        rule.onNodeWithText("Press banca").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun theRow_showsThePlaceholderThumbnailWhenThereIsNoImage() {
        rule.setContent {
            AppTheme { ExerciseRow(name = "Sentadilla", supporting = null, onOpen = {}, tagPrefix = "row") }
        }

        rule.onNodeWithTag("row_thumbnail_placeholder", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theSupportingLine_isShownUnderTheName() {
        rule.setContent {
            AppTheme { ExerciseRow(name = "Remo", supporting = "3 x 8", onOpen = {}, tagPrefix = "row") }
        }

        rule.onNodeWithText("3 x 8").assertIsDisplayed()
    }

    @Test
    fun theRow_isAtLeast48dpHigh_andCarriesItsTag() {
        rule.setContent { AppTheme { ExerciseRow(name = "Remo", supporting = null, onOpen = {}, tagPrefix = "row") } }

        rule.onNodeWithTag("row").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun theTrailingSlot_isRendered() {
        rule.setContent {
            AppTheme {
                ExerciseRow(name = "Remo", supporting = null, onOpen = {
                }, tagPrefix = "row", trailing = { Text("fin") })
            }
        }

        rule.onNodeWithText("fin").assertIsDisplayed()
    }
}
