package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_day_picker_open_cd
import io.github.lopezjavier.appfitness.composeapp.resources.diary_next_day
import io.github.lopezjavier.appfitness.composeapp.resources.diary_prev_day
import io.github.lopezjavier.appfitness.composeapp.resources.diary_quick_search_cd
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_back
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calendar
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_left
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.ic_search
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_back_cd
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsBell
import io.github.lopezjavier.appfitness.feature.workout.DateLabel
import io.github.lopezjavier.appfitness.feature.workout.text
import org.jetbrains.compose.resources.stringResource

/**
 * The header of the Diario screen (the `AppScreen.header` slot): previous/next day arrows around the centred day
 * label (one line, ellipsised; next is disabled and dimmed on today) and, at the end, the quick-search magnifier
 * ([onOpenSearch], tab root only), the calendar button and the bell. With [onBack] (a past-day route) a back arrow
 * `diary_back` leads the row and the magnifier and bell are not shown.
 */
@Composable
internal fun DiaryDateHeader(
    label: DateLabel,
    canGoNext: Boolean,
    onEvent: (DiaryEvent) -> Unit,
    onOpenDayPicker: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenSearch: () -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            AppIconButton(
                contentDescription = stringResource(Res.string.topbar_back_cd),
                icon = Res.drawable.ic_arrow_back,
                testTag = "diary_back",
                onClick = onBack,
            )
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }.testTag("diary_date_label"),
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.diary_next_day),
            icon = Res.drawable.ic_chevron_right,
            testTag = "diary_date_next",
            onClick = { onEvent(DiaryEvent.NextDay) },
            enabled = canGoNext,
        )
        // Only the tab root offers the quick (view-only) food search; a past day does not.
        if (onBack == null) {
            AppIconButton(
                contentDescription = stringResource(Res.string.diary_quick_search_cd),
                icon = Res.drawable.ic_search,
                testTag = "diary_quick_search",
                onClick = onOpenSearch,
            )
        }
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
