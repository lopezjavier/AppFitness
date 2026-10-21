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
import androidx.compose.ui.text.style.TextAlign
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_open_cd
import io.github.lopezjavier.appfitness.composeapp.resources.diary_next_day
import io.github.lopezjavier.appfitness.composeapp.resources.diary_prev_day
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calendar
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_left
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.workout.DateLabel
import io.github.lopezjavier.appfitness.feature.workout.text
import org.jetbrains.compose.resources.stringResource

/** Previous/next day arrows around the centred day label (next is disabled and dimmed on today). */
@Composable
internal fun DiaryDateHeader(
    label: DateLabel,
    canGoNext: Boolean,
    onEvent: (DiaryEvent) -> Unit,
    modifier: Modifier = Modifier,
    showHistory: Boolean = false,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Keeps the day label centred between the arrows while the history button sits at the end.
        if (showHistory) Spacer(Modifier.size(Spacing.minTouchTarget))
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
            modifier = Modifier.weight(1f).testTag("diary_date_label"),
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.diary_next_day),
            icon = Res.drawable.ic_chevron_right,
            testTag = "diary_date_next",
            onClick = { onEvent(DiaryEvent.NextDay) },
            enabled = canGoNext,
        )
        if (showHistory) {
            AppIconButton(
                contentDescription = stringResource(Res.string.diary_history_open_cd),
                icon = Res.drawable.ic_calendar,
                testTag = "diary_history_open",
                onClick = { onEvent(DiaryEvent.OpenHistory) },
            )
        }
    }
}
