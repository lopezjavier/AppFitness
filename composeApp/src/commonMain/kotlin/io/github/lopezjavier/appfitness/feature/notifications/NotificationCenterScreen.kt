package io.github.lopezjavier.appfitness.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.ic_notifications
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_checkin_answered_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_checkin_answered_title
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_checkin_week
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_circumference_reminder
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_offline_action
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_pending_section
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_reminder_checkin
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_reminder_open_checkin
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_reminder_open_circumference
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_reminder_open_weight
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_settings_section
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_switch_checkin
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_switch_checkin_helper
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_switch_circumference
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_switch_circumference_helper
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_switch_weight
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_switch_weight_helper
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_this_week_section
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_title
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_weight_reminder
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppSwitchRow
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import io.github.lopezjavier.appfitness.domain.model.CheckInDay
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.Reminder
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 3
private const val DAYS_TO_SUNDAY = 6

/**
 * "Centro de avisos" (US-57) on [AppScreen]: "Pendientes" lists the derived reminders (one tap opens where to log
 * it through [onOpenReminder]) or "Todo al día", and "Avisos" holds one switch per reminder kind that applies on
 * toggle (R-42). Offline the switches are inert with the standard hint while the list stays readable.
 */
@Composable
fun NotificationCenterScreen(
    state: NotificationCenterUiState,
    onEvent: (NotificationCenterEvent) -> Unit,
    onOpenReminder: (ReminderKind) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(
        title = stringResource(Res.string.notifications_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = "notifications",
    ) {
        Box(Modifier.fillMaxSize().testTag("notifications_screen")) {
            when (state) {
                NotificationCenterUiState.Loading -> Skeleton()

                NotificationCenterUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.root_error_message),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
                            onEvent(NotificationCenterEvent.Retry)
                        },
                        actionTestTag = "notifications_retry",
                        testTagPrefix = "notifications_error",
                    )
                }

                is NotificationCenterUiState.Content -> CenterContent(state, onEvent, onOpenReminder)
            }
        }
    }
}

@Composable
private fun CenterContent(
    state: NotificationCenterUiState.Content,
    onEvent: (NotificationCenterEvent) -> Unit,
    onOpenReminder: (ReminderKind) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "pending_header") {
            ListSectionHeader(stringResource(Res.string.notifications_pending_section), count = state.reminders.size)
        }
        if (state.reminders.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    message = stringResource(Res.string.notifications_empty_message),
                    title = stringResource(Res.string.notifications_empty_title),
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg).testTag("notifications_empty"),
                )
            }
        } else {
            items(state.reminders, key = { it.kind.name }) { reminder ->
                ReminderRow(reminder, state, onOpenReminder, appAnimateItem())
            }
        }
        val answered = state.checkIn?.takeIf { it.answer != null }
        if (answered != null) {
            item(key = "this_week_header") {
                ListSectionHeader(
                    stringResource(Res.string.notifications_this_week_section),
                    Modifier.padding(top = Spacing.lg).testTag("notifications_section_this_week"),
                )
            }
            item(key = "checkin_answered") { AnsweredCheckInRow(answered, onOpenReminder, appAnimateItem()) }
        }
        item(key = "settings_header") {
            ListSectionHeader(
                stringResource(Res.string.notifications_settings_section),
                Modifier.padding(top = Spacing.lg),
            )
        }
        item(key = "settings") { SwitchCard(state, onEvent) }
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    state: NotificationCenterUiState.Content,
    onOpen: (ReminderKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val headline: String
    val label: String
    val tag: String
    var supporting = AppDateFormat.list(reminder.dueDate, state.today)
    when (reminder.kind) {
        ReminderKind.Weight -> {
            headline = stringResource(Res.string.notifications_weight_reminder, reminder.days)
            label = stringResource(Res.string.notifications_reminder_open_weight)
            tag = "notifications_reminder_weight"
        }

        ReminderKind.Circumference -> {
            headline = stringResource(Res.string.notifications_circumference_reminder, reminder.weeks)
            label = stringResource(Res.string.notifications_reminder_open_circumference)
            tag = "notifications_reminder_circumference"
        }

        ReminderKind.CheckIn -> {
            headline = stringResource(Res.string.notifications_reminder_checkin)
            label = stringResource(Res.string.notifications_reminder_open_checkin)
            tag = "notifications_reminder_checkin"
            state.checkIn?.let { supporting = stringResource(Res.string.notifications_checkin_week, it.weekRange()) }
        }
    }
    AppListRow(
        headline = headline,
        supporting = supporting,
        modifier = modifier.testTag(tag),
        onClick = { onOpen(reminder.kind) },
        onClickLabel = label,
        leading = { RowIcon(Res.drawable.ic_notifications) },
        trailing = { RowIcon(Res.drawable.ic_chevron_right) },
    )
}

