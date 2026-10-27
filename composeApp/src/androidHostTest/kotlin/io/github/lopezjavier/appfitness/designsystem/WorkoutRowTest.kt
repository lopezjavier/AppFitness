package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.assertContentDescriptionEquals
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

/** R-16: the shared completed-workout row (name, date, duration / volume / sets). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class WorkoutRowTest {
    @get:Rule
    val rule = createComposeRule()

    private var clicks = 0

    private fun show() {
        rule.setContent {
            AppTheme {
                WorkoutRow(
                    name = "Push",
                    dateText = "lun, 24 oct",
                    durationText = "52 min",
                    volumeText = "8.400 kg",
                    setsText = "18 series",
                    onClick = { clicks++ },
                    tag = "workout_row_test",
                    contentDescription = "Push, lun 24 oct, 52 min, 8.400 kg, 18 series",
                )
            }
        }
    }

    @Test
    fun theRow_showsNameDateAndTheThreeFigures() {
        show()

        rule.onNodeWithText("Push", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("lun, 24 oct", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("52 min", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("8.400 kg", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("18 series", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theRow_isAtLeast48dpHigh_andReadsAsOneSentence() {
        show()

        rule.onNodeWithTag("workout_row_test").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("workout_row_test")
            .assertContentDescriptionEquals("Push, lun 24 oct, 52 min, 8.400 kg, 18 series")
    }

    @Test
    fun tappingTheRow_callsOnClick() {
        show()

        rule.onNodeWithTag("workout_row_test").performClick()

        assertEquals(1, clicks)
    }
}
