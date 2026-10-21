package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.feature.analytics.ExerciseProgressRoot
import io.github.lopezjavier.appfitness.feature.analytics.MuscleVolumeRoot
import io.github.lopezjavier.appfitness.feature.analytics.StrengthRoot
import io.github.lopezjavier.appfitness.feature.diary.DiaryHistoryRoot
import io.github.lopezjavier.appfitness.feature.diary.DiaryRoot
import io.github.lopezjavier.appfitness.feature.diary.EditEntryRoot
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseDetailRoot
import io.github.lopezjavier.appfitness.feature.exercises.ExercisePickerRoot
import io.github.lopezjavier.appfitness.feature.food.AddFoodRoot
import io.github.lopezjavier.appfitness.feature.food.CreateFoodRoot
import io.github.lopezjavier.appfitness.feature.food.FoodSearchRoot
import io.github.lopezjavier.appfitness.feature.meals.MealSlotsRoot
import io.github.lopezjavier.appfitness.feature.profile.PreferencesRoute
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootRoute
import io.github.lopezjavier.appfitness.feature.routines.RoutineBuilderRoot
import io.github.lopezjavier.appfitness.feature.templates.SaveTemplateRoot
import io.github.lopezjavier.appfitness.feature.weight.WeightRoot
import io.github.lopezjavier.appfitness.feature.workout.HistoryRoot
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionRoot
import io.github.lopezjavier.appfitness.feature.workout.SessionDetailRoot
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryRoot
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: connects [AppViewModel], the Perfil root and Preferencias to the stateless [App]. */
@Composable
@Suppress("LongMethod") // One slot per destination.
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
        profile = {
                profileModifier,
                onOpenPreferences,
                onOpenCatalog,
                onOpenStrength,
                onOpenDiaryHistory,
                onOpenWeight,
            ->
            ProfileRootRoute(
                profileModifier,
                onOpenPreferences,
                onOpenCatalog,
                onOpenStrength,
                onOpenDiaryHistory,
                onOpenWeight,
            )
        },
        strength = { strengthModifier, onOpenExercise, onOpenMuscleVolume, onStartWorkout ->
            StrengthRoot(onOpenExercise, onOpenMuscleVolume, onStartWorkout, strengthModifier)
        },
        muscleVolume = { volumeModifier, onStartWorkout -> MuscleVolumeRoot(onStartWorkout, volumeModifier) },
        exerciseProgress = { progressModifier, exerciseId -> ExerciseProgressRoot(exerciseId, progressModifier) },
        preferences = { preferencesModifier, onOpenMeals -> PreferencesRoute(onOpenMeals, preferencesModifier) },
        exercisePicker = { pickerModifier, selectMode, onOpenDetail, onSelected ->
            ExercisePickerRoot(selectMode, onOpenDetail, onSelected, pickerModifier)
        },
        exerciseDetail = { detailModifier, exerciseId, selectMode, onSelect, onOpenProgress ->
            ExerciseDetailRoot(exerciseId, selectMode, onSelect, detailModifier, onOpenProgress)
        },
        routines = { routinesModifier, onOpenBuilder, onOpenLive, onOpenHistory ->
            WorkoutEntryRoot(onOpenBuilder, onOpenLive, onOpenHistory, routinesModifier)
        },
        diary = { diaryModifier, onOpenPreferences, onAddFood, onSaveTemplate, edit ->
            DiaryRoot(onOpenPreferences, onAddFood, { onAddFood(null, it) }, onSaveTemplate, edit, diaryModifier)
        },
        weight = { weightModifier -> WeightRoot(weightModifier) },
        diaryHistory = { historyModifier, onOpenDay -> DiaryHistoryRoot(onOpenDay, historyModifier) },
        mealSlots = { mealsModifier, onClose -> MealSlotsRoot(onClose, mealsModifier) },
        foodSearch = { searchModifier, slotId, date, onOpenFood, onCreateFood, onAdd, onOpenTemplate ->
            FoodSearchRoot(slotId, date, onOpenFood, onCreateFood, onAdd, onOpenTemplate, searchModifier)
        },
        createFood = { formModifier, barcode, name, onSaved -> CreateFoodRoot(barcode, name, onSaved, formModifier) },
        addFood = { foodId, slotId, date, onAdded, onDismiss ->
            AddFoodRoot(foodId, slotId, date, onAdded, onDismiss)
        },
        editEntry = { entryId, date, onSaved, onDeleteRequested, onDismiss ->
            EditEntryRoot(entryId, date, onSaved, onDeleteRequested, onDismiss)
        },
        saveTemplate = { slotId, date, onSave, onDismiss -> SaveTemplateRoot(slotId, date, onSave, onDismiss) },
        history = { historyModifier, onOpenSession -> HistoryRoot(onOpenSession, historyModifier) },
        sessionDetail = { detailModifier, sessionId, onClose, onOpenProgress ->
            SessionDetailRoot(sessionId, onClose, detailModifier, onOpenProgress)
        },
        routineBuilder = { builderModifier, host ->
            RoutineBuilderRoot(
                routineId = host.routineId,
                selectedExerciseId = host.selectedExerciseId,
                onConsumeSelection = host.onConsumeSelection,
                onAddExercise = host.onAddExercise,
                onOpenExercise = host.onOpenExercise,
                onClose = host.onClose,
                modifier = builderModifier,
            )
        },
        liveSession = { liveModifier, host ->
            LiveSessionRoot(
                selectedExerciseId = host.selectedExerciseId,
                onConsumeSelection = host.onConsumeSelection,
                onAddExercise = host.onAddExercise,
                onOpenExercise = host.onOpenExercise,
                onClose = host.onClose,
                modifier = liveModifier,
            )
        },
        onOnboardingComplete = { viewModel.onEvent(AppEvent.OnboardingCompleted) },
    )
}
