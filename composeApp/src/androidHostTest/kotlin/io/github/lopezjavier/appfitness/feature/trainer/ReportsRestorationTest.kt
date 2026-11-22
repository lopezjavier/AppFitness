package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.app.AppScaffold
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Reminder
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import io.github.lopezjavier.appfitness.domain.model.ReportItem
import io.github.lopezjavier.appfitness.domain.model.groupReportsByBlock
import io.github.lopezjavier.appfitness.feature.diary.DiaryScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryUiState
import io.github.lopezjavier.appfitness.feature.notifications.NotificationCenterScreen
import io.github.lopezjavier.appfitness.feature.notifications.NotificationCenterUiState
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootScreen
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootUiState
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryHost
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryState
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-144 "Leaving the app": the list keeps its scroll position and the opened report survives recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ReportsRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val items: List<ReportItem> = List(30) { reportItem("r-$it", planId = "plan-${it / 10}") }

    private fun showList(tester: StateRestorationTester) {
        tester.setContent {
            AppTheme {
                ReportsScreen(ReportsUiState.Content(groupReportsByBlock(items)), flowOf(true), {}, {}, {})
            }
        }
    }

    @Test
    fun scrollPosition_isKeptAfterRecreation() {
        val tester = StateRestorationTester(rule)
        showList(tester)
        rule.onNodeWithTag("reports_list").performScrollToNode(hasTestTag("reports_row_r-25"))
        rule.onNodeWithTag("reports_row_r-25").assertIsDisplayed()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("reports_row_r-25").assertIsDisplayed()
        rule.onNodeWithTag("reports_row_r-0").assertIsNotDisplayed()
    }

    @Test
    fun openedReport_isStillOpenAfterRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                AppScaffold(
                    navController = rememberNavController(),
                    pendingReminders = 1,
                    diary = { modifier, _, _, _, _, _, _ -> DiaryScreen(DiaryUiState.Loading, {}, modifier) },
                    routines = { modifier, _, _, _, _, _, _ ->
                        WorkoutEntryHost(WorkoutEntryState.Routines, {}, {}, modifier, routines = { _, _ -> })
                    },
                    profile = { modifier, _ ->
                        ProfileRootScreen(ProfileRootUiState.Loaded("Ana", null), {}, modifier)
                    },
                    notificationCenter = { modifier, onOpenReminder, onOpenReports, onBack ->
                        val reminders = NotificationCenterUiState.Content(
                            listOf(Reminder(ReminderKind.WeekSummary, LocalDate(2026, 10, 25), 0)),
                            today = LocalDate(2026, 10, 28),
                        )
                        NotificationCenterScreen(reminders, {}, onOpenReminder, onBack, modifier, onOpenReports)
                    },
                    reports = { modifier, onOpen, onBack ->
                        ReportsScreen(
                            ReportsUiState.Content(groupReportsByBlock(items)),
                            flowOf(true),
                            {},
                            onOpen,
                            onBack,
                            modifier,
                        )
                    },
                    pastReport = { modifier, reportId, _ ->
                        Column(modifier.fillMaxSize()) {
                            Text("report=$reportId", Modifier.testTag("past_report_id").clickable { })
                        }
                    },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("diary_action_notifications").performClick()
        rule.onNodeWithTag("notifications_reports").performClick()
        rule.onNodeWithTag("reports_list").performScrollToNode(hasTestTag("reports_row_r-25"))
        rule.onNodeWithTag("reports_row_r-25").performClick()
        rule.onNodeWithTag("past_report_id").assertTextEquals("report=r-25")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("past_report_id").assertTextEquals("report=r-25")
    }
}
