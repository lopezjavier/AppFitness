package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_day_cards_error
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_day_empty
import io.github.lopezjavier.appfitness.composeapp.resources.history_add_workout_item
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

private val SkeletonRowHeight = Spacing.xl * 3

/**
 * The selected day under the month grid: its date as a heading, one [HistoryWorkoutRow] per workout ("Sin entrenos"
 * when there is none) and, up to today, "Añadir entreno" at the end of the list (R-19).
 */
@OptIn(ExperimentalFoundationApi::class) // bringIntoView keeps the section reachable with one hand.
@Composable
internal fun CalendarDaySection(
    day: LocalDate,
    cards: DayCardsState,
    canAdd: Boolean,
    onEvent: (WorkoutCalendarEvent) -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(cards) { if (cards is DayCardsState.Loaded) requester.bringIntoView() }
    Column(
        Modifier.fillMaxWidth().bringIntoViewRequester(requester).testTag("calendar_day_cards"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = AppDateFormat.title(day),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.testTag("calendar_day_heading").semantics { heading() },
        )
        DayRows(cards, onEvent)
        if (canAdd) {
            AddItemButton(
                objectLabel = stringResource(Res.string.history_add_workout_item),
                onClick = { onEvent(WorkoutCalendarEvent.AddWorkout) },
                modifier = Modifier.testTag("calendar_add_workout"),
            )
        }
    }
}

@Composable
private fun DayRows(cards: DayCardsState, onEvent: (WorkoutCalendarEvent) -> Unit) {
    when (cards) {
        DayCardsState.None -> Unit

        DayCardsState.Loading -> SkeletonBox(
            Modifier.fillMaxWidth().height(SkeletonRowHeight).testTag("calendar_day_cards_loading"),
            AppShapes.lg,
        )

        DayCardsState.Error -> ErrorState(
            message = stringResource(Res.string.calendar_day_cards_error),
            action = AppStateAction(stringResource(Res.string.action_retry)) {
                onEvent(WorkoutCalendarEvent.RetryDayCards)
            },
            testTagPrefix = "calendar_day_cards_error",
        )

        is DayCardsState.Loaded -> if (cards.rows.isEmpty()) {
            Text(
                text = stringResource(Res.string.calendar_day_empty),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("calendar_day_empty"),
            )
        } else {
            cards.rows.forEach { row ->
                HistoryWorkoutRow(
                    row = row,
                    onClick = { onEvent(WorkoutCalendarEvent.OpenSession(row.id)) },
                    tag = "calendar_day_card_${row.id}",
                )
            }
        }
    }
}
