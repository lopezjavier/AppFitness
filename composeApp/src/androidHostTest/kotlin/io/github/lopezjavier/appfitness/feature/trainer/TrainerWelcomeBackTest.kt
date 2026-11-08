package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** US-107: the Trainer welcomes the user back after a break of 14+ days and offers "Bloque nuevo". */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TrainerWelcomeBackTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<TrainerEvent>()

    private fun content(welcomeBack: Boolean, reviewDue: Boolean = false, savedBlock: Boolean = false) =
        TrainerUiState.Content(
            plan = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)),
            block = if (savedBlock) BlockSummary.Saved(coachBlock(saved = true)) else BlockSummary.None,
            blockReviewDue = reviewDue,
            welcomeBack = welcomeBack,
        )

    private fun show(state: TrainerUiState.Content, online: Boolean = true) {
        rule.setContent {
            AppTheme {
                TrainerScreen(
                    state = state,
                    isOnline = flowOf(online),
                    onEvent = { events += it },
                    actions = TrainerActions(),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun afterABreak_theCardShowsTitleLineAndTheNewBlockAction() {
        show(content(welcomeBack = true))

        rule.onNodeWithTag("trainer_welcome_back_card").assertIsDisplayed()
        rule.onNodeWithText("Bienvenido de nuevo").assertIsDisplayed()
        rule.onNodeWithText("Tu primera sesión no subirá las cargas.").assertIsDisplayed()
        rule.onNodeWithTag("trainer_welcome_back_new_block").assertIsDisplayed()
    }

    @Test
    fun afterAShortGap_noCard() {
        show(content(welcomeBack = false))

        rule.onAllNodesWithTag("trainer_welcome_back_card").assertCountEquals(0)
    }

    @Test
    fun onceHandled_theCardGoesAway() {
        var state by mutableStateOf(content(welcomeBack = true))
        rule.setContent {
            AppTheme { TrainerScreen(state, flowOf(true), { events += it }, actions = TrainerActions()) }
        }
        rule.onNodeWithTag("trainer_welcome_back_card").assertIsDisplayed()

        state = content(welcomeBack = false)
        rule.waitForIdle()

        rule.onAllNodesWithTag("trainer_welcome_back_card").assertCountEquals(0)
    }

    @Test
    fun tappingNewBlock_sendsOpenNewBlock() {
        show(content(welcomeBack = true))

        rule.onNodeWithTag("trainer_welcome_back_new_block").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.OpenNewBlock), events)
    }

    @Test
    fun offline_theActionIsDisabledWithTheHint() {
        show(content(welcomeBack = true), online = false)

        rule.onNodeWithTag("trainer_welcome_back_new_block").assertIsNotEnabled()
        rule.onNodeWithText("crear un bloque nuevo", substring = true).assertExists()
    }

    @Test
    fun online_theActionIsEnabled() {
        show(content(welcomeBack = true))

        rule.onNodeWithTag("trainer_welcome_back_new_block").assertIsEnabled()
    }

    @Test
    fun withTheReviewDue_onlyTheReviewCardShows_noWelcomeNorNewBlockEntry() {
        show(content(welcomeBack = true, reviewDue = true, savedBlock = true))

        rule.onNodeWithTag("trainer_review_card").assertIsDisplayed()
        rule.onAllNodesWithTag("trainer_welcome_back_card").assertCountEquals(0)
        rule.onAllNodesWithTag("trainer_new_block").assertCountEquals(0)
        rule.onAllNodesWithTag("trainer_my_training").assertCountEquals(0)
    }

    @Test
    fun withoutTheReview_aSavedBlockOffersNewBlock() {
        show(content(welcomeBack = false, savedBlock = true))

        rule.onNodeWithTag("trainer_new_block").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun recreation_keepsTheWelcomeBackCard() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme { TrainerScreen(content(welcomeBack = true), flowOf(true), {}, actions = TrainerActions()) }
        }
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_welcome_back_card").performScrollTo().assertIsDisplayed()
    }
}
