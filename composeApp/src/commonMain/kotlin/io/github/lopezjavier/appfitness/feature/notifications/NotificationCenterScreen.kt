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
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_circumference_reminder
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_offline_action
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_pending_section
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_reminder_open_circumference
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_reminder_open_weight
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_settings_section
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_switch_circumference
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_switch_circumference_helper
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_switch_weight
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_switch_weight_helper
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
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.Reminder
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 3

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
    val isWeight = reminder.kind == ReminderKind.Weight
    val headline = if (isWeight) {
        stringResource(Res.string.notifications_weight_reminder, reminder.days)
    } else {
        stringResource(Res.string.notifications_circumference_reminder, reminder.weeks)
    }
    val label = stringResource(
        if (isWeight) {
            Res.string.notifications_reminder_open_weight
        } else {
            Res.string.notifications_reminder_open_circumference
        },
    )
    val tag = if (isWeight) "notifications_reminder_weight" else "notifications_reminder_circumference"
    AppListRow(
        headline = headline,
        supporting = AppDateFormat.list(reminder.dueDate, state.today),
        modifier = modifier.testTag(tag),
        onClick = { onOpen(reminder.kind) },
        onClickLabel = label,
        leading = {
            Icon(
                painter = painterResource(Res.drawable.ic_notifications),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Spacing.xl),
            )
        },
        trailing = {
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Spacing.xl),
            )
        },
    )
}

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
