package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-69: the Trainer tab shows the plan created elsewhere once the user comes back to it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TrainerPlanAfterCreateTest {
    @get:Rule
    val rule = createComposeRule()

    private val plans = FakeCoachPlanRepository()
    private lateinit var owner: TestOwner

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun showTrainer() {
        owner = TestOwner().also { it.registry.currentState = Lifecycle.State.RESUMED }
        val model = TrainerViewModel(FakeCoachRepository(), MutableClock(), SavedStateHandle(), plans = plans)
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                AppTheme { TrainerRoute(viewModel = model, connectivity = FakeConnectivityObserver()) }
            }
        }
        rule.waitForIdle()
    }

    private fun leaveAndComeBack() {
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.waitForIdle()
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.waitForIdle()
    }

    @Test
    fun aPlanCreatedWhileAway_showsThePendingCardAndCrearBloqueOnReturn() {
        showTrainer()
        rule.onNodeWithTag("trainer_create_plan").assertExists()

        plans.current = Result.success(coachPlan())
        leaveAndComeBack()

        rule.onNodeWithTag("trainer_create_plan").assertDoesNotExist()
        rule.onNodeWithText("Plan pendiente").assertExists()
        rule.onNodeWithTag("trainer_plan_card").assertExists()
    }

    @Test
    fun aPlanAcceptedWhileAway_showsTheActiveCardAndCrearBloqueOnReturn() {
        showTrainer()

        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        leaveAndComeBack()

        rule.onNodeWithTag("trainer_create_plan").assertDoesNotExist()
        rule.onNodeWithText("Plan activo").assertExists()
        rule.onNodeWithTag("trainer_create_block").assertExists()
    }

    @Test
    fun aPlanAlreadyThereAtStart_showsTheActiveCardAndNoCrearPlan() {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        showTrainer()

        rule.onNodeWithTag("trainer_create_plan").assertDoesNotExist()
        rule.onNodeWithText("Plan activo").assertExists()
        rule.onNodeWithTag("trainer_create_block").assertExists()
    }
}
