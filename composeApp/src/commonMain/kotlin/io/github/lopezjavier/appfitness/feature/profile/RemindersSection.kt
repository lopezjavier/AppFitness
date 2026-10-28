package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_checkin_day_helper
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_checkin_day_row
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_checkin
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_checkin_helper
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_circumference
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_circumference_helper
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_weight
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_weight_helper
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppSwitchRow
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.weekdayName
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val DAYS_PER_ISO_WEEK = 7

/**
 * "Avisos" section of Preferencias (US-57, US-59): the three reminder switches and the "Día del check-in" row (value =
 * the weekday, lowercase) opening a picker of the seven weekdays. Switches and day are drafts (R-10): a tap applies to
 * the draft (the picker closes, R-12) and the editor's "Guardar" saves them.
 */
@Composable
internal fun RemindersSection(form: PreferencesForm, onEvent: (PreferencesEvent) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val weekday = form.checkInWeekday
    val dayLabel = stringResource(Res.string.preferences_checkin_day_row)
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AppSwitchRow(
                label = stringResource(Res.string.preferences_reminders_weight),
                helper = stringResource(Res.string.preferences_reminders_weight_helper),
                checked = form.weightReminder,
                onCheckedChange = { onEvent(PreferencesEvent.SetReminderEnabled(NotificationKind.Weight, it)) },
                modifier = Modifier.testTag("preferences_reminders_switch_weight"),
            )
            AppSwitchRow(
                label = stringResource(Res.string.preferences_reminders_circumference),
                helper = stringResource(Res.string.preferences_reminders_circumference_helper),
                checked = form.circumferenceReminder,
                onCheckedChange = { onEvent(PreferencesEvent.SetReminderEnabled(NotificationKind.Circumference, it)) },
                modifier = Modifier.testTag("preferences_reminders_switch_circumference"),
            )
            AppSwitchRow(
                label = stringResource(Res.string.preferences_reminders_checkin),
                helper = stringResource(Res.string.preferences_reminders_checkin_helper),
                checked = form.checkInReminder,
                onCheckedChange = { onEvent(PreferencesEvent.SetReminderEnabled(NotificationKind.WeeklyCheckIn, it)) },
                modifier = Modifier.testTag("preferences_reminders_switch_checkin"),
            )
            NavigationRow(
                label = dayLabel,
                onClick = { picking = true },
                modifier = Modifier.testTag("preferences_checkin_weekday_row"),
                value = weekdayName(weekday).lowercase(),
            )
            Text(
                text = stringResource(Res.string.preferences_checkin_day_helper),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
    if (picking) {
        WeekdayPicker(dayLabel, weekday, onEvent) { picking = false }
    }
}

@Composable
private fun WeekdayPicker(title: String, weekday: Int, onEvent: (PreferencesEvent) -> Unit, onClose: () -> Unit) {
    AppPickerSheet(
        title = title,
        onDismiss = onClose,
        testTagPrefix = "preferences_checkin_weekday_sheet",
    ) {
        for (day in 1..DAYS_PER_ISO_WEEK) {
            AppListRow(
                headline = weekdayName(day),
                modifier = Modifier
                    .testTag("preferences_checkin_weekday_option_$day")
                    .semantics { selected = day == weekday },
                onClick = {
                    onEvent(PreferencesEvent.SetCheckInWeekday(day))
                    onClose()
                },
                trailing = if (day == weekday) {
                    {
                        Icon(
                            painter = painterResource(Res.drawable.ic_check),
                            contentDescription = null,
                            tint = AppTheme.colors.athleticCyan,
                            modifier = Modifier.size(Spacing.xl),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}
