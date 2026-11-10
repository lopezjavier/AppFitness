package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.App
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.RootRoute
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the Trainer tab, the plan section and the weeks survive recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per restoration scenario.
class TrainerRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun recreation_keepsTheTrainerTabSelected() {
        val tester = StateRestorationTester(rule)
        tester.setContent { App(rootRoute = RootRoute.Main) }
        rule.onNodeWithTag("nav_tab_trainer").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_trainer").assertIsSelected()
        rule.onNodeWithTag("trainer_screen").assertExists()
    }

    @Test
    fun recreatedViewModel_keepsTheRootAndTheTusDatosRow() {
        val repository = FakeCoachRepository()
        var current by mutableStateOf(TrainerViewModel(repository, MutableClock()))
        val connectivity = FakeConnectivityObserver()
        rule.setContent { AppTheme { TrainerRoute(viewModel = current, connectivity = connectivity) } }
        rule.waitForIdle()

        // A new view model, as after process death: the root has no draft and no activity sheet any more.
        current = TrainerViewModel(repository, MutableClock())
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_your_data").assertExists()
        rule.onNodeWithTag("activity_level_sheet_activity_light").assertDoesNotExist()
        assertTrue(repository.confirmCalls.isEmpty())
    }

    @Test
    fun recreatedViewModel_keepsThePlanSectionAndTheCalorieCard() {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        plans.pending = Result.success(calorieProposal())
        val repository = FakeCoachRepository()
        var current by mutableStateOf(TrainerViewModel(repository, MutableClock(), plans = plans))
        val connectivity = FakeConnectivityObserver()
        rule.setContent { AppTheme { TrainerRoutesWithBoxes(current, connectivity) } }
        rule.waitForIdle()
        rule.onNodeWithTag("trainer_calorie_card").assertExists()

        // A new view model over the same saved state, as after process death: the plan is read again.
        current = TrainerViewModel(repository, MutableClock(), plans = plans)
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_calorie_card").assertExists()
        rule.openTrainerPlan()
        rule.onNodeWithTag("trainer_plan_card").assertExists()
    }

    @Test
    fun recreatedViewModel_reloadsTheWeeksFromTheServer() {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        val blocks = FakeCoachBlockRepository()
        blocks.current = Result.success(coachBlock(saved = true))
        val weeks = FakeCoachWeeksRepository()
        weeks.weeksResult = Result.success(listOf(planWeek(week = 1), planWeek(week = 2)))
        val repository = FakeCoachRepository()
        fun model() = TrainerViewModel(
            repository,
            MutableClock(),
            plans = plans,
            blocks = blocks,
            coachWeeks = weeks,
        )
        var current by mutableStateOf(model())
        val connectivity = FakeConnectivityObserver()
        rule.setContent { AppTheme { TrainerRoutesWithBoxes(current, connectivity) } }
        rule.waitForIdle()
        rule.openTrainerBlock()

        // A new view model over the same saved state, as after process death: nothing is kept only in memory.
        current = model()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_week_2").assertExists()
    }

    @Test
    fun recreation_keepsTheWeekList() {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        val blocks = FakeCoachBlockRepository()
        blocks.current = Result.success(coachBlock(saved = true))
        val weeks = FakeCoachWeeksRepository()
        weeks.weeksResult = Result.success(listOf(planWeek(week = 1), planWeek(week = 2)))
        val model = TrainerViewModel(
            FakeCoachRepository(),
            MutableClock(),
            plans = plans,
            blocks = blocks,
            coachWeeks = weeks,
        )
        val connectivity = FakeConnectivityObserver()
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { TrainerRoutesWithBoxes(model, connectivity) } }
        rule.waitForIdle()
        rule.openTrainerBlock()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_weeks_section").assertExists()
    }

    @Test
    fun recreation_keepsTheWeekCardioAndStepsLines_afterScrollingToThem() {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        val blocks = FakeCoachBlockRepository()
        blocks.current = Result.success(coachBlock(saved = true))
        val weeks = FakeCoachWeeksRepository()
        weeks.weeksResult = Result.success(
            listOf(
                planWeek(week = 1, cardioMinDone = 100, cardioMinTarget = 150, stepsAvg = 7800, stepsTarget = 7000),
                planWeek(week = 2),
            ),
        )
        val model = TrainerViewModel(
            FakeCoachRepository(),
            MutableClock(),
            plans = plans,
            blocks = blocks,
            coachWeeks = weeks,
        )
        val connectivity = FakeConnectivityObserver()
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { TrainerRoutesWithBoxes(model, connectivity) } }
        rule.waitForIdle()
        rule.openTrainerBlock()
        rule.onNodeWithTag("plan_week_cardio_value_1").performScrollTo()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("plan_week_cardio_value_1").assertTextEquals("Cardio de la semana: 100 de 150 min")
        rule.onNodeWithTag("plan_week_steps_value_1").assertTextEquals("Pasos: media 7.800 de 7.000")
    }

    @Test
    fun recreatedViewModel_keepsTheCardioSheetOpenWithTheChangedDraft() {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(
            coachPlan(status = PlanStatus.Accepted, activity = defaultActivity.copy(cardioMinWeek = 120)),
        )
        val repository = FakeCoachRepository()
        val saved = SavedStateHandle()
        var current by mutableStateOf(TrainerViewModel(repository, MutableClock(), plans = plans, savedState = saved))
        val connectivity = FakeConnectivityObserver()
        rule.setContent { AppTheme { TrainerRoutesWithBoxes(current, connectivity) } }
        rule.waitForIdle()
        rule.openTrainerPlan()
        rule.onNodeWithTag("trainer_plan_cardio_row").performClick()
        rule.onNodeWithTag("cardio_sheet_stepper_increment").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("cardio_sheet_stepper_value").assertTextEquals("125 min")

        current = TrainerViewModel(repository, MutableClock(), plans = plans, savedState = saved)
        rule.waitForIdle()

        rule.onNodeWithTag("cardio_sheet_stepper_value").assertTextEquals("125 min")
        rule.onNodeWithTag("cardio_sheet_commit").assertIsEnabled()
    }

    @Test
    fun recreation_keepsTuPlanOpenWithTheTrainerTabSelected() = keepsBoxScreenOpen("trainer_plan_screen") {
        it.onOpenPlan()
    }

    @Test
    fun recreation_keepsTuBloqueOpenWithTheTrainerTabSelected() = keepsBoxScreenOpen("trainer_block_screen") {
        it.onOpenBlockScreen()
    }

    private fun keepsBoxScreenOpen(screenTag: String, open: (TrainerActions) -> Unit) {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            App(
                rootRoute = RootRoute.Main,
                trainer = { modifier, actions ->
                    Box(modifier.fillMaxSize().testTag("trainer_screen").clickable { open(actions) })
                },
            )
        }
        rule.onNodeWithTag("nav_tab_trainer").performClick()
        rule.onNodeWithTag("trainer_screen").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(screenTag).assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag(screenTag).assertExists()
        rule.onNodeWithTag("trainer_screen").assertDoesNotExist()
    }

    @Test
    fun recreatedViewModel_keepsTheRemoveBlockDialogOpenOnTuBloque() {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        val blocks = FakeCoachBlockRepository()
        blocks.current = Result.success(coachBlock(saved = true))
        val repository = FakeCoachRepository()
        val saved = SavedStateHandle()
        fun model() = TrainerViewModel(repository, MutableClock(), plans, blocks, FakeCoachWeeksRepository(), saved)
        var current by mutableStateOf(model())
        val connectivity = FakeConnectivityObserver()
        rule.setContent { AppTheme { TrainerRoutesWithBoxes(current, connectivity) } }
        rule.waitForIdle()
        rule.openTrainerBlock()
        rule.onNodeWithTag("trainer_remove_block").performClick()
        rule.waitForIdle()

        current = model()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_remove_block_dialog_confirm").assertExists()
    }
}
