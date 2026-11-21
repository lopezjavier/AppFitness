package io.github.lopezjavier.appfitness.feature.notifications

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
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

    private fun content(reminders: List<Reminder> = listOf(weight, circumference)) =
        NotificationCenterUiState.Content(reminders, TODAY)

    private fun show(
        state: NotificationCenterUiState,
        events: MutableList<NotificationCenterEvent> = mutableListOf(),
        opened: MutableList<ReminderKind> = mutableListOf(),
        onOpenReports: () -> Unit = {},
    ) {
        rule.setContent {
            AppTheme {
                NotificationCenterScreen(
                    state,
                    { events += it },
                    { opened += it },
                    onBack = {},
                    onOpenReports = onOpenReports,
                )
            }
        }
    }

    @Test
    fun pendingReminders_areListedWithTheirTextAndDate() {
        show(content())

        rule.onNodeWithTag("notifications_reminder_weight").assertIsDisplayed()
        rule.onNodeWithText("Llevas 4 días sin pesarte").assertIsDisplayed()
        rule.onNodeWithText("Llevas 2 semanas sin medirte").assertIsDisplayed()
        rule.onNodeWithTag("notifications_reminder_weight").assertTextContains("sáb, 24 oct", substring = true)
        rule.onNodeWithTag("notifications_reminder_circumference").assertTextContains("mié, 14 oct", substring = true)
    }

    @Test
    fun weekSummaryNotice_isListedWithItsTitleAndOpensTheSummary() {
        val opened = mutableListOf<ReminderKind>()
        val notice = Reminder(ReminderKind.WeekSummary, LocalDate(2026, 10, 25), days = 0, week = 3)
        show(content(reminders = listOf(notice)), opened = opened)

        rule.onNodeWithText("Tu resumen semanal está listo").assertIsDisplayed()
        rule.onNodeWithTag("notification_center_row_week_summary").assertTextContains("Semana 3", substring = true)
        rule.onNodeWithTag("notification_center_row_week_summary").performClick()

        assertEquals(listOf(ReminderKind.WeekSummary), opened)
    }

    @Test
    fun blockEndNotice_isListedWithItsTitleAndOpensTheReview() {
        val opened = mutableListOf<ReminderKind>()
        val notice = Reminder(ReminderKind.BlockEnd, LocalDate(2026, 10, 26), days = 0)
        show(content(reminders = listOf(notice)), opened = opened)

        rule.onNodeWithText("Tu bloque ha terminado: revísalo").assertIsDisplayed()
        rule.onNodeWithTag("notification_center_row_block_end").assertTextContains("lun, 26 oct", substring = true)
        rule.onNodeWithTag("notification_center_row_block_end").performClick()

        assertEquals(listOf(ReminderKind.BlockEnd), opened)
    }

    @Test
    fun blockEndNotice_recreation_keepsTheRow() {
        val tester = StateRestorationTester(rule)
        val notice = Reminder(ReminderKind.BlockEnd, LocalDate(2026, 10, 26), days = 0)
        tester.setContent {
            AppTheme { NotificationCenterScreen(content(reminders = listOf(notice)), {}, {}, onBack = {}) }
        }
        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("notification_center_row_block_end").assertIsDisplayed()
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
    fun informesEntry_isListedBelowTheNotices_andOpensTheReports() {
        var opened = 0
        show(content(), onOpenReports = { opened++ })

        rule.onNodeWithTag("notifications_reports").assertIsDisplayed().assertTextContains("Informes")
        rule.onNodeWithTag("notifications_reports").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun informesEntry_isListedAlsoWhenNothingIsPending() {
        show(content(reminders = emptyList()))

        rule.onNodeWithTag("notifications_empty").assertIsDisplayed()
        rule.onNodeWithTag("notifications_reports").assertIsDisplayed()
    }

    @Test
    fun informesEntry_isNotShownWhileLoadingOrOnError() {
        show(NotificationCenterUiState.Loading)

        rule.onAllNodesWithTag("notifications_reports").assertCountEquals(0)
    }

    @Test
    fun noReminders_showsTheEmptyStateWithoutAction() {
        show(content(reminders = emptyList()))

        rule.onNodeWithTag("notifications_empty").assertIsDisplayed()
        rule.onNodeWithText("Todo al día").assertIsDisplayed()
        rule.onAllNodesWithTag("notifications_reminder_weight").assertCountEquals(0)
    }

    @Test
    fun theCenterHasNoSwitches_theyLiveInPreferencias() {
        show(content())

        listOf("weight", "circumference").forEach {
            rule.onAllNodesWithTag("notifications_switch_$it").assertCountEquals(0)
        }
        rule.onAllNodesWithText("Recordatorio de peso").assertCountEquals(0)
    }

    /** US-142: with every notice kind pending, the center lists the four rows and nothing of the weekly check-in. */
    @Test
    fun noCheckInRowOrSection_withEveryKindPending() {
        val all = listOf(
            weight,
            circumference,
            Reminder(ReminderKind.WeekSummary, LocalDate(2026, 10, 25), days = 0, week = 3),
            Reminder(ReminderKind.BlockEnd, LocalDate(2026, 10, 26), days = 0),
        )
        show(content(reminders = all))

        listOf(
            "notifications_reminder_weight",
            "notifications_reminder_circumference",
            "notification_center_row_week_summary",
            "notification_center_row_block_end",
        ).forEach { rule.onNodeWithTag(it, useUnmergedTree = true).assertExists() }
        listOf(
            "notifications_reminder_checkin",
            "notifications_checkin_answered",
            "notifications_section_this_week",
        ).forEach { rule.onAllNodesWithTag(it, useUnmergedTree = true).assertCountEquals(0) }
        rule.onAllNodesWithText("Hacer el check-in semanal", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Check-in semanal", substring = true, ignoreCase = true).assertCountEquals(0)
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
    fun recreation_keepsTheReminderList() {
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { NotificationCenterScreen(content(), {}, {}, onBack = {}) } }
        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("notifications_reminder_circumference").assertIsDisplayed()
        rule.onNodeWithTag("notifications_reminder_weight").assertIsDisplayed()
    }
}
