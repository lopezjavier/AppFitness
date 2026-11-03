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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartMode
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartRoot
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartViewModel
import io.github.lopezjavier.appfitness.feature.trainer.FakeCoachIntakeRepository
import io.github.lopezjavier.appfitness.feature.trainer.FakeCoachRepository
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

/** US-81 "Nothing opens on its own": the first "Crear plan" goes straight to "Antes de empezar". */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class IntakeFlowNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val coach = FakeCoachRepository().apply {
        inputs[PlanInputKind.Plan] = Result.success(planKeys())
    }
    private val intake = FakeCoachIntakeRepository()
    private val connectivity = FakeConnectivityObserver()

    private val trainerSlot: TrainerSlot = { modifier, actions ->
        Column(modifier.fillMaxSize().testTag("trainer_screen")) {
            Box(
                Modifier.size(48.dp).testTag("t_plan")
                    .clickable { actions.onOpenBeforeStart(BeforeStartMode.Plan, null, null) },
            )
        }
    }
    private val beforeStartSlot: BeforeStartSlot = { modifier, onBack, onCreated, onOpenYourData, onFix, _ ->
        val model = remember {
            BeforeStartViewModel(
                coach,
                intake,
                MutableClock(),
                FakeRoutineRepository(),
                SavedStateHandle(mapOf("mode" to "plan")),
            )
        }
        BeforeStartRoot(onBack, onCreated, onOpenYourData, onFix, modifier, model, connectivity)
    }
    private val intakeSlot: IntakeSlot = { modifier, host ->
        val model = remember { IntakeViewModel(intake, SavedStateHandle()) }
        IntakeRoot(host, modifier, model, connectivity)
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
    fun theFirstPlan_opensAntesDeEmpezarDirectly_andNothingElseOpens() {
        show()

        tap("t_plan")

        rule.onNodeWithTag("before_start_screen").assertExists()
        rule.onNodeWithTag("intake_screen").assertDoesNotExist()
        rule.onNodeWithTag("intake_years_sheet").assertDoesNotExist()
        assertTrue(intake.saveCalls.isEmpty())
    }

    @Test
    fun theFreeTextRow_opensParaConocerte_andSavingReturnsToAntesDeEmpezarWithTheText() {
        show()
        tap("t_plan")

        tap("before_start_row_free_text")
        rule.onNodeWithTag("intake_screen").assertExists()
        rule.onNodeWithTag("intake_free_text").performTextInput("Rodilla operada")
        tap("intake_save")

        rule.onNodeWithTag("before_start_screen").assertExists()
        rule.onNodeWithTag("intake_screen").assertDoesNotExist()
        assertEquals(listOf<IntakeUpdate>(IntakeUpdate.FreeText("Rodilla operada")), intake.saveCalls)
    }

    @Test
    fun backFromParaConocerte_returnsToAntesDeEmpezar() {
        show()
        tap("t_plan")
        tap("before_start_row_free_text")

        tap("intake_back")

        rule.onNodeWithTag("before_start_screen").assertExists()
    }
}
