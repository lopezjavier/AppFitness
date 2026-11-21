package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_block_end
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_circumference
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_circumference_helper
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_week_summary
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_weight
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_weight_helper
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppSwitchRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import org.jetbrains.compose.resources.stringResource

/**
 * "Avisos" section of Preferencias (US-57): the four notice switches, in the order weight, circumferences, weekly
 * summary, block end (C-156). The switches are drafts (R-10): a tap applies to the draft and the editor's "Guardar"
 * saves them.
 */
@Composable
internal fun RemindersSection(form: PreferencesForm, onEvent: (PreferencesEvent) -> Unit) {
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
            NoticeSwitches(form, onEvent)
        }
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
