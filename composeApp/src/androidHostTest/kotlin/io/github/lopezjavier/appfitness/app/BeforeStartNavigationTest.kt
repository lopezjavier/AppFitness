package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartEffect
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-78 back-stack: "Antes de empezar" is popped when it hands over, so Back never returns to a stale screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class BeforeStartNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val trainerSlot: TrainerSlot = { modifier, actions ->
        Column(modifier.fillMaxSize().testTag("trainer_screen")) {
            Box(
                Modifier.size(48.dp).testTag("t_plan")
                    .clickable { actions.onOpenBeforeStart(BeforeStartMode.Plan, null, null) },
            )
            Box(
                Modifier.size(48.dp).testTag("t_block")
                    .clickable { actions.onOpenBeforeStart(BeforeStartMode.Block, "plan-4", null) },
            )
        }
    }
    private val beforeStartSlot: BeforeStartSlot = { modifier, onBack, onCreated, onOpenYourData, onFix ->
        AppScreen("Antes de empezar", modifier.testTag("before_start_screen"), onBack = onBack, testTagPrefix = "bs") {
            Column {
                Box(
                    Modifier.size(48.dp).testTag("bs_create").clickable {
                        onCreated(BeforeStartEffect.Created(BeforeStartMode.Plan, null, null))
                    },
                )
                Box(
                    Modifier.size(48.dp).testTag("bs_create_block").clickable {
                        onCreated(BeforeStartEffect.Created(BeforeStartMode.Block, "plan-4", null))
                    },
                )
                Box(Modifier.size(48.dp).testTag("bs_data").clickable(onClick = onOpenYourData))
                Box(Modifier.size(48.dp).testTag("bs_fix").clickable { onFix(PlanFixTarget.PersonalData) })
            }
        }
    }
    private val proposalSlot: PlanProposalSlot = { modifier, onBack, _ ->
        AppScreen("Plan", modifier.testTag("plan_proposal_screen"), onBack = onBack, testTagPrefix = "pp") {}
    }
    private val blockSlot: BlockPreviewSlot = { modifier, planId, _, onBack ->
        AppScreen("Bloque", modifier.testTag("block_preview_screen_$planId"), onBack = onBack, testTagPrefix = "bp") {}
    }
    private val yourDataSlot: YourDataSlot = { modifier, onBack, _ ->
        AppScreen("Tus datos", modifier.testTag("your_data_screen"), onBack = onBack, testTagPrefix = "yd") {}
    }

    private fun show() {
        rule.setContent {
            AppTheme {
                AppScaffold(
                    navController = rememberNavController(),
                    trainer = trainerSlot,
                    beforeStart = beforeStartSlot,
                    planProposal = proposalSlot,
                    blockPreview = blockSlot,
                    yourData = yourDataSlot,
                )
            }
        }
        rule.onNodeWithTag("nav_tab_trainer").performClick()
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun creatingAPlan_opensTheProposal_andBackReturnsToTheTrainerNotToAntesDeEmpezar() {
        show()
        tap("t_plan")
        rule.onNodeWithTag("before_start_screen").assertExists()

        tap("bs_create")
        rule.onNodeWithTag("plan_proposal_screen").assertExists()
        rule.onNodeWithTag("before_start_screen").assertDoesNotExist()
        tap("pp_back")

        rule.onNodeWithTag("trainer_screen").assertExists()
    }

    @Test
    fun creatingABlock_opensThePreviewForThatPlan_andBackReturnsToTheTrainer() {
        show()
        tap("t_block")

        tap("bs_create_block")
        rule.onNodeWithTag("block_preview_screen_plan-4").assertExists()
        tap("bp_back")

        rule.onNodeWithTag("trainer_screen").assertExists()
    }

    @Test
    fun tusDatos_isAPush_soBackReturnsToAntesDeEmpezar() {
        show()
        tap("t_plan")

        tap("bs_data")
        rule.onNodeWithTag("your_data_screen").assertExists()
        tap("yd_back")

        rule.onNodeWithTag("before_start_screen").assertExists()
    }

    @Test
    fun backFromAntesDeEmpezar_returnsToTheTrainer() {
        show()
        tap("t_plan")

        tap("bs_back")

        rule.onNodeWithTag("trainer_screen").assertExists()
    }
}
