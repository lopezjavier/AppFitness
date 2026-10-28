package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ICON_BUTTON_BADGE_TAG
import io.github.lopezjavier.appfitness.domain.model.CheckInDay
import io.github.lopezjavier.appfitness.domain.model.Reminder
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import io.github.lopezjavier.appfitness.feature.diary.DiaryScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryUiState
import io.github.lopezjavier.appfitness.feature.notifications.NotificationCenterScreen
import io.github.lopezjavier.appfitness.feature.notifications.NotificationCenterUiState
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootScreen
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootUiState
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryHost
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryState
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-57: the bell on the three tab roots shows the pending count and opens the center; a reminder opens its entry. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class NotificationNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val diarySlot: DiarySlot = { modifier, _, _, _, _, _ -> DiaryScreen(DiaryUiState.Loading, {}, modifier) }
    private val routinesSlot: RoutinesSlot = { modifier, _, _, _, _ ->
        WorkoutEntryHost(WorkoutEntryState.Routines, {}, {}, modifier, routines = { _, _ -> })
    }
    private val profileSlot: ProfileSlot = { modifier, _, _, _, _, _, _, _ ->
        ProfileRootScreen(ProfileRootUiState.Loaded("Ana", null), {}, modifier)
    }
    private val today = LocalDate(2026, 10, 28)
    private val reminders = NotificationCenterUiState.Content(
        listOf(
            Reminder(ReminderKind.Weight, LocalDate(2026, 10, 24), 4),
            Reminder(ReminderKind.Circumference, LocalDate(2026, 10, 14), 14),
            Reminder(ReminderKind.CheckIn, LocalDate(2026, 10, 25), 0),
        ),
        weightEnabled = true,
        circumferenceEnabled = true,
        today = today,
        isOffline = false,
        checkIn = CheckInDay(LocalDate(2026, 10, 25), LocalDate(2026, 10, 19), answer = null),
    )
    private val centerSlot: NotificationCenterSlot = { modifier, onOpenReminder, onBack ->
        NotificationCenterScreen(reminders, {}, onOpenReminder, onBack, modifier)
    }
    private val weightSlot: WeightSlot = { modifier, openEntry, _, _ ->
        Box(modifier.fillMaxSize().testTag("weight_screen")) { Text("target=$openEntry") }
    }

    private val checkInSlot: CheckInSlot = { modifier, onClose ->
        Box(modifier.fillMaxSize().testTag("checkin_screen")) {
            Text("close", Modifier.testTag("checkin_close").clickable(onClick = onClose))
        }
    }

    private fun open(pending: Int) {
        rule.setContent {
            AppTheme {
                AppScaffold(
                    navController = rememberNavController(),
                    pendingReminders = pending,
                    diary = diarySlot,
                    routines = routinesSlot,
                    profile = profileSlot,
                    notificationCenter = centerSlot,
                    weight = weightSlot,
                    checkIn = checkInSlot,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun diarioBell_showsThePendingCount_andOpensTheCenter() {
        open(pending = 2)

        rule.onNodeWithContentDescription("Centro de avisos, 2 pendientes").assertIsDisplayed()
        tap("diary_action_notifications")

        rule.onNodeWithTag("notifications_screen").assertIsDisplayed()
    }

    @Test
    fun entrenoBell_isPresent_andOpensTheCenter() {
        open(pending = 1)
        tap("nav_tab_workout")

        rule.onNodeWithContentDescription("Centro de avisos, 1 pendiente").assertIsDisplayed()
        tap("workout_action_notifications")

        rule.onNodeWithTag("notifications_screen").assertIsDisplayed()
    }

    @Test
    fun perfilBell_sitsBesideTheGear_andOpensTheCenter() {
        open(pending = 2)
        tap("nav_tab_profile")

        rule.onNodeWithTag("profile_action_0").assertIsDisplayed()
        tap("profile_action_notifications")

        rule.onNodeWithTag("notifications_screen").assertIsDisplayed()
    }

    @Test
    fun noPendingReminders_drawsNoBadge_andNamesTheCenterOnly() {
        open(pending = 0)

        rule.onNode(hasContentDescription("Centro de avisos")).assertIsDisplayed()
        rule.onAllNodesWithTag(ICON_BUTTON_BADGE_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun tappingTheWeightReminder_opensThePesoScreenOnTheWeightEntry() {
        open(pending = 2)
        tap("diary_action_notifications")

        tap("notifications_reminder_weight")

        rule.onNodeWithTag("weight_screen").assertIsDisplayed()
        rule.onNode(androidx.compose.ui.test.hasText("target=Weight")).assertIsDisplayed()
    }

    @Test
    fun tappingTheCircumferenceReminder_opensThePesoScreenOnTheCircumferenceSheet() {
        open(pending = 2)
        tap("diary_action_notifications")

        tap("notifications_reminder_circumference")

        rule.onNode(androidx.compose.ui.test.hasText("target=Circumference")).assertIsDisplayed()
    }

    @Test
    fun tappingTheCheckInReminder_opensTheCheckIn_andClosingReturnsToTheCenter() {
        open(pending = 3)
        tap("diary_action_notifications")

        tap("notifications_reminder_checkin")
        rule.onNodeWithTag("checkin_screen").assertIsDisplayed()
        tap("checkin_close")

        rule.onNodeWithTag("notifications_screen").assertIsDisplayed()
    }

    @Test
    fun back_returnsFromTheCenterToTheTab() {
        open(pending = 2)
        tap("diary_action_notifications")

        tap("notifications_back")

        rule.onNodeWithTag("diary_action_notifications").assertIsDisplayed()
    }
}
