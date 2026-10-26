package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.weight_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.weight_history_title
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.MonthCalendarGrid
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

private val SkeletonCalendarHeight = Spacing.xl * 11

/**
 * Stateless "Historial de peso": the month calendar of the measurements on an `AppScreen` with a back arrow. Tapping a
 * day opens its day sheet (US-25); loading is a skeleton and a failed load an error with "Reintentar".
 */
@Composable
fun WeightHistoryScreen(
    state: WeightUiState,
    onEvent: (WeightEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    AppScreen(
        title = stringResource(Res.string.weight_history_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = "weight_history",
    ) {
        Box(Modifier.fillMaxSize().testTag("weight_history_screen")) {
            when (state) {
                WeightUiState.Loading -> Column(
                    Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("weight_history_loading"),
                ) {
                    SkeletonBox(Modifier.fillMaxWidth().height(SkeletonCalendarHeight), AppShapes.lg)
                }

                WeightUiState.Error -> ErrorState(
                    message = stringResource(Res.string.weight_error_message),
                    action = AppStateAction(stringResource(Res.string.state_retry)) { onEvent(WeightEvent.Retry) },
                    modifier = Modifier.align(Alignment.Center),
                    testTagPrefix = "weight_history_error",
                )

                is WeightUiState.Content -> {
                    HistoryCalendar(state.content, onEvent)
                    state.content.sheet?.let { WeightDaySheet(it, state.content.isOnline, timeZone, onEvent) }
                }
            }
        }
    }
}

@Composable
private fun HistoryCalendar(content: WeightContent, onEvent: (WeightEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        MonthCalendarGrid(
            month = content.month,
            markedDays = content.markedDays,
            selected = content.sheet?.day,
            today = content.today,
            onSelect = { onEvent(WeightEvent.OpenDay(it)) },
            onPrevMonth = { onEvent(WeightEvent.PreviousMonth) },
            onNextMonth = { onEvent(WeightEvent.NextMonth) },
            canGoNext = content.canGoNext,
            modifier = Modifier.testTag("weight_history_calendar"),
        )
    }
}
