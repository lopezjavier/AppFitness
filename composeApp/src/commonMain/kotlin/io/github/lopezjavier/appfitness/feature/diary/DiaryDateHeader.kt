package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_day_picker_open_cd
import io.github.lopezjavier.appfitness.composeapp.resources.diary_next_day
import io.github.lopezjavier.appfitness.composeapp.resources.diary_prev_day
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_back
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calendar
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_left
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_back_cd
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsBell
import io.github.lopezjavier.appfitness.feature.workout.DateLabel
import io.github.lopezjavier.appfitness.feature.workout.text
import org.jetbrains.compose.resources.stringResource

/**
 * The header of the Diario screen (the `AppScreen.header` slot): previous/next day arrows around the centred day
 * label (next is disabled and dimmed on today) and, at the end, the calendar button. With [onBack] (a past-day route)
 * a back arrow `diary_back` replaces the 48 dp spacer that keeps the label centred.
 */
@Composable
internal fun DiaryDateHeader(
    label: DateLabel,
    canGoNext: Boolean,
    onEvent: (DiaryEvent) -> Unit,
    onOpenDayPicker: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            AppIconButton(
                contentDescription = stringResource(Res.string.topbar_back_cd),
                icon = Res.drawable.ic_arrow_back,
                testTag = "diary_back",
                onClick = onBack,
            )
        } else {
            Spacer(Modifier.size(Spacing.minTouchTarget))
        }
        AppIconButton(
            contentDescription = stringResource(Res.string.diary_prev_day),
            icon = Res.drawable.ic_chevron_left,
            testTag = "diary_date_prev",
            onClick = { onEvent(DiaryEvent.PreviousDay) },
        )
        Text(
            text = label.text(),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() }.testTag("diary_date_label"),
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.diary_next_day),
            icon = Res.drawable.ic_chevron_right,
            testTag = "diary_date_next",
            onClick = { onEvent(DiaryEvent.NextDay) },
            enabled = canGoNext,
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.diary_day_picker_open_cd),
            icon = Res.drawable.ic_calendar,
            testTag = "diary_day_picker_open",
            onClick = onOpenDayPicker,
        )
        // Only the tab root (no back arrow) offers the notification center; a past day does not.
        if (onBack == null) NotificationsBell("diary_action_notifications")
    }
}
