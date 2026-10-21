package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

/** Stateful diary history: connects [DiaryHistoryViewModel]; [onOpenDay] opens the Diary of a day. */
@Composable
fun DiaryHistoryRoot(
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiaryHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenDay by rememberUpdatedState(onOpenDay)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DiaryHistoryEffect.OpenDiaryDay -> currentOnOpenDay(effect.date)
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(DiaryHistoryEvent.Refresh) }
    DiaryHistoryScreen(state, viewModel::onEvent, modifier)
}
