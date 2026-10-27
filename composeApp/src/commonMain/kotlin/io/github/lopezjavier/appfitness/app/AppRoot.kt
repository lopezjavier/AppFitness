package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.feature.analytics.ExerciseProgressRoot
import io.github.lopezjavier.appfitness.feature.analytics.MuscleVolumeRoot
import io.github.lopezjavier.appfitness.feature.analytics.StrengthRoot
import io.github.lopezjavier.appfitness.feature.analytics.stats.TrainingStatsRoot
import io.github.lopezjavier.appfitness.feature.diary.DiaryHistoryRoot
import io.github.lopezjavier.appfitness.feature.diary.DiaryRoot
import io.github.lopezjavier.appfitness.feature.diary.EditEntryRoot
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseDetailRoot
import io.github.lopezjavier.appfitness.feature.exercises.ExercisePickerRoot
import io.github.lopezjavier.appfitness.feature.food.AddFoodRoot
import io.github.lopezjavier.appfitness.feature.food.CreateFoodRoot
import io.github.lopezjavier.appfitness.feature.food.EditFoodRoot
import io.github.lopezjavier.appfitness.feature.food.FoodSearchRoot
import io.github.lopezjavier.appfitness.feature.meals.MealSlotsRoot
import io.github.lopezjavier.appfitness.feature.meals.defaults.MealDefaultsRoot
import io.github.lopezjavier.appfitness.feature.profile.PreferencesRoute
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootRoute
import io.github.lopezjavier.appfitness.feature.routines.RoutineBuilderRoot
import io.github.lopezjavier.appfitness.feature.routines.RoutineDetailsRoot
import io.github.lopezjavier.appfitness.feature.templates.SaveTemplateRoot
import io.github.lopezjavier.appfitness.feature.templates.TemplateDetailsRoot
import io.github.lopezjavier.appfitness.feature.weight.WeightHistoryRoot
import io.github.lopezjavier.appfitness.feature.weight.WeightRoot
import io.github.lopezjavier.appfitness.feature.workout.EditWorkoutRoot
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionRoot
import io.github.lopezjavier.appfitness.feature.workout.PastWorkoutRoot
import io.github.lopezjavier.appfitness.feature.workout.SessionDetailRoot
import io.github.lopezjavier.appfitness.feature.workout.WorkoutCalendarRoot
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryRoot
import kotlinx.datetime.LocalDate
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
                onOpenTrainingStats,
            ->
            ProfileRootRoute(
                profileModifier,
                onOpenPreferences,
                onOpenCatalog,
                onOpenStrength,
                onOpenDiaryHistory,
                onOpenWeight,
                onOpenTrainingStats,
            )
        },
        strength = { strengthModifier, onOpenExercise, onOpenMuscleVolume, onStartWorkout, onBack ->
            StrengthRoot(onOpenExercise, onOpenMuscleVolume, onStartWorkout, onBack, strengthModifier)
        },
        muscleVolume = { volumeModifier, onStartWorkout, onBack ->
            MuscleVolumeRoot(onStartWorkout, onBack, volumeModifier)
        },
        trainingStats = { statsModifier, onOpenExercise, onBack ->
            TrainingStatsRoot(onOpenExercise, onBack, statsModifier)
        },
        exerciseProgress = { progressModifier, exerciseId, onBack ->
            ExerciseProgressRoot(exerciseId, onBack, progressModifier)
        },
        preferences = { preferencesModifier, onOpenMeals -> PreferencesRoute(onOpenMeals, preferencesModifier) },
        exercisePicker = { pickerModifier, selectMode, onOpenDetail, onSelected, onBack ->
            ExercisePickerRoot(selectMode, onOpenDetail, onSelected, pickerModifier, onBack)
        },
        exerciseDetail = { detailModifier, exerciseId, selectMode, onSelect, onOpenProgress, onClose ->
            ExerciseDetailRoot(exerciseId, selectMode, onSelect, detailModifier, onOpenProgress, onClose)
        },
        routines = { routinesModifier, onOpenBuilder, onOpenDetails, onOpenLive, onOpenHistory ->
            WorkoutEntryRoot(onOpenBuilder, onOpenDetails, onOpenLive, onOpenHistory, routinesModifier)
        },
        diary = { diaryModifier, onOpenPreferences, onAddFood, onSaveTemplate, edit, onBack ->
            DiaryRoot(
                onOpenPreferences,
                onAddFood,
                { onAddFood(null, it) },
                onSaveTemplate,
                edit,
                diaryModifier,
                onBack,
            )
        },
        weight = { weightModifier, onOpenHistory, onBack -> WeightRoot(onOpenHistory, onBack, weightModifier) },
        weightHistory = { historyModifier, onBack -> WeightHistoryRoot(onBack, historyModifier) },
        diaryHistory = { historyModifier, onOpenDay, onBack -> DiaryHistoryRoot(onOpenDay, onBack, historyModifier) },
        mealSlots = { mealsModifier, onClose, onOpenDefaults -> MealSlotsRoot(onClose, onOpenDefaults, mealsModifier) },
        mealDefaults = { defaultsModifier, onClose -> MealDefaultsRoot(onClose, defaultsModifier) },
        foodSearch = { searchModifier, slotId, date, onOpenFood, onCreateFood, onOpenTemplate, pick, onBack ->
            FoodSearchRoot(slotId, date, onOpenFood, onCreateFood, onOpenTemplate, onBack, searchModifier, pick)
        },
        createFood = { formModifier, barcode, name, prefill, editFoodId, onBack, onSaved ->
            if (editFoodId != null) {
                EditFoodRoot(editFoodId, onSaved, onBack, formModifier)
            } else {
                CreateFoodRoot(barcode, name, onSaved, onBack, formModifier, prefill)
            }
        },
        addFood = { foodId, slotId, date, onAdded, onDismiss, onEdit, pick, onPick ->
            AddFoodRoot(
                foodId,
                slotId,
                date,
                onAdded,
                onDismiss,
                onEdit = onEdit,
                pick = pick,
                onPick = { onPick(it.foodId, it.amount, it.portionLabel) },
            )
        },
        templateDetails = {
                detailsModifier,
                route,
                onClose,
                onAdded,
                onDeleted,
                onAddFood,
                pickedFood,
                onConsumePickedFood,
            ->
            TemplateDetailsRoot(
                templateId = route.templateId,
                slotId = route.slotId,
                date = route.date,
                onAdd = onAdded,
                onClose = onClose,
                onDelete = onDeleted,
                onAddFood = onAddFood,
                modifier = detailsModifier,
                pickedFood = pickedFood,
                onConsumePickedFood = onConsumePickedFood,
            )
        },
        editEntry = { entryId, date, onSaved, onDeleteRequested, onDismiss ->
            EditEntryRoot(entryId, date, onSaved, onDeleteRequested, onDismiss)
        },
        saveTemplate = { slotId, date, onSave, onDismiss -> SaveTemplateRoot(slotId, date, onSave, onDismiss) },
        history = { historyModifier, onOpenSession, onBack, onStartPastWorkout ->
            WorkoutCalendarRoot(onOpenSession, onBack, onStartPastWorkout, historyModifier)
        },
        sessionDetail = { detailModifier, sessionId, onClose, onOpenExercise, onEdit ->
            SessionDetailRoot(sessionId, onClose, onOpenExercise, detailModifier, onEdit)
        },
        editWorkout = { formModifier, sessionId, host, onDeleted ->
            EditWorkoutRoot(
                sessionId = sessionId,
                selectedExerciseId = host.selectedExerciseId,
                onConsumeSelection = host.onConsumeSelection,
                onAddExercise = host.onAddExercise,
                onClose = host.onClose,
                onDeleted = onDeleted,
                modifier = formModifier,
            )
        },
        pastWorkout = { formModifier, routineId, sessionId, date, host ->
            PastWorkoutRoot(
                routineId = routineId,
                sessionId = sessionId,
                selectedExerciseId = host.selectedExerciseId,
                onConsumeSelection = host.onConsumeSelection,
                onAddExercise = host.onAddExercise,
                onClose = host.onClose,
                modifier = formModifier,
                date = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            )
        },
        routineDetails = { detailsModifier, routineId, onEdit, onOpenLive, onOpenExercise, onBack ->
            RoutineDetailsRoot(routineId, onEdit, onOpenLive, onOpenExercise, onBack, detailsModifier)
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
