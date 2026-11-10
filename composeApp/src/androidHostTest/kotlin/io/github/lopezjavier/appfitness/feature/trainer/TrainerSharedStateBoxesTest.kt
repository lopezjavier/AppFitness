package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.lopezjavier.appfitness.app.App
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.RootRoute
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T-270: the root and its box screens share one view model, so a box opens with the loaded content and no refetch. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TrainerSharedStateBoxesTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCoachRepository()

    private fun loadedModel(): TrainerViewModel {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(id = "plan-7", status = PlanStatus.Accepted))
        val blocks = FakeCoachBlockRepository()
        blocks.current = Result.success(coachBlock(saved = true))
        return TrainerViewModel(repository, MutableClock(), plans, blocks, FakeCoachWeeksRepository())
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private val owner = TestOwner().also { it.registry.currentState = Lifecycle.State.RESUMED }

    private fun showLoaded(): TrainerViewModel {
        val model = loadedModel()
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                AppTheme { TrainerRoutesWithBoxes(model, FakeConnectivityObserver()) }
            }
        }
        rule.waitForIdle()
        return model
    }

    @Test
    fun tuPlan_opensWithItsContentAndNoSkeleton_whenTheRootIsLoaded() {
        showLoaded()

        rule.openTrainerPlan()

        rule.onNodeWithTag("trainer_plan_screen").assertIsDisplayed()
        rule.onNodeWithTag("trainer_plan_loading").assertDoesNotExist()
    }

    @Test
    fun tuBloque_opensWithItsContentAndNoSkeleton_whenTheRootIsLoaded() {
        showLoaded()

        rule.openTrainerBlock()

        rule.onNodeWithTag("trainer_block_screen").assertIsDisplayed()
        rule.onNodeWithTag("trainer_block_loading").assertDoesNotExist()
    }

    @Test
    fun openingABox_doesNotFetchAgain() {
        showLoaded()
        val afterRoot = repository.loadCalls

        rule.openTrainerPlan()

        assertEquals(afterRoot, repository.loadCalls)
        rule.onNodeWithTag("trainer_plan_loading").assertDoesNotExist()
    }

    @Test
    fun aRealResumeOnABox_stillRefreshes() {
        showLoaded()
        rule.openTrainerBlock()
        val afterOpen = repository.loadCalls

        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.waitForIdle()
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.waitForIdle()

        assertEquals(afterOpen + 1, repository.loadCalls)
    }

    @Test
    fun recreation_keepsTuBloqueAndItsRemoveConfirmation() {
        val model = loadedModel()
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { TrainerRoutesWithBoxes(model, FakeConnectivityObserver()) } }
        rule.waitForIdle()
        rule.openTrainerBlock()
        rule.onNodeWithTag("trainer_remove_block").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_block_screen").assertIsDisplayed()
        rule.onNodeWithTag("trainer_remove_block_dialog_confirm").assertIsDisplayed()
    }

    @Test
    fun theBoxScreen_receivesTheOwnerOfTheTrainerTabEntry() {
        var tabOwner: ViewModelStoreOwner? = null
        var boxOwner: ViewModelStoreOwner? = null
        rule.setContent {
            App(
                rootRoute = RootRoute.Main,
                trainer = { modifier, actions ->
                    tabOwner = LocalViewModelStoreOwner.current
                    Box(modifier.fillMaxSize().testTag("trainer_screen").clickable { actions.onOpenPlan() })
                },
                trainerBox = { modifier, _, _, trainerOwner, _ ->
                    boxOwner = trainerOwner
                    Box(modifier.fillMaxSize().testTag("trainer_plan_screen"))
                },
            )
        }
        rule.onNodeWithTag("nav_tab_trainer").performClick()
        rule.onNodeWithTag("trainer_screen").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_plan_screen").assertIsDisplayed()
        assertNotNull(boxOwner)
        assertSame(tabOwner, boxOwner)
    }

    @Test
    fun aSilentRefreshOnABox_keepsItsContent() {
        val model = showLoaded()
        rule.openTrainerBlock()

        model.onEvent(TrainerEvent.Refresh)
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_block_screen").assertIsDisplayed()
        rule.onNodeWithTag("trainer_block_loading").assertDoesNotExist()
    }
}
