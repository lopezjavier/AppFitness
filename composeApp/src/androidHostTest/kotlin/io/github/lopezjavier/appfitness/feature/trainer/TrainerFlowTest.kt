package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.App
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.RootRoute
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.feature.notifications.LocalNotificationsEntry
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsEntry
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-68, US-69 and US-78: the Trainer root as a plan hub, driven through the real view model over fakes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TrainerFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCoachRepository()
    private val plans = FakeCoachPlanRepository()
    private val blocks = FakeCoachBlockRepository()
    private val connectivity = FakeConnectivityObserver()

    private fun model() = TrainerViewModel(repository, MutableClock(), plans = plans, blocks = blocks)

    private fun show(actions: TrainerActions = TrainerActions()) {
        val model = model()
        rule.setContent {
            AppTheme { TrainerRoute(actions = actions, viewModel = model, connectivity = connectivity) }
        }
        rule.waitForIdle()
    }

    private fun showWithBell(entry: NotificationsEntry) {
        val model = model()
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalNotificationsEntry provides entry) {
                    TrainerRoute(viewModel = model, connectivity = connectivity)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    @Test
    fun fourTabs_inOrder_andTrainerOpensItsRoot() {
        rule.setContent { App(rootRoute = RootRoute.Main) }
        val tags = listOf("nav_tab_diary", "nav_tab_workout", "nav_tab_trainer", "nav_tab_profile")

        val lefts = tags.map { rule.onNodeWithTag(it).getUnclippedBoundsInRoot().left }

        assertEquals(lefts.sorted(), lefts)
        tap("nav_tab_trainer")
        rule.onNodeWithTag("trainer_screen").assertExists()
    }

    @Test
    fun root_hasNoDerivedValuesCardAndNoConfirmButton() {
        show()

        rule.onNodeWithTag("trainer_card").assertDoesNotExist()
        rule.onNodeWithTag("trainer_confirm_button").assertDoesNotExist()
        rule.onNodeWithTag("trainer_edit_activity_button").assertDoesNotExist()
        rule.onNodeWithText("Tus valores").assertDoesNotExist()
    }

    @Test
    fun root_listsProposalsThenPlanThenBlockThenTusDatos() {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        plans.pending = Result.success(calorieProposal())

        show()

        val order = listOf("trainer_calorie_card", "trainer_plan_card", "trainer_create_block", "trainer_your_data")
        val tops = order.map { top(it) }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun tusDatosRow_showsHowManyValuesAreProvisional_andOpensYourData() {
        var opened = 0

        show(TrainerActions(onOpenYourData = { opened++ }))

        rule.onNodeWithTag("trainer_your_data").assertExists()
        rule.onNodeWithText("Tus datos").assertExists()
        rule.onNodeWithText("4 provisionales").assertExists()
        tap("trainer_your_data")
        assertEquals(1, opened)
    }

    @Test
    fun theFreeTextRow_sitsRightUnderTusDatos_andOpensWhatTheCoachKnows() {
        var opened = 0

        show(TrainerActions(onOpenFreeText = { opened++ }))

        rule.onNodeWithText("Lo que el entrenador sabe de ti").assertExists()
        assertTrue(top("trainer_free_text_row") > top("trainer_your_data"))
        tap("trainer_free_text_row")
        assertEquals(1, opened)
    }

    @Test
    fun tusDatosRow_hidesTheCount_whenEverythingIsConfirmed() {
        repository.loaded = Result.success(coachValues(provisional = false))

        show()

        rule.onNodeWithTag("trainer_your_data").assertExists()
        rule.onNodeWithText("4 provisionales").assertDoesNotExist()
    }

    @Test
    fun failedDerivedRead_keepsTheRootAndTheRowWithoutACount() {
        repository.loaded = Result.failure(AppException(AppError.Unknown))

        show()

        rule.onNodeWithTag("trainer_screen").assertExists()
        rule.onNodeWithTag("trainer_your_data").assertExists()
        rule.onNodeWithText("provisionales", substring = true).assertDoesNotExist()
    }

    @Test
    fun bell_isShownWithTheCount_andOpensTheNotificationCenter() {
        var opened = 0
        showWithBell(NotificationsEntry(count = 2, onOpen = { opened++ }))

        tap("trainer_action_notifications")

        assertEquals(1, opened)
    }

    @Test
    fun bell_isHiddenWithoutANotificationsEntry() {
        show()

        rule.onNodeWithTag("trainer_action_notifications").assertDoesNotExist()
    }

    @Test
    fun bell_isStillShownWhenTheDerivedReadFails() {
        repository.loaded = Result.failure(AppException(AppError.Network))
        showWithBell(NotificationsEntry(count = 0, onOpen = {}))

        rule.onNodeWithTag("trainer_action_notifications").assertExists()
    }

    @Test
    fun header_hasNoOverflowAndNoUsoDeIa() {
        showWithBell(NotificationsEntry(count = 0, onOpen = {}))

        rule.onNodeWithTag("trainer_action_notifications").assertExists()
        rule.onNodeWithTag("trainer_overflow").assertDoesNotExist()
        rule.onNodeWithTag("trainer_action_coach_usage").assertDoesNotExist()
        rule.onNodeWithText("Uso de IA").assertDoesNotExist()
    }

    @Test
    fun crearPlan_throughTheShell_opensAntesDeEmpezar() {
        val model = model()
        rule.setContent {
            App(
                rootRoute = RootRoute.Main,
                trainer = { modifier, actions ->
                    TrainerRoute(modifier, actions, viewModel = model, connectivity = connectivity)
                },
            )
        }
        tap("nav_tab_trainer")

        tap("trainer_create_plan")

        rule.onNodeWithTag("before_start_screen").assertExists()
    }

    @Test
    fun tusDatos_throughTheShell_opensYourDataWithoutWritingAnything() {
        val model = model()
        rule.setContent {
            App(
                rootRoute = RootRoute.Main,
                trainer = { modifier, actions ->
                    TrainerRoute(modifier, actions, viewModel = model, connectivity = connectivity)
                },
            )
        }
        tap("nav_tab_trainer")

        tap("trainer_your_data")
        rule.onNodeWithTag("your_data_screen").assertExists()
        assertTrue(repository.confirmCalls.isEmpty())
    }
}