/** The answered check-in of the open window: reachable and editable, but not a pending reminder (not counted). */
@Composable
private fun AnsweredCheckInRow(window: CheckInDay, onOpen: (ReminderKind) -> Unit, modifier: Modifier = Modifier) {
    AppListRow(
        headline = stringResource(Res.string.notifications_checkin_answered_title),
        supporting = stringResource(
            Res.string.notifications_checkin_answered_supporting,
            window.answer?.energy ?: 0,
        ),
        modifier = modifier.testTag("notifications_checkin_answered"),
        onClick = { onOpen(ReminderKind.CheckIn) },
        onClickLabel = stringResource(Res.string.notifications_reminder_open_checkin),
        trailing = { RowIcon(Res.drawable.ic_chevron_right) },
    )
}

@Composable
private fun RowIcon(icon: DrawableResource) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = AppTheme.colors.textSecondary,
        modifier = Modifier.size(Spacing.xl),
    )
}

/** "9 al 15 nov": the Monday-Sunday week of the check-in. */
private fun CheckInDay.weekRange(): String =
    AppDateFormat.dayRange(weekStart, weekStart.plus(DAYS_TO_SUNDAY, DateTimeUnit.DAY))

@Composable
private fun SwitchCard(state: NotificationCenterUiState.Content, onEvent: (NotificationCenterEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AppSwitchRow(
                    label = stringResource(Res.string.notifications_switch_weight),
                    helper = stringResource(Res.string.notifications_switch_weight_helper),
                    checked = state.weightEnabled,
                    onCheckedChange = { onEvent(NotificationCenterEvent.SetEnabled(NotificationKind.Weight, it)) },
                    modifier = Modifier.testTag("notifications_switch_weight"),
                    enabled = !state.isOffline,
                )
                AppSwitchRow(
                    label = stringResource(Res.string.notifications_switch_circumference),
                    helper = stringResource(Res.string.notifications_switch_circumference_helper),
                    checked = state.circumferenceEnabled,
                    onCheckedChange = {
                        onEvent(NotificationCenterEvent.SetEnabled(NotificationKind.Circumference, it))
                    },
                    modifier = Modifier.testTag("notifications_switch_circumference"),
                    enabled = !state.isOffline,
                )
                AppSwitchRow(
                    label = stringResource(Res.string.notifications_switch_checkin),
                    helper = stringResource(Res.string.notifications_switch_checkin_helper),
                    checked = state.checkInEnabled,
                    onCheckedChange = {
                        onEvent(NotificationCenterEvent.SetEnabled(NotificationKind.WeeklyCheckIn, it))
                    },
                    modifier = Modifier.testTag("notifications_switch_checkin"),
                    enabled = !state.isOffline,
                )
            }
        }
        if (state.isOffline) OfflineHint(stringResource(Res.string.notifications_offline_action))
    }
}

@Composable
private fun Skeleton() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("notifications_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(SKELETON_ROWS) {
            SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget), AppShapes.sm)
        }
    }
}
