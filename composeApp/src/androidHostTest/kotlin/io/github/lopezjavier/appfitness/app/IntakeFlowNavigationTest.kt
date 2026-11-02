package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartMode
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartRoot
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartViewModel
import io.github.lopezjavier.appfitness.feature.trainer.FakeCoachIntakeRepository
import io.github.lopezjavier.appfitness.feature.trainer.FakeCoachRepository
import io.github.lopezjavier.appfitness.feature.trainer.FakeIntakePromptPreference
import io.github.lopezjavier.appfitness.feature.trainer.IntakeRoot
import io.github.lopezjavier.appfitness.feature.trainer.IntakeViewModel
import io.github.lopezjavier.appfitness.feature.trainer.planKeys
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-81 "Asked once before the first plan": the first "Crear plan" shows the intake, later ones do not. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class IntakeFlowNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val coach = FakeCoachRepository().apply {
        inputs[PlanInputKind.Plan] = Result.success(planKeys())
    }
    private val intake = FakeCoachIntakeRepository()
    private val prompt = FakeIntakePromptPreference()
    private val connectivity = FakeConnectivityObserver()
    private val exercises = FakeExerciseRepository()

    private val trainerSlot: TrainerSlot = { modifier, actions ->
        Column(modifier.fillMaxSize().testTag("trainer_screen")) {
            Box(
                Modifier.size(48.dp).testTag("t_plan")
                    .clickable { actions.onOpenBeforeStart(BeforeStartMode.Plan, null, null) },
            )
        }
    }
    private val beforeStartSlot: BeforeStartSlot = { modifier, onBack, onCreated, onOpenYourData, onFix, onOpenIntake ->
        val model = remember {
            BeforeStartViewModel(coach, intake, prompt, MutableClock(), SavedStateHandle(mapOf("mode" to "plan")))
        }
        BeforeStartRoot(onBack, onCreated, onOpenYourData, onFix, onOpenIntake, modifier, model, connectivity)
    }
    private val intakeSlot: IntakeSlot = { modifier, host ->
        val model = remember { IntakeViewModel(intake, prompt, SavedStateHandle()) }
        IntakeRoot(host, modifier, model, connectivity, exercises)
    }

    private fun show() {
        rule.setContent {
            AppTheme {
                AppScaffold(
                    navController = rememberNavController(),
                    trainer = trainerSlot,
                    beforeStart = beforeStartSlot,
                    intake = intakeSlot,
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
    fun theFirstPlan_showsTheIntake_andAhoraNoGoesOnToAntesDeEmpezar() {
        show()

        tap("t_plan")
        rule.onNodeWithTag("intake_screen").assertExists()
        rule.onNodeWithTag("before_start_screen").assertDoesNotExist()
        tap("intake_skip")

        rule.onNodeWithTag("before_start_screen").assertExists()
        rule.onNodeWithTag("intake_screen").assertDoesNotExist()
        assertTrue(prompt.asked)
    }

    @Test
    fun afterSkipping_theNextPlanGoesStraightToAntesDeEmpezar() {
        prompt.asked = true
        show()

        tap("t_plan")

        rule.onNodeWithTag("before_start_screen").assertExists()
        rule.onNodeWithTag("intake_screen").assertDoesNotExist()
    }

    @Test
    fun savingTheIntake_continuesToAntesDeEmpezar_andRemembersIt() {
        show()
        tap("t_plan")

        tap("intake_years_increment")
        tap("intake_save")

        rule.onNodeWithTag("before_start_screen").assertExists()
        assertEquals(1.0, intake.saveCalls.single().trainingYears)
        assertTrue(prompt.asked)
    }

    @Test
    fun backFromTheFirstPlanIntake_returnsToTheTrainer() {
        show()
        tap("t_plan")

        tap("intake_back")

        rule.onNodeWithTag("trainer_screen").assertExists()
    }
}
