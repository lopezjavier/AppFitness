package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
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

/** The goal card, the comparison delta row and the metric interpretation row. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class GoalComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    private var sets = 0
    private var changes = 0

    private fun showCard(target: String?, since: String? = null, distance: String? = null) {
        sets = 0
        changes = 0
        rule.setContent {
            AppTheme {
                GoalCard(
                    title = "Objetivo",
                    targetText = target,
                    distanceText = distance,
                    sinceFirstText = since,
                    emptyHint = "Fija un peso objetivo",
                    setLabel = "Fijar objetivo",
                    changeLabel = "Cambiar objetivo",
                    onSet = { sets++ },
                    onChange = { changes++ },
                    testTagPrefix = "g",
                )
            }
        }
    }

    @Test
    fun goalCard_withoutTarget_offersToSetOne() {
        showCard(target = null)

        rule.onNodeWithTag("g_set_button").assertIsDisplayed().performClick()

        assertEquals(1, sets)
        rule.onNodeWithTag("g_change_button").assertDoesNotExist()
    }

    @Test
    fun goalCard_withTarget_showsDistanceSinceFirstAndACompactEditButton() {
        showCard("70,0 kg", "Desde tu primer peso: -2,6 kg", "faltan 4,2 kg")

        rule.onNodeWithText("70,0 kg").assertIsDisplayed()
        rule.onNodeWithTag("g_distance").assertTextEquals("faltan 4,2 kg")
        rule.onNodeWithTag("g_since_first").assertTextEquals("Desde tu primer peso: -2,6 kg")
        rule.onNodeWithTag("g_change_button").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, changes)
    }

    @Test
    fun metricInterpretationRow_showsValueStatusAsTextAndExplanation() {
        rule.setContent {
            AppTheme {
                MetricInterpretationRow("IMC", "23,4", "Normal", StatusTone.Good, "Peso adecuado", testTagPrefix = "m")
            }
        }

        rule.onNodeWithTag("m_status").assertTextContains("Normal", substring = true)
        rule.onNodeWithTag("m_explanation").assertTextEquals("Peso adecuado")
    }
}
