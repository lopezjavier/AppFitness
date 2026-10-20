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

/**
 * Stateful "Diario" tab: connects [DiaryViewModel]. [onOpenPreferences] leads the "Configura tu objetivo" prompt to
 * Preferencias; [onAddToMeal] receives the meal and day to add food to and [onAddFood] the day for the Diary-level
 * add action (no meal preselected). On resume the goals are reloaded so a goal
 * set in Preferencias shows up.
 */
@Composable
fun DiaryRoot(
    onOpenPreferences: () -> Unit,
    onAddToMeal: (slotId: String, date: LocalDate) -> Unit,
    onAddFood: (date: LocalDate) -> Unit,
    onOpenMealSettings: (date: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenMealSettings by rememberUpdatedState(onOpenMealSettings)
    val currentOnAddToMeal by rememberUpdatedState(onAddToMeal)
    val currentOnAddFood by rememberUpdatedState(onAddFood)
    val currentOnOpenPreferences by rememberUpdatedState(onOpenPreferences)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DiaryEffect.AddToMeal -> currentOnAddToMeal(effect.slotId, effect.date)
                is DiaryEffect.AddFood -> currentOnAddFood(effect.date)
                DiaryEffect.OpenPreferences -> currentOnOpenPreferences()
                is DiaryEffect.OpenMealSettings -> currentOnOpenMealSettings(effect.date)
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(DiaryEvent.Refresh) }
    DiaryScreen(state, viewModel::onEvent, modifier)
}
