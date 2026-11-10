package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-94: "Eliminar bloque" on "Tu plan", its confirmation, and its survival across recreation (ADR-40). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class TrainerRemoveBlockScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val plans = FakeCoachPlanRepository()
    private val blocks = FakeCoachBlockRepository()
    private val connectivity = FakeConnectivityObserver()

    private fun model(saved: SavedStateHandle = SavedStateHandle()): TrainerViewModel {
        plans.current = Result.success(coachPlan(id = "plan-7", status = PlanStatus.Accepted))
        return TrainerViewModel(FakeCoachRepository(), MutableClock(), plans, blocks, FakeCoachWeeksRepository(), saved)
    }

    private fun showRoute(model: TrainerViewModel, details: Boolean = true) {
        rule.setContent {
            AppTheme { TrainerRoutesWithDetails(model, connectivity) }
        }
        rule.waitForIdle()
        if (details) rule.openTrainerDetails()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun savedBlock_showsEliminarBloque_andNotCrearBloque() {
        blocks.current = Result.success(coachBlock(saved = true))
        showRoute(model())

        rule.onNodeWithTag("trainer_remove_block").assertExists()
        rule.onNodeWithText("Eliminar bloque").assertExists()
        rule.onNodeWithTag("trainer_create_block").assertDoesNotExist()
    }

    @Test
    fun withoutABlock_theActionIsAbsent() {
        blocks.current = Result.success(null)
        showRoute(model(), details = false)

        rule.onNodeWithTag("trainer_remove_block").assertDoesNotExist()
        rule.onNodeWithTag("trainer_create_block").assertExists()
    }

    @Test
    fun aProposedBlock_doesNotOfferTheAction() {
        blocks.current = Result.success(coachBlock(saved = false))
        showRoute(model(), details = false)

        rule.onNodeWithTag("trainer_block_card").assertExists()
        rule.onNodeWithTag("trainer_remove_block").assertDoesNotExist()
    }

    @Test
    fun tappingTheAction_asksForConfirmation_withTheCopy() {
        blocks.current = Result.success(coachBlock(saved = true))
        showRoute(model())

        tap("trainer_remove_block")

        rule.onNodeWithText("¿Eliminar el bloque?").assertExists()
        rule.onNodeWithText("Tus rutinas se quedan en Entreno. Tu plan de comida no cambia.").assertExists()
        rule.onNodeWithTag("trainer_remove_block_dialog_confirm").assertExists()
        assertTrue(blocks.removals.isEmpty())
    }

    @Test
    fun cancel_leavesTheBlockAndChangesNothing() {
        blocks.current = Result.success(coachBlock(saved = true))
        showRoute(model())
        tap("trainer_remove_block")

        tap("trainer_remove_block_dialog_cancel")

        rule.onNodeWithText("¿Eliminar el bloque?").assertDoesNotExist()
        rule.onNodeWithTag("trainer_block_card").assertExists()
        assertTrue(blocks.removals.isEmpty())
    }

    @Test
    fun confirm_removesTheBlock_offersCrearBloque_andKeepsThePlanCard() {
        blocks.current = Result.success(coachBlock(saved = true))
        showRoute(model())
        tap("trainer_remove_block")

        tap("trainer_remove_block_dialog_confirm")

        assertEquals(listOf("plan-7"), blocks.removals)
        rule.onNodeWithText("¿Eliminar el bloque?").assertDoesNotExist()
        rule.onNodeWithTag("trainer_remove_block").assertDoesNotExist()
        rule.onNodeWithTag("trainer_weeks_section").assertDoesNotExist()
        rule.onNodeWithTag("trainer_plan_card").assertExists()
        tap("trainer_details_back")
        rule.onNodeWithTag("trainer_create_block").assertExists()
        rule.onNodeWithTag("trainer_block_status").assertDoesNotExist()
    }

    @Test
    fun failure_keepsTheBlock_andClosesTheDialog() {
        blocks.current = Result.success(coachBlock(saved = true))
        blocks.removeResult = Result.failure(IllegalStateException("boom"))
        showRoute(model())
        tap("trainer_remove_block")

        tap("trainer_remove_block_dialog_confirm")

        rule.onNodeWithText("¿Eliminar el bloque?").assertDoesNotExist()
        rule.onNodeWithTag("trainer_block_card").assertExists()
        rule.onNodeWithTag("trainer_remove_block").assertExists()
    }

    @Test
    fun offline_disablesTheAction_andShowsTheHint() {
        blocks.current = Result.success(coachBlock(saved = true))
        connectivity.state.value = false
        showRoute(model())

        rule.onNodeWithTag("trainer_remove_block").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede eliminar el bloque").assertExists()
    }

    @Test
    fun recreation_keepsTheConfirmationOpen_andCancelStillWorks() {
        blocks.current = Result.success(coachBlock(saved = true))
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent {
            val model = remember { model(saved) }
            AppTheme { TrainerRoutesWithDetails(model, connectivity) }
        }
        rule.waitForIdle()
        rule.openTrainerDetails()
        tap("trainer_remove_block")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithText("¿Eliminar el bloque?").assertExists()
        tap("trainer_remove_block_dialog_cancel")
        rule.onNodeWithText("¿Eliminar el bloque?").assertDoesNotExist()
        rule.onNodeWithTag("trainer_block_card").assertExists()
    }
}
