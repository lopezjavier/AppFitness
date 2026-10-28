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
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.weekdayName
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val DAYS_PER_ISO_WEEK = 7

/**
 * "Check-in semanal" section of Preferencias (US-59): one "Día del check-in" row (value = the weekday, lowercase)
 * opening a picker of the seven weekdays. A tap applies to the draft and closes the picker (R-12); the section then
 * saves through the editor's "Guardar" like every other section.
 */
@Composable
internal fun CheckInDaySection(weekday: Int, onSelect: (Int) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(Res.string.preferences_checkin_day_row)
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            NavigationRow(
                label = title,
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
        AppPickerSheet(
            title = title,
            onDismiss = { picking = false },
            testTagPrefix = "preferences_checkin_weekday_sheet",
        ) {
            for (day in 1..DAYS_PER_ISO_WEEK) {
                AppListRow(
                    headline = weekdayName(day),
                    modifier = Modifier
                        .testTag("preferences_checkin_weekday_option_$day")
                        .semantics { selected = day == weekday },
                    onClick = {
                        onSelect(day)
                        picking = false
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
}
