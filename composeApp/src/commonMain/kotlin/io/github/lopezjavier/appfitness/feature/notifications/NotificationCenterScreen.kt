package io.github.lopezjavier.appfitness.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fact_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flag
import io.github.lopezjavier.appfitness.composeapp.resources.ic_insights
import io.github.lopezjavier.appfitness.composeapp.resources.ic_monitor_weight
import io.github.lopezjavier.appfitness.composeapp.resources.ic_notifications
import io.github.lopezjavier.appfitness.composeapp.resources.ic_straighten
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_block_end_title
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_checkin_answered_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_checkin_answered_title
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_checkin_week
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_circumference_reminder
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_circumference_reminder_due
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_pending_section
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_reminder_checkin
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_reminder_open_block_review
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_reminder_open_checkin
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_reminder_open_circumference
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_reminder_open_week_summary
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_reminder_open_weight
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_this_week_section
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_title
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_week_summary_title
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_week_summary_week
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_weight_reminder
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_weight_reminder_due
import io.github.lopezjavier.appfitness.composeapp.resources.reports_title
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import io.github.lopezjavier.appfitness.domain.model.CheckInDay
import io.github.lopezjavier.appfitness.domain.model.Reminder
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 3
private const val DAYS_TO_SUNDAY = 6
private const val SKELETON_HEADER_FRACTION = 0.3f
private const val SKELETON_TITLE_FRACTION = 0.7f
private const val SKELETON_DETAIL_FRACTION = 0.4f

/**
 * "Centro de avisos" (US-57) on [AppScreen]: "Pendientes" lists the derived reminders (one tap opens where to log
 * it through [onOpenReminder]) or "Todo al día", plus the answered check-in. It has no settings: the switches live
 * in Preferencias, "Avisos".
 */
@Composable
fun NotificationCenterScreen(
    state: NotificationCenterUiState,
    onEvent: (NotificationCenterEvent) -> Unit,
    onOpenReminder: (ReminderKind) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenReports: () -> Unit = {},
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

                is NotificationCenterUiState.Content -> CenterContent(state, onOpenReminder, onOpenReports)
            }
        }
    }
}

@Composable
private fun CenterContent(
    state: NotificationCenterUiState.Content,
    onOpenReminder: (ReminderKind) -> Unit,
    onOpenReports: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "pending_header") {
            ListSectionHeader(
                stringResource(Res.string.notifications_pending_section),
                count = state.reminders.size.takeIf { it > 0 },
            )
        }
        if (state.reminders.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Res.drawable.ic_notifications,
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
        // The stored reports are a destination, not a notice: always last, never counted by the bell.
        item(key = "reports_entry") {
            NavigationRow(
                label = stringResource(Res.string.reports_title),
                onClick = onOpenReports,
                modifier = Modifier.padding(top = Spacing.lg).testTag("notifications_reports"),
            )
        }
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
            headline = if (reminder.days == 0) {
                stringResource(Res.string.notifications_weight_reminder_due)
            } else {
                pluralStringResource(Res.plurals.notifications_weight_reminder, reminder.days, reminder.days)
            }
            label = stringResource(Res.string.notifications_reminder_open_weight)
            tag = "notifications_reminder_weight"
        }

        ReminderKind.Circumference -> {
            headline = if (reminder.weeks == 0) {
                stringResource(Res.string.notifications_circumference_reminder_due)
            } else {
                pluralStringResource(Res.plurals.notifications_circumference_reminder, reminder.weeks, reminder.weeks)
            }
            label = stringResource(Res.string.notifications_reminder_open_circumference)
            tag = "notifications_reminder_circumference"
        }

        ReminderKind.CheckIn -> {
            headline = stringResource(Res.string.notifications_reminder_checkin)
            label = stringResource(Res.string.notifications_reminder_open_checkin)
            tag = "notifications_reminder_checkin"
            state.checkIn?.let { supporting = stringResource(Res.string.notifications_checkin_week, it.weekRange()) }
        }

        ReminderKind.WeekSummary -> {
            headline = stringResource(Res.string.notifications_week_summary_title)
            label = stringResource(Res.string.notifications_reminder_open_week_summary)
            tag = "notification_center_row_week_summary"
            reminder.week?.let { supporting = stringResource(Res.string.notifications_week_summary_week, it) }
        }

        ReminderKind.BlockEnd -> {
            headline = stringResource(Res.string.notifications_block_end_title)
            label = stringResource(Res.string.notifications_reminder_open_block_review)
            tag = "notification_center_row_block_end"
        }
    }
    NavigationRow(
        label = headline,
        onClick = { onOpen(reminder.kind) },
        modifier = modifier.testTag(tag),
        supporting = supporting,
        leading = { RowIcon(reminder.kind.icon()) },
        onClickLabel = label,
    )
}

/** The answered check-in of the open window: reachable and editable, but not a pending reminder (not counted). */
@Composable
private fun AnsweredCheckInRow(window: CheckInDay, onOpen: (ReminderKind) -> Unit, modifier: Modifier = Modifier) {
    NavigationRow(
        label = stringResource(Res.string.notifications_checkin_answered_title),
        onClick = { onOpen(ReminderKind.CheckIn) },
        modifier = modifier.testTag("notifications_checkin_answered"),
        supporting = stringResource(
            Res.string.notifications_checkin_answered_supporting,
            window.answer?.energy ?: 0,
        ),
        onClickLabel = stringResource(Res.string.notifications_reminder_open_checkin),
    )
}

/** One leading icon per notice type (R-74, C-32). */
private fun ReminderKind.icon(): DrawableResource = when (this) {
    ReminderKind.Weight -> Res.drawable.ic_monitor_weight
    ReminderKind.Circumference -> Res.drawable.ic_straighten
    ReminderKind.WeekSummary -> Res.drawable.ic_insights
    ReminderKind.BlockEnd -> Res.drawable.ic_flag
    ReminderKind.CheckIn -> Res.drawable.ic_fact_check
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
private fun Skeleton() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("notifications_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // The layout of the loaded screen (R-31): a section header, then rows with a leading icon and two lines.
        SkeletonBox(Modifier.fillMaxWidth(SKELETON_HEADER_FRACTION).height(Spacing.lg), AppShapes.sm)
        repeat(SKELETON_ROWS) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(Modifier.size(Spacing.xl), AppShapes.full)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SkeletonBox(Modifier.fillMaxWidth(SKELETON_TITLE_FRACTION).height(Spacing.lg), AppShapes.sm)
                    SkeletonBox(Modifier.fillMaxWidth(SKELETON_DETAIL_FRACTION).height(Spacing.md), AppShapes.sm)
                }
            }
        }
    }
}
