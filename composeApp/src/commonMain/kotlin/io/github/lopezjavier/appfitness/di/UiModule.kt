package io.github.lopezjavier.appfitness.di

import io.github.lopezjavier.appfitness.app.AppViewModel
import io.github.lopezjavier.appfitness.app.HealthSyncCoordinator
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.usecase.MarkWeekSummarySeen
import io.github.lopezjavier.appfitness.domain.usecase.ReviewFolderUseCase
import io.github.lopezjavier.appfitness.feature.about.AboutViewModel
import io.github.lopezjavier.appfitness.feature.activity.ActivityViewModel
import io.github.lopezjavier.appfitness.feature.analytics.ExerciseProgressViewModel
import io.github.lopezjavier.appfitness.feature.analytics.MuscleVolumeViewModel
import io.github.lopezjavier.appfitness.feature.analytics.StrengthViewModel
import io.github.lopezjavier.appfitness.feature.analytics.stats.TrainingStatsViewModel
import io.github.lopezjavier.appfitness.feature.auth.WelcomeViewModel
import io.github.lopezjavier.appfitness.feature.checkin.CheckInViewModel
import io.github.lopezjavier.appfitness.feature.diary.DiaryHistoryViewModel
import io.github.lopezjavier.appfitness.feature.diary.DiaryViewModel
import io.github.lopezjavier.appfitness.feature.diary.EditEntryViewModel
import io.github.lopezjavier.appfitness.feature.exercises.ComposeResourcesExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseChatViewModel
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseDetailViewModel
import io.github.lopezjavier.appfitness.feature.exercises.ExercisePickerViewModel
import io.github.lopezjavier.appfitness.feature.food.AddFoodViewModel
import io.github.lopezjavier.appfitness.feature.food.CreateFoodViewModel
import io.github.lopezjavier.appfitness.feature.food.FoodSearchViewModel
import io.github.lopezjavier.appfitness.feature.food.photo.AmountPhotoViewModel
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoRetryStore
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoViewModel
import io.github.lopezjavier.appfitness.feature.meals.MealSlotsEditorViewModel
import io.github.lopezjavier.appfitness.feature.meals.defaults.MealDefaultsViewModel
import io.github.lopezjavier.appfitness.feature.notifications.NotificationCenterViewModel
import io.github.lopezjavier.appfitness.feature.onboarding.OnboardingViewModel
import io.github.lopezjavier.appfitness.feature.profile.AccountViewModel
import io.github.lopezjavier.appfitness.feature.profile.PreferencesViewModel
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootViewModel
import io.github.lopezjavier.appfitness.feature.routines.RoutineBuilderViewModel
import io.github.lopezjavier.appfitness.feature.routines.RoutineDetailsViewModel
import io.github.lopezjavier.appfitness.feature.routines.RoutinesViewModel
import io.github.lopezjavier.appfitness.feature.templates.SaveTemplateViewModel
import io.github.lopezjavier.appfitness.feature.templates.TemplateDetailsViewModel
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartViewModel
import io.github.lopezjavier.appfitness.feature.trainer.BlockBuilderViewModel
import io.github.lopezjavier.appfitness.feature.trainer.BlockPreviewViewModel
import io.github.lopezjavier.appfitness.feature.trainer.BlockReviewViewModel
import io.github.lopezjavier.appfitness.feature.trainer.FolderBlockViewModel
import io.github.lopezjavier.appfitness.feature.trainer.FolderReviewResultViewModel
import io.github.lopezjavier.appfitness.feature.trainer.FreeTextLinesViewModel
import io.github.lopezjavier.appfitness.feature.trainer.IntakeViewModel
import io.github.lopezjavier.appfitness.feature.trainer.PastReportViewModel
import io.github.lopezjavier.appfitness.feature.trainer.PlanProposalViewModel
import io.github.lopezjavier.appfitness.feature.trainer.ReportsViewModel
import io.github.lopezjavier.appfitness.feature.trainer.TrainerViewModel
import io.github.lopezjavier.appfitness.feature.trainer.WeekSummaryViewModel
import io.github.lopezjavier.appfitness.feature.trainer.YourDataViewModel
import io.github.lopezjavier.appfitness.feature.weight.CircumferencesViewModel
import io.github.lopezjavier.appfitness.feature.weight.GoalViewModel
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncViewModel
import io.github.lopezjavier.appfitness.feature.weight.WeightTrendViewModel
import io.github.lopezjavier.appfitness.feature.weight.WeightViewModel
import io.github.lopezjavier.appfitness.feature.workout.EditWorkoutViewModel
import io.github.lopezjavier.appfitness.feature.workout.HistoryViewModel
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionViewModel
import io.github.lopezjavier.appfitness.feature.workout.PastWorkoutChooserViewModel
import io.github.lopezjavier.appfitness.feature.workout.PastWorkoutSource
import io.github.lopezjavier.appfitness.feature.workout.PastWorkoutViewModel
import io.github.lopezjavier.appfitness.feature.workout.SessionDetailViewModel
import io.github.lopezjavier.appfitness.feature.workout.WorkoutCalendarViewModel
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The day handed to the food screens in pick mode (choosing a food for a comida), where it is not used. */
private const val PICK_MODE_DATE = "1970-01-01"

