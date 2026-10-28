package io.github.lopezjavier.appfitness.feature.notifications

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.Reminder
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

private val TODAY = LocalDate(2026, 10, 28)

/** US-57: the notification center renders the pending reminders, the switches and the loading/empty/error states. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class NotificationCenterScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val weight = Reminder(ReminderKind.Weight, LocalDate(2026, 10, 24), days = 4)
    private val circumference = Reminder(ReminderKind.Circumference, LocalDate(2026, 10, 14), days = 14)

    private fun content(
        reminders: List<Reminder> = listOf(weight, circumference),
        weightEnabled: Boolean = true,
        circumferenceEnabled: Boolean = true,
        isOffline: Boolean = false,
    ) = NotificationCenterUiState.Content(reminders, weightEnabled, circumferenceEnabled, TODAY, isOffline)

    private fun show(
        state: NotificationCenterUiState,
        events: MutableList<NotificationCenterEvent> = mutableListOf(),
        opened: MutableList<ReminderKind> = mutableListOf(),
    ) {
        rule.setContent {
            AppTheme { NotificationCenterScreen(state, { events += it }, { opened += it }, onBack = {}) }
        }
    }

    @Test
    fun pendingReminders_areListedWithTheirTextAndDate() {
        show(content())

        rule.onNodeWithTag("notifications_reminder_weight").assertIsDisplayed()
        rule.onNodeWithText("No te has pesado en 4 días").assertIsDisplayed()
        rule.onNodeWithText("No te has medido en 2 semanas").assertIsDisplayed()
        rule.onNodeWithTag("notifications_reminder_weight").assertTextContains("sáb, 24 oct", substring = true)
        rule.onNodeWithTag("notifications_reminder_circumference").assertTextContains("mié, 14 oct", substring = true)
    }

    @Test
    fun tappingAReminder_asksToOpenItsKind() {
        val opened = mutableListOf<ReminderKind>()
        show(content(), opened = opened)

        rule.onNodeWithTag("notifications_reminder_weight").performClick()
        rule.onNodeWithTag("notifications_reminder_circumference").performClick()

        assertEquals(listOf(ReminderKind.Weight, ReminderKind.Circumference), opened)
    }

    @Test
    fun noReminders_showsTheEmptyStateWithoutAction() {
        show(content(reminders = emptyList()))

        rule.onNodeWithTag("notifications_empty").assertIsDisplayed()
        rule.onNodeWithText("Todo al día").assertIsDisplayed()
        rule.onAllNodesWithTag("notifications_reminder_weight").assertCountEquals(0)
    }

    @Test
    fun togglingASwitch_sendsTheNewValueAtOnce() {
        val events = mutableListOf<NotificationCenterEvent>()
        show(content(), events = events)

        rule.onNodeWithTag("notifications_switch_weight").performClick()
        rule.onNodeWithTag("notifications_switch_circumference").performClick()

        assertEquals(
            listOf<NotificationCenterEvent>(
                NotificationCenterEvent.SetEnabled(NotificationKind.Weight, false),
                NotificationCenterEvent.SetEnabled(NotificationKind.Circumference, false),
            ),
            events,
        )
    }

    @Test
    fun switchLabels_followTheGlossary() {
        show(content())

        rule.onNodeWithText("Recordatorio de peso").assertIsDisplayed()
        rule.onNodeWithText("Recordatorio de circunferencias").assertIsDisplayed()
    }

    @Test
    fun offline_disablesTheSwitchesWithTheHint_andKeepsTheListReadable() {
        show(content(isOffline = true))

        rule.onNodeWithTag("notifications_switch_weight").assertIsNotEnabled()
        rule.onNodeWithTag("notifications_switch_circumference").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede cambiar el aviso").assertIsDisplayed()
        rule.onNodeWithTag("notifications_reminder_weight").assertIsDisplayed()
    }

    @Test
    fun loading_showsSkeletonRows() {
        show(NotificationCenterUiState.Loading)

        rule.onNodeWithTag("notifications_loading").assertIsDisplayed()
    }

    @Test
    fun error_offersRetry() {
        val events = mutableListOf<NotificationCenterEvent>()
        show(NotificationCenterUiState.Error, events = events)

        rule.onNodeWithTag("notifications_retry").performClick()

        assertEquals(listOf<NotificationCenterEvent>(NotificationCenterEvent.Retry), events)
    }

    @Test
    fun backArrow_isTaggedAndCalls() {
        var backs = 0
        rule.setContent {
            AppTheme {
                NotificationCenterScreen(content(), {}, {}, onBack = { backs++ })
            }
        }

        rule.onNodeWithTag("notifications_back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun recreation_keepsTheScrolledListAndSwitches() {
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { NotificationCenterScreen(content(), {}, {}, onBack = {}) } }
        rule.onNode(hasScrollAction()).performScrollToIndex(3)

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("notifications_switch_circumference").assertIsDisplayed()
        rule.onNodeWithTag("notifications_reminder_weight").assertIsDisplayed()
    }
}
