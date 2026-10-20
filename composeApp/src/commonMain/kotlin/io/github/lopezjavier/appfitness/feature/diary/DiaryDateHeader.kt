package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_next_day
import io.github.lopezjavier.appfitness.composeapp.resources.diary_prev_day
import io.github.lopezjavier.appfitness.composeapp.resources.diary_view_menu_cd
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_left
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.ic_view_list
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.workout.DateLabel
import io.github.lopezjavier.appfitness.feature.workout.text
import org.jetbrains.compose.resources.stringResource

/**
 * Previous/next day arrows around the centred day label (next is disabled and dimmed on today), and the button that
 * opens the view-mode selector through [onViewMenu].
 */
@Composable
internal fun DiaryDateHeader(
    label: DateLabel,
    canGoNext: Boolean,
    onEvent: (DiaryEvent) -> Unit,
    onViewMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
        AppIconButton(
            contentDescription = stringResource(Res.string.diary_view_menu_cd),
            icon = Res.drawable.ic_view_list,
            testTag = "diary_view_menu",
            onClick = onViewMenu,
        )
    }
}