/** Koin module of the UI layer (ViewModels). */
val uiModule =
    module {
        // App-wide singleton: the shell badge and the notification center read the same reminders.
        single { ReminderCoordinator(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
        single { FoodPhotoRetryStore(photoCapture = get()) }
        single {
            HealthSyncCoordinator(get(), get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
        }
        viewModelOf(::AppViewModel)
        viewModelOf(::WelcomeViewModel)
        viewModelOf(::ProfileRootViewModel)
        viewModelOf(::NotificationCenterViewModel)
        viewModel {
            CheckInViewModel(
                get(),
                get(),
                get(),
                get(),
                savedState = get(),
                exercises = get(),
                library = get(),
                exerciseNames = get(),
            )
        }
        viewModelOf(::AccountViewModel)
        viewModelOf(::AboutViewModel)
        // Explicit: the time zone is a defaulted constructor parameter (the device zone), not a dependency.
        viewModel {
            PreferencesViewModel(
                profileRepository = get(),
                goalsRepository = get(),
                bodyMeasurementRepository = get(),
                trainingPreferencesRepository = get(),
                foodPreferencesRepository = get(),
                notificationSettings = get(),
                savePreferences = get(),
                connectivity = get(),
                clock = get(),
                savedState = get(),
                healthWriteBack = get(),
                reminders = get(),
            )
        }
        viewModel {
            DiaryViewModel(
                mealSlots = get(),
                foodDiary = get(),
                goalsRepository = get(),
                buildDiaryDay = get(),
                clock = get(),
                connectivity = get(),
                defaults = get(),
                savedState = get(),
                groupMealEntries = get(),
            )
        }
        viewModel {
            DiaryHistoryViewModel(
                foodDiary = get(),
                goalsRepository = get(),
                clock = get(),
                savedState = get(),
            )
        }
        // The edited entry's day travels as an ISO yyyy-MM-dd string.
        viewModel { (entryId: String, date: String) ->
            EditEntryViewModel(
                entryId = entryId,
                date = LocalDate.parse(date),
                foodRepository = get(),
                foodDiary = get(),
                mealSlots = get(),
                recalculateEntry = get(),
                connectivity = get(),
                savedState = get(),
            )
        }
        // Explicit: the time zone is a defaulted constructor parameter (the device zone), not a dependency.
        viewModel {
            WeightViewModel(
                repository = get(),
                measurementsByDay = get(),
                saveBodyMeasurement = get(),
                validate = get(),
                connectivity = get(),
                clock = get(),
                savedState = get(),
                healthWriteBack = get(),
            )
        }
        viewModel {
            WeightTrendViewModel(
                measurements = get(),
                circumferences = get(),
                goals = get(),
                plans = get(),
                preferences = get(),
                weightTrend = get(),
                clock = get(),
                savedState = get(),
            )
        }
        viewModel {
            GoalViewModel(
                profile = get(),
                measurements = get(),
                goals = get(),
                circumferences = get(),
                bmi = get(),
                interpret = get(),
                waistHipRatio = get(),
                connectivity = get(),
                savedState = get(),
            )
        }
        viewModel {
            HealthSyncViewModel(
                source = get(),
                coordinator = get(),
                repository = get(),
                connectivity = get(),
                clock = get(),
                savedState = get(),
            )
        }
        viewModel {
            CircumferencesViewModel(
                repository = get(),
                validate = get(),
                waistHipRatio = get(),
                connectivity = get(),
                clock = get(),
                savedState = get(),
            )
        }
        viewModel {
            MealDefaultsViewModel(
                mealSlots = get(),
                mealTemplates = get(),
                mealDefaults = get(),
                foodPreferences = get(),
                cookedItemNutrition = get(),
                connectivity = get(),
                savedState = get(),
            )
        }
        viewModel {
            MealSlotsEditorViewModel(
                savedState = get(),
                mealSlots = get(),
                validate = get(),
                connectivity = get(),
            )
        }
        // A blank slotId means no meal is preselected (Koin parameters cannot be null); date is an ISO yyyy-MM-dd.
        // An optional trailing Boolean parameter turns on pick mode (choosing a food for a comida draft, T-102b).
        // The second optional Boolean turns on view-only mode (quick search, no "Añadir").
        viewModel { params ->
            val flags = params.values.filterIsInstance<Boolean>()
            FoodSearchViewModel(
                pick = flags.getOrNull(0) ?: false,
                viewOnly = flags.getOrNull(1) ?: false,
                // FoodSearchRoot passes (slotId, date, pick): the first String is the meal the search adds to.
                slotId = params.getOrNull<String>(),
                mealSlots = get(),
                foodRepository = get(),
                connectivity = get(),
                barcodeScanner = get(),
                mealTemplates = get(),
                foodPreferences = get(),
                cookedItemNutrition = get(),
                savedState = get(),
            )
        }
        // Scoped to the FoodSearchRoute back-stack entry by FoodSearchRoot.
        viewModel {
            FoodPhotoViewModel(
                photoRepository = get(),
                photoCapture = get(),
                connectivity = get(),
                savedState = get(),
                retryStore = get(),
            )
        }
        // Scoped to the add-food route; the food travels as a parameter (US-54).
        viewModel { (foodId: String) ->
            AmountPhotoViewModel(
                foodId = foodId,
                foodRepository = get(),
                photoRepository = get(),
                photoCapture = get(),
                connectivity = get(),
                savedState = get(),
            )
        }
        // The saved meal's day travels as an ISO yyyy-MM-dd string, like the edit sheet.
        viewModel { (slotId: String, date: String) ->
            SaveTemplateViewModel(
                slotId = slotId,
                date = LocalDate.parse(date),
                foodDiary = get(),
                buildTemplateItems = get(),
                validateTemplateName = get(),
                mealTemplates = get(),
                connectivity = get(),
                savedState = get(),
            )
        }
        // A blank slotId means the meal of the time of day (Koin parameters cannot be null); date is ISO yyyy-MM-dd.
        viewModel { (templateId: String, slotId: String, date: String) ->
            TemplateDetailsViewModel(
                templateId = templateId,
                slotId = slotId.ifBlank { null },
                date = LocalDate.parse(date),
                mealTemplates = get(),
                scaleNutrition = get(),
                validateQuantity = get(),
                validateTemplateName = get(),
                foodRepository = get(),
                foodPreferences = get(),
                addTemplateToDiary = get(),
                mealSlots = get(),
                defaultMealSlot = get(),
                clock = get(),
                connectivity = get(),
                savedState = get(),
            )
        }
        // A blank slotId means no meal is preselected (Koin parameters cannot be null); date is an ISO yyyy-MM-dd.
        viewModel { params ->
            val (foodId: String, slotId: String, date: String) = params
            val flags = params.values.filterIsInstance<Boolean>()
            AddFoodViewModel(
                pick = flags.getOrNull(0) ?: false,
                viewOnly = flags.getOrNull(1) ?: false,
                foodId = foodId,
                initialSlotId = slotId.ifBlank { null },
                date = LocalDate.parse(date.ifBlank { PICK_MODE_DATE }),
                foodRepository = get(),
                foodDiary = get(),
                mealSlots = get(),
                foodPreferencesRepository = get(),
                resolvePortion = get(),
                scaleNutrition = get(),
                validateQuantity = get(),
                defaultMealSlot = get(),
                clock = get(),
                connectivity = get(),
                savedState = get(),
            )
        }
        // Blank barcode / name mean "not prefilled" (Koin parameters cannot be null).
        viewModel { params ->
            val (barcode: String, name: String, editFoodId: String) = params
            CreateFoodViewModel(
                barcode = barcode.ifBlank { null },
                name = name.ifBlank { null },
                foodRepository = get(),
                connectivity = get(),
                savedState = get(),
                editFoodId = editFoodId.ifBlank { null },
                prefill = params.get<String>(3).ifBlank { null },
                // The fifth parameter is optional: the token of the photo kept for "Repetir la estimación".
                retryToken = (params.values.getOrNull(4) as? String)?.ifBlank { null },
                photoRepository = get(),
                photoCapture = get(),
                retryStore = get(),
            )
        }
        viewModelOf(::OnboardingViewModel)
        single<ExerciseLibrary> { ComposeResourcesExerciseLibrary() }
        viewModel { (selectMode: Boolean) ->
            ExercisePickerViewModel(
                loadCatalog = get(),
                searchExercises = get(),
                repository = get(),
                favorites = get(),
                connectivity = get(),
                selectMode = selectMode,
                savedState = get(),
            )
        }
        viewModel { (exerciseId: String) ->
            ExerciseDetailViewModel(exerciseId, get(), get(), get(), get(), get(), get())
        }
        viewModel { ExerciseChatViewModel(repository = get(), connectivity = get(), savedState = get()) }
        viewModelOf(::RoutinesViewModel)
        viewModel { WorkoutEntryViewModel(repository = get(), connectivity = get(), savedState = get()) }
        viewModel {
            LiveSessionViewModel(
                repository = get(),
                exerciseRepository = get(),
                exerciseLibrary = get(),
                connectivity = get(),
                clock = get(),
                haptics = get(),
                routineRepository = get(),
                alertSound = get(),
                preferences = get(),
                bodyMeasurements = get(),
                saveBodyMeasurement = get(),
                savedState = get(),
                restAlertScheduler = get(),
                notificationPermission = get(),
                restAlertPreferences = get(),
                discomfortRepository = get(),
                progressionRepository = get(),
                deloadRepository = get(),
                coachChanges = get(),
                exerciseNames = get(),
                coachPlans = get(),
                coachBlocks = get(),
            )
        }
        viewModelOf(::HistoryViewModel)
        viewModel {
            WorkoutCalendarViewModel(
                repository = get(),
                preferences = get(),
                clock = get(),
                bodyMeasurements = get(),
                exercises = get(),
                savedState = get(),
            )
        }
        viewModel { (sessionId: String) ->
            SessionDetailViewModel(
                sessionId = sessionId,
                savedState = get(),
                repository = get(),
                exerciseRepository = get(),
                exerciseLibrary = get(),
                connectivity = get(),
                clock = get(),
                preferences = get(),
                bodyMeasurements = get(),
                routineRepository = get(),
                exerciseNames = get(),
            )
        }
        viewModel { (sessionId: String) ->
            EditWorkoutViewModel(
                sessionId = sessionId,
                repository = get(),
                exerciseRepository = get(),
                exerciseLibrary = get(),
                connectivity = get(),
                clock = get(),
                savedState = get(),
                exerciseNames = get(),
            )
        }
        // Blank ids mean "none" (Koin parameters cannot be null): both blank = an empty workout.
        // `date` is an ISO yyyy-MM-dd day or blank (none).
        viewModel { (routineId: String, sessionId: String, date: String) ->
            PastWorkoutViewModel(
                source = when {
                    routineId.isNotBlank() -> PastWorkoutSource.FromRoutine(routineId)
                    sessionId.isNotBlank() -> PastWorkoutSource.FromSession(sessionId)
                    else -> PastWorkoutSource.Empty
                },
                repository = get(),
                routineRepository = get(),
                exerciseRepository = get(),
                exerciseLibrary = get(),
                connectivity = get(),
                clock = get(),
                date = date.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                savedState = get(),
                exerciseNames = get(),
            )
        }
        viewModel { PastWorkoutChooserViewModel(routineRepository = get(), savedState = get()) }
        viewModelOf(::StrengthViewModel)
        viewModel { ActivityViewModel(get(), get(), TimeZone.currentSystemDefault(), get(), get()) }
        viewModelOf(::MuscleVolumeViewModel)
        viewModel {
            WeekSummaryViewModel(
                summaries = get(),
                markSeen = MarkWeekSummarySeen(get()),
                recordPausedWeek = get(),
                reminders = get(),
                names = get(),
                clock = get(),
                savedState = get(),
                timeZone = TimeZone.currentSystemDefault(),
            )
        }
        viewModel { ReportsViewModel(loadReports = get()) }
        viewModel { params -> PastReportViewModel(reportId = params.get(), loadReport = get(), names = get()) }
        viewModel {
            TrainerViewModel(
                repository = get(),
                clock = get(),
                plans = get(),
                blocks = get(),
                coachWeeks = get(),
                savedState = get(),
                goals = get(),
                profile = get(),
                measurements = get(),
                coachChanges = get(),
                decideProposal = get(),
                summaries = get(),
                loadBlockReview = get(),
                loadWeeksInPhase = get(),
                loadWelcomeBack = get(),
                discomfortFollowUps = get(),
                exerciseNames = get(),
            )
        }
        viewModel {
            BlockReviewViewModel(
                loadReview = get(),
                decidePhase = get(),
                plans = get(),
                decideCalorie = get(),
                markSeen = get(),
                reminders = get(),
                names = get(),
                clock = get(),
                savedState = get(),
            )
        }
        viewModel {
            BeforeStartViewModel(
                coach = get(),
                intakeRepo = get(),
                clock = get(),
                routines = get(),
                savedState = get(),
                openNewBlock = get(),
                previewGoalSwitch = get(),
            )
        }
        viewModel { YourDataViewModel(coach = get(), intakeRepo = get(), clock = get(), savedState = get()) }
        viewModel { IntakeViewModel(repository = get(), savedState = get()) }
        viewModel { FreeTextLinesViewModel(repository = get(), clock = get()) }
        viewModel { params ->
            BlockBuilderViewModel(
                planId = params.get(),
                builder = get(),
                catalog = get(),
                favourites = get(),
                exercises = get(),
                preferences = get(),
                plans = get(),
                clock = get(),
                previousPlanId = params.values.getOrNull(1) as? String,
                nextBlock = get(),
                savedState = get(),
            )
        }
        viewModel { params ->
            BlockPreviewViewModel(
                planId = params.get(),
                blocks = get(),
                exercises = get(),
                library = get(),
                preferences = get(),
                favorites = get(),
                savedState = get(),
                exerciseNames = get(),
            )
        }
        viewModel { params ->
            FolderBlockViewModel(
                planId = params.get(0),
                folderId = params.get(1),
                buildBlock = get(),
                catalog = get(),
                favourites = get(),
                plans = get(),
                clock = get(),
                savedState = get(),
            )
        }
        viewModel {
            val reviewFolder: ReviewFolderUseCase = get()
            FolderReviewResultViewModel(
                review = { reviewFolder(it) },
                names = get(),
                routines = get(),
                savedState = get(),
            )
        }
        viewModel {
            PlanProposalViewModel(plans = get(), coach = get(), clock = get(), savedState = get(), intakeRepo = get())
        }
        viewModel {
            TrainingStatsViewModel(
                stats = get(),
                exerciseRepository = get(),
                exerciseLibrary = get(),
                clock = get(),
                savedState = get(),
                exerciseNames = get(),
            )
        }
        viewModel { (exerciseId: String) ->
            ExerciseProgressViewModel(
                exerciseId = exerciseId,
                workouts = get(),
                strength = get(),
                exerciseRepository = get(),
                exerciseLibrary = get(),
                clock = get(),
                preferences = get(),
                savedState = get(),
                exerciseNames = get(),
            )
        }
        viewModel { (routineId: String) ->
            RoutineDetailsViewModel(
                routineId = routineId,
                routines = get(),
                sessions = get(),
                loadCatalog = get(),
                connectivity = get(),
            )
        }
        // A blank routineId means a new routine (Koin parameters cannot be null). The optional second parameter is
        // the exercise whose rep range gets the focus (blank or missing = none).
        viewModel { params ->
            RoutineBuilderViewModel(
                routineId = params.get<String>(0).ifBlank { null },
                savedState = get(),
                repository = get(),
                loadCatalog = get(),
                connectivity = get(),
                preferences = get(),
                focusExerciseId = (params.values.getOrNull(1) as? String)?.ifBlank { null },
                coachChanges = get(),
            )
        }
    }
