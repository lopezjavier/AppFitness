package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-98: the Trainer tab offers "Resumen semanal" only when the latest finished week has a summary. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TrainerWeekSummaryRowTest {
    @get:Rule
    val rule = createComposeRule()

    private var opened = 0

    private fun show(hasSummary: Boolean) {
        rule.setContent {
            AppTheme {
                TrainerScreen(
                    state = TrainerUiState.Content(hasWeekSummary = hasSummary),
                    isOnline = flowOf(true),
                    onEvent = {},
                    actions = TrainerActions(onOpenWeekSummary = { opened++ }),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun withASummary_theRowIsShownAndOpensIt() {
        show(hasSummary = true)

        rule.onNodeWithTag("trainer_box_week_summary").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("trainer_box_week_summary").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun withoutASummary_noRow() {
        show(hasSummary = false)

        rule.onAllNodesWithTag("trainer_box_week_summary").assertCountEquals(0)
    }
}
