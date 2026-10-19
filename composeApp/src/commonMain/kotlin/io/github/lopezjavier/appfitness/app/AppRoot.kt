package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseDetailRoot
import io.github.lopezjavier.appfitness.feature.exercises.ExercisePickerRoot
import io.github.lopezjavier.appfitness.feature.profile.PreferencesRoute
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootRoute
import io.github.lopezjavier.appfitness.feature.routines.RoutineBuilderRoot
import io.github.lopezjavier.appfitness.feature.workout.HistoryRoot
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionRoot
import io.github.lopezjavier.appfitness.feature.workout.SessionDetailRoot
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryRoot
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: connects [AppViewModel], the Perfil root and Preferencias to the stateless [App]. */
@Composable
fun AppRoot(
    modifier: Modifier = Modifier,
    features: AppFeatures = AppFeatures(),
    viewModel: AppViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    App(
        modifier = modifier,
        features = features,
        isOffline = state.isOffline,
        rootRoute = state.rootRoute,
        onRetryProfile = { viewModel.onEvent(AppEvent.RetryProfile) },
        profile = { profileModifier, onOpenPreferences, onOpenCatalog ->
            ProfileRootRoute(profileModifier, onOpenPreferences, onOpenCatalog)
        },
        preferences = { preferencesModifier -> PreferencesRoute(preferencesModifier) },
        exercisePicker = { pickerModifier, selectMode, onOpenDetail, onSelected ->
            ExercisePickerRoot(selectMode, onOpenDetail, onSelected, pickerModifier)
        },
        exerciseDetail = { detailModifier, exerciseId, selectMode, onSelect ->
            ExerciseDetailRoot(exerciseId, selectMode, onSelect, detailModifier)
        },
        routines = { routinesModifier, onOpenBuilder, onOpenLive, onOpenHistory ->
            WorkoutEntryRoot(onOpenBuilder, onOpenLive, onOpenHistory, routinesModifier)
        },
        history = { historyModifier, onOpenSession -> HistoryRoot(onOpenSession, historyModifier) },
        sessionDetail = { detailModifier, sessionId, onClose -> SessionDetailRoot(sessionId, onClose, detailModifier) },
        routineBuilder = { builderModifier, host ->
            RoutineBuilderRoot(
                routineId = host.routineId,
                selectedExerciseId = host.selectedExerciseId,
                onConsumeSelection = host.onConsumeSelection,
                onAddExercise = host.onAddExercise,
                onClose = host.onClose,
                modifier = builderModifier,
            )
        },
        liveSession = { liveModifier, host ->
            LiveSessionRoot(
                selectedExerciseId = host.selectedExerciseId,
                onConsumeSelection = host.onConsumeSelection,
                onAddExercise = host.onAddExercise,
                onClose = host.onClose,
                modifier = liveModifier,
            )
        },
        onOnboardingComplete = { viewModel.onEvent(AppEvent.OnboardingCompleted) },
    )
}
