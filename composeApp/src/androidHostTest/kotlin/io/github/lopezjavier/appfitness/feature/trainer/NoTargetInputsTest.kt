package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.feature.profile.PreferencesHubActions
import io.github.lopezjavier.appfitness.feature.profile.PreferencesHubScreen
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * US-69, "The user never enters target numbers" (ADR-78): no screen offers a target weight, and the plan screen has
 * no field at all. The Weight screen is covered in `WeightScreenGoalTest`. Onboarding is not rendered here (it needs
 * its ViewModel); its sources are checked with a text search instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class NoTargetInputsTest {
    @get:Rule
    val rule = createComposeRule()

    private val forbidden = listOf(
        "Peso objetivo",
        "Fijar objetivo",
        "Grasa objetivo",
        "Objetivo de peso",
        "Objetivo de grasa",
        "Perímetro objetivo",
        "Cintura objetivo",
    )

    @Test
    fun onboarding_sourcesDeclareNoTargetWeightField() {
        val dir = listOf(
            File("src/commonMain/kotlin/io/github/lopezjavier/appfitness/feature/onboarding"),
            File("composeApp/src/commonMain/kotlin/io/github/lopezjavier/appfitness/feature/onboarding"),
        ).first { it.isDirectory }
        val sources = dir.listFiles { f -> f.extension == "kt" }.orEmpty().joinToString("\n") { it.readText() }
        assertTrue(sources.isNotEmpty())
        listOf("targetWeight", "target_weight", "targetBodyFat", "targetCircumference").forEach {
            assertFalse("Onboarding must not mention $it", sources.contains(it, ignoreCase = true))
        }
    }

    private fun showAndAssertNoTargetInput(content: @Composable () -> Unit) {
        rule.setContent { AppTheme { content() } }
        rule.waitForIdle()
        forbidden.forEach { rule.onAllNodesWithText(it, substring = true).assertCountEquals(0) }
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun preferencias_hasNoTargetWeight() = showAndAssertNoTargetInput {
        PreferencesHubScreen(onBack = {}, actions = PreferencesHubActions({}, {}, {}))
    }

    @Test
    fun planProposal_hasNoTargetInput() = showAndAssertNoTargetInput {
        PlanProposalScreen(
            state = PlanProposalUiState.Proposal(coachPlan(), coachValues(provisional = false)),
            isOnline = flowOf(true),
            onEvent = {},
            onBack = {},
        )
    }

    @Test
    fun trainer_hasNoTargetInput() = showAndAssertNoTargetInput {
        TrainerScreen(
            state = TrainerUiState.Content(
                coachValues(provisional = false),
                plan = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)),
                calorieChange = calorieChange(),
            ),
            isOnline = flowOf(true),
            onEvent = {},
        )
    }
}
