package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_checkin_day_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_checkin_day_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_checkin_day_row
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_block_end
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_checkin
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_checkin_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_checkin_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_circumference
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_circumference_helper
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_week_summary
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_weight
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_weight_helper
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppSwitchRow
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.HelpContent
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.OptionPickerSheet
import io.github.lopezjavier.appfitness.designsystem.PickerOption
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.weekdayName
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import org.jetbrains.compose.resources.stringResource

private const val DAYS_PER_ISO_WEEK = 7

/**
 * "Avisos" section of Preferencias (US-57, US-59): the reminder switches, in the order weight, circumferences,
 * check-in, its day, weekly summary, block end (C-156), and the "Día del check-in" row (value = the weekday,
 * lowercase) opening a picker of the seven weekdays. Switches and day are drafts (R-10): a tap applies to
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
                checked = form.checkInReminder,
                onCheckedChange = { onEvent(PreferencesEvent.SetReminderEnabled(NotificationKind.WeeklyCheckIn, it)) },
                modifier = Modifier.testTag("preferences_reminders_switch_checkin"),
                help = HelpContent.fromBody(stringResource(Res.string.preferences_reminders_checkin_help_body)),
                helpContentDescription = stringResource(Res.string.preferences_reminders_checkin_help_cd),
                helpTestTag = "preferences_reminders_checkin_help",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavigationRow(
                    label = dayLabel,
                    onClick = { picking = true },
                    modifier = Modifier.weight(1f).testTag("preferences_checkin_weekday_row"),
                    value = weekdayName(weekday).lowercase(),
                )
                HelpButton(
                    contentDescription = stringResource(Res.string.preferences_checkin_day_help_cd),
                    title = dayLabel,
                    body = stringResource(Res.string.preferences_checkin_day_help_body),
                    testTag = "preferences_checkin_day_help",
                )
            }
            NoticeSwitches(form, onEvent)
        }
    }
    if (picking) {
        WeekdayPicker(dayLabel, weekday, onEvent) { picking = false }
    }
}

/** The two "Centro de avisos" notice switches: the weekly summary and the end of the block (T-243). */
@Composable
private fun NoticeSwitches(form: PreferencesForm, onEvent: (PreferencesEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppSwitchRow(
            label = stringResource(Res.string.preferences_reminders_week_summary),
            checked = form.weekSummaryReminder,
            onCheckedChange = { onEvent(PreferencesEvent.SetReminderEnabled(NotificationKind.WeekSummary, it)) },
            modifier = Modifier.testTag("preferences_switch_week_summary"),
        )
        AppSwitchRow(
            label = stringResource(Res.string.preferences_reminders_block_end),
            checked = form.blockEndReminder,
            onCheckedChange = { onEvent(PreferencesEvent.SetReminderEnabled(NotificationKind.BlockEnd, it)) },
            modifier = Modifier.testTag("preferences_switch_block_end"),
        )
    }
}

@Composable
private fun WeekdayPicker(title: String, weekday: Int, onEvent: (PreferencesEvent) -> Unit, onClose: () -> Unit) {
    // One choice applies on tap with a lime check on the current day (R-12, R-64 b).
    OptionPickerSheet(
        title = title,
        tagPrefix = "preferences_checkin_weekday",
        options = (1..DAYS_PER_ISO_WEEK).map { day ->
            PickerOption(
                tag = day.toString(),
                label = weekdayName(day),
                isSelected = day == weekday,
                onPick = { onEvent(PreferencesEvent.SetCheckInWeekday(day)) },
            )
        },
        onClose = onClose,
    )
}
