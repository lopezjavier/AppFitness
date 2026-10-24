package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_choice_cd
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_choices_close_cd
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_choices_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.TrainedSession
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

/**
 * Picker of a day with two or more workouts: one row per workout (name and start time). Read-only: a row opens the
 * workout, the X, back, the scrim and a swipe close it without side effects.
 */
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
@Composable
internal fun WorkoutChoicesSheet(
    sessions: List<TrainedSession>,
    zone: TimeZone,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, testTagPrefix = "calendar_choices_sheet") {
        Column(
            Modifier.padding(horizontal = Spacing.screenMargin).padding(bottom = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.calendar_choices_title),
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                AppIconButton(
                    contentDescription = stringResource(Res.string.calendar_choices_close_cd),
                    icon = Res.drawable.ic_close,
                    testTag = "calendar_choices_close",
                    onClick = onDismiss,
                )
            }
            sessions.forEach { session -> ChoiceRow(session, zone) { onPick(session.id) } }
        }
    }
}

@Composable
private fun ChoiceRow(session: TrainedSession, zone: TimeZone, onClick: () -> Unit) {
    val time = timeText(session.startedAt, zone)
    val description = stringResource(Res.string.calendar_choice_cd, session.name, time)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("calendar_choice_${session.id}")
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = session.name,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(time, style = AppTheme.typography.labelNumeric, color = AppTheme.colors.athleticCyan)
    }
}
