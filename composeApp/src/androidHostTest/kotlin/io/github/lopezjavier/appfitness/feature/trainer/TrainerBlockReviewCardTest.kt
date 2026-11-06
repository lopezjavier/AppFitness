package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-101: the Trainer tab offers "Revisión del bloque" after the block ends, and never starts anything by itself. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TrainerBlockReviewCardTest {
    @get:Rule
    val rule = createComposeRule()

    private var opened = 0

    private fun show(due: Boolean) {
        rule.setContent {
            AppTheme {
                TrainerScreen(
                    state = TrainerUiState.Content(
                        plan = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)),
                        blockReviewDue = due,
                    ),
                    isOnline = flowOf(true),
                    onEvent = {},
                    actions = TrainerActions(onOpenBlockReview = { opened++ }),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun beforeTheBlockEnds_noCard_andCreateBlockStays() {
        show(due = false)

        rule.onAllNodesWithTag("trainer_review_card").assertCountEquals(0)
        rule.onNodeWithTag("trainer_create_block").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun afterTheBlockEnds_theCardOpensTheReview_andCreateBlockIsHidden() {
        show(due = true)

        rule.onNodeWithText("Tu bloque ha terminado: revísalo.").assertIsDisplayed()
        rule.onAllNodesWithTag("trainer_create_block").assertCountEquals(0)
        assertEquals(0, opened)
        rule.onNodeWithTag("trainer_review_card").performClick()

        assertEquals(1, opened)
    }
}
