package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.feature.about.AboutRoot
import io.github.lopezjavier.appfitness.feature.activity.ActivityRoot
import io.github.lopezjavier.appfitness.feature.analytics.ExerciseProgressRoot
import io.github.lopezjavier.appfitness.feature.analytics.MuscleVolumeRoot
import io.github.lopezjavier.appfitness.feature.analytics.StrengthRoot
import io.github.lopezjavier.appfitness.feature.analytics.stats.TrainingStatsRoot
import io.github.lopezjavier.appfitness.feature.checkin.CheckInRoot
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
import io.github.lopezjavier.appfitness.feature.notifications.NotificationCenterRoot
import io.github.lopezjavier.appfitness.feature.profile.PreferencesHubActions
import io.github.lopezjavier.appfitness.feature.profile.PreferencesHubRoot
import io.github.lopezjavier.appfitness.feature.profile.PreferencesSectionRoot
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootRoute
import io.github.lopezjavier.appfitness.feature.routines.RoutineBuilderRoot
import io.github.lopezjavier.appfitness.feature.routines.RoutineDetailsRoot
import io.github.lopezjavier.appfitness.feature.templates.SaveTemplateRoot
import io.github.lopezjavier.appfitness.feature.templates.TemplateDetailsRoot
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartRoot
import io.github.lopezjavier.appfitness.feature.trainer.BlockBuilderRoot
import io.github.lopezjavier.appfitness.feature.trainer.BlockPreviewRoot
import io.github.lopezjavier.appfitness.feature.trainer.BlockReviewRoot
import io.github.lopezjavier.appfitness.feature.trainer.FolderBlockRoot
import io.github.lopezjavier.appfitness.feature.trainer.FolderReviewResultRoot
import io.github.lopezjavier.appfitness.feature.trainer.FreeTextLinesRoot
import io.github.lopezjavier.appfitness.feature.trainer.IntakeRoot
import io.github.lopezjavier.appfitness.feature.trainer.PastReportRoot
import io.github.lopezjavier.appfitness.feature.trainer.PlanProposalRoot
import io.github.lopezjavier.appfitness.feature.trainer.ReportsRoot
import io.github.lopezjavier.appfitness.feature.trainer.TrainerBoxRoot
import io.github.lopezjavier.appfitness.feature.trainer.TrainerRoute
import io.github.lopezjavier.appfitness.feature.trainer.TrainerViewModel
import io.github.lopezjavier.appfitness.feature.trainer.WeekSummaryRoot
import io.github.lopezjavier.appfitness.feature.trainer.YourDataRoot
import io.github.lopezjavier.appfitness.feature.weight.WeightHistoryRoot
import io.github.lopezjavier.appfitness.feature.weight.WeightRoot
import io.github.lopezjavier.appfitness.feature.workout.EditWorkoutRoot
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionRoot
import io.github.lopezjavier.appfitness.feature.workout.PastWorkoutRoot
import io.github.lopezjavier.appfitness.feature.workout.SessionDetailRoot
import io.github.lopezjavier.appfitness.feature.workout.WorkoutCalendarRoot
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryRoot
import kotlinx.datetime.LocalDate
import org.koin.compose.koinInject
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
    val refreshReminders: () -> Unit = { viewModel.onEvent(AppEvent.RefreshReminders) }
    // Coming back to the app recomputes the reminders (a day may have passed); ignored until Main is shown.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { refreshReminders() }
    App(
        modifier = modifier,
        features = features,
        isOffline = state.isOffline,
        pendingReminders = state.pendingReminders,
        rootRoute = state.rootRoute,
        onRetryProfile = { viewModel.onEvent(AppEvent.RetryProfile) },
        profile = { profileModifier, actions -> ProfileRootRoute(profileModifier, actions) },
        trainer = { trainerModifier, actions ->
            TrainerRoute(trainerModifier, actions, nameResolver = koinInject())
        },
        trainerBox = { boxModifier, box, actions, trainerOwner, onBack ->
            // The Trainer tab's instance (already loaded); a fresh one only when its entry is gone.
            val trainerModel = if (trainerOwner != null) {
                koinViewModel<TrainerViewModel>(viewModelStoreOwner = trainerOwner)
            } else {
                koinViewModel<TrainerViewModel>()
            }
            TrainerBoxRoot(box, boxModifier, actions, onBack, trainerModel, nameResolver = koinInject())
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
        preferences = { hubModifier, onBack, onOpenSection, onOpenMeals ->
            PreferencesHubRoot(
                onBack = onBack,
                actions = PreferencesHubActions({ onOpenSection(it.key) }, onOpenMeals),
                modifier = hubModifier,
            )
        },
        preferencesSection = { sectionModifier, onClose -> PreferencesSectionRoot(onClose, sectionModifier) },
        planProposal = { planModifier, onBack, onOpenBeforeStart, onFix ->
            PlanProposalRoot(onBack, onOpenBeforeStart, onFix, planModifier)
        },
        beforeStart = { startModifier, onBack, onCreated, onOpenYourData, onFix, onOpenReview ->
            BeforeStartRoot(onBack, onCreated, onOpenYourData, onFix, startModifier, onOpenFolderBlock = onOpenReview)
        },
        yourData = { dataModifier, onBack, onFix -> YourDataRoot(onBack, onFix, dataModifier) },
        intake = { intakeModifier, host -> IntakeRoot(host, intakeModifier) },
        freeTextLines = { linesModifier, onBack -> FreeTextLinesRoot(onBack, linesModifier) },
        blockPreview = { blockModifier, planId, onOpenExercise, onBack ->
            BlockPreviewRoot(planId, onBack, onOpenExercise, blockModifier)
        },
        folderBlock = { blockModifier, planId, folderId, onSaved, onClose, onOpenExercise ->
            FolderBlockRoot(planId, folderId, onSaved, onClose, onOpenExercise, blockModifier)
        },
        folderReviewResult = { resultModifier, onBack -> FolderReviewResultRoot(onBack, resultModifier) },
        blockBuilder = { builderModifier, planId, previousPlanId, onSaved, onClose, onOpenExercise, onOpenMyTraining ->
            BlockBuilderRoot(
                planId,
                onSaved,
                onClose,
                onOpenExercise,
                builderModifier,
                previousPlanId,
                onOpenMyTraining,
            )
        },
        about = { aboutModifier, onBack -> AboutRoot(onBack, aboutModifier) },
        activity = { activityModifier, onBack -> ActivityRoot(onBack, activityModifier) },
        exercisePicker = { pickerModifier, selectMode, onOpenDetail, onSelected, onBack ->
            ExercisePickerRoot(selectMode, onOpenDetail, onSelected, pickerModifier, onBack)
        },
        exerciseDetail = { detailModifier, exerciseId, selectMode, onSelect, onOpenProgress, onClose, onOpenExercise ->
            ExerciseDetailRoot(
                exerciseId,
                selectMode,
                onSelect,
                detailModifier,
                onOpenProgress,
                onClose,
                onOpenExercise,
            )
        },
        routines = {
                routinesModifier,
                onOpenBuilder,
                onOpenDetails,
                onOpenLive,
                onOpenHistory,
                onOpenReview,
                onSearch,
            ->
            WorkoutEntryRoot(
                onOpenBuilder,
                onOpenDetails,
                onOpenLive,
                onOpenHistory,
                routinesModifier,
                onOpenFolderReview = onOpenReview,
                onOpenSearch = onSearch,
            )
        },
        diary = { diaryModifier, onOpenPreferences, onAddFood, onSaveTemplate, edit, onBack, onQuickSearch ->
            DiaryRoot(
                onOpenPreferences,
                onAddFood,
                { onAddFood(null, it) },
                onSaveTemplate,
                edit,
                diaryModifier,
                onBack,
                onQuickSearch = onQuickSearch,
            )
        },
        weight = { weightModifier, openEntry, onOpenHistory, onBack ->
            WeightRoot(
                onOpenHistory,
                onBack,
                weightModifier,
                openEntry = openEntry,
                onRemindersChange = refreshReminders,
            )
        },
        notificationCenter = { centerModifier, onOpenReminder, onOpenReports, onBack ->
            NotificationCenterRoot(onOpenReminder, onBack, centerModifier, onOpenReports)
        },
        reports = { reportsModifier, onOpen, onBack -> ReportsRoot(onOpen, onBack, reportsModifier) },
        pastReport = { reportModifier, reportId, onBack -> PastReportRoot(reportId, onBack, reportModifier) },
        checkIn = { checkInModifier, onClose -> CheckInRoot(onClose, checkInModifier) },
        weekSummary = { summaryModifier, onBack -> WeekSummaryRoot(onBack, summaryModifier) },
        blockReview = { reviewModifier, onBack, onOpenBeforeStart ->
            BlockReviewRoot(onBack, onOpenBeforeStart, reviewModifier)
        },
        weightHistory = { historyModifier, onBack ->
            WeightHistoryRoot(onBack, historyModifier, onRemindersChange = refreshReminders)
        },
        diaryHistory = { historyModifier, onOpenDay, onBack -> DiaryHistoryRoot(onOpenDay, onBack, historyModifier) },
        mealSlots = { mealsModifier, onClose, onOpenDefaults -> MealSlotsRoot(onClose, onOpenDefaults, mealsModifier) },
        mealDefaults = { defaultsModifier, onClose -> MealDefaultsRoot(onClose, defaultsModifier) },
        foodSearch = { searchModifier, slotId, date, onOpenFood, onCreateFood, onOpenTemplate, pick, viewOnly, onBack ->
            FoodSearchRoot(
                slotId,
                date,
                onOpenFood,
                onCreateFood,
                onOpenTemplate,
                onBack,
                searchModifier,
                pick,
                viewOnly,
            )
        },
        createFood = { formModifier, barcode, name, prefill, retryToken, editFoodId, onBack, onSaved ->
            if (editFoodId != null) {
                EditFoodRoot(editFoodId, onSaved, onBack, formModifier)
            } else {
                CreateFoodRoot(barcode, name, onSaved, onBack, formModifier, prefill, retryToken)
            }
        },
        addFood = { route, onAdded, onDismiss, onEdit, onPick, onCorrectOff ->
            AddFoodRoot(
                route.foodId,
                route.slotId,
                route.date,
                onAdded,
                onDismiss,
                onEdit = onEdit,
                pick = route.pick,
                viewOnly = route.viewOnly,
                onPick = { onPick(it.foodId, it.amount, it.portionLabel) },
                onCorrectOff = { onCorrectOff(it.prefill, it.barcode) },
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
                focusExerciseId = host.focusExerciseId,
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
