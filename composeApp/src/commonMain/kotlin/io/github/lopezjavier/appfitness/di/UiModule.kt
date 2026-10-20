package io.github.lopezjavier.appfitness.di

import io.github.lopezjavier.appfitness.app.AppViewModel
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.feature.analytics.ExerciseProgressViewModel
import io.github.lopezjavier.appfitness.feature.analytics.MuscleVolumeViewModel
import io.github.lopezjavier.appfitness.feature.analytics.StrengthViewModel
import io.github.lopezjavier.appfitness.feature.auth.WelcomeViewModel
import io.github.lopezjavier.appfitness.feature.diary.DiaryViewModel
import io.github.lopezjavier.appfitness.feature.diary.EditEntryViewModel
import io.github.lopezjavier.appfitness.feature.exercises.ComposeResourcesExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseDetailViewModel
import io.github.lopezjavier.appfitness.feature.exercises.ExercisePickerViewModel
import io.github.lopezjavier.appfitness.feature.food.AddFoodViewModel
import io.github.lopezjavier.appfitness.feature.food.CreateFoodViewModel
import io.github.lopezjavier.appfitness.feature.food.FoodSearchViewModel
import io.github.lopezjavier.appfitness.feature.meals.MealSlotsEditorViewModel
import io.github.lopezjavier.appfitness.feature.onboarding.OnboardingViewModel
import io.github.lopezjavier.appfitness.feature.profile.PreferencesViewModel
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootViewModel
import io.github.lopezjavier.appfitness.feature.routines.RoutineBuilderViewModel
import io.github.lopezjavier.appfitness.feature.routines.RoutinesViewModel
import io.github.lopezjavier.appfitness.feature.workout.HistoryViewModel
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionViewModel
import io.github.lopezjavier.appfitness.feature.workout.SessionDetailViewModel
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryViewModel
import kotlinx.datetime.LocalDate
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Koin module of the UI layer (ViewModels). */
val uiModule =
    module {
        viewModelOf(::AppViewModel)
        viewModelOf(::WelcomeViewModel)
        viewModelOf(::ProfileRootViewModel)
        // Explicit: the time zone is a defaulted constructor parameter (the device zone), not a dependency.
        viewModel {
            PreferencesViewModel(
                profileRepository = get(),
                goalsRepository = get(),
                bodyMeasurementRepository = get(),
                savePreferences = get(),
                connectivity = get(),
                clock = get(),
                savedState = get(),
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
        // The viewed Diary day travels as an ISO yyyy-MM-dd string.
        viewModel { (date: String) ->
            MealSlotsEditorViewModel(
                viewedDay = LocalDate.parse(date),
                savedState = get(),
                mealSlots = get(),
                foodDiary = get(),
                validate = get(),
                connectivity = get(),
            )
        }
        viewModelOf(::FoodSearchViewModel)
        // A blank slotId means no meal is preselected (Koin parameters cannot be null); date is an ISO yyyy-MM-dd.
        viewModel { (foodId: String, slotId: String, date: String) ->
            AddFoodViewModel(
                foodId = foodId,
                initialSlotId = slotId.ifBlank { null },
                date = LocalDate.parse(date),
                foodRepository = get(),
                foodDiary = get(),
                mealSlots = get(),
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
        viewModel { (barcode: String, name: String) ->
            CreateFoodViewModel(
                barcode = barcode.ifBlank { null },
                name = name.ifBlank { null },
                foodRepository = get(),
                connectivity = get(),
                savedState = get(),
            )
        }
        viewModelOf(::OnboardingViewModel)
        single<ExerciseLibrary> { ComposeResourcesExerciseLibrary() }
        viewModel { (selectMode: Boolean) ->
            ExercisePickerViewModel(
                loadCatalog = get(),
                searchExercises = get(),
                repository = get(),
                connectivity = get(),
                selectMode = selectMode,
                savedState = get(),
            )
        }
        viewModel { (exerciseId: String) -> ExerciseDetailViewModel(exerciseId, get()) }
        viewModelOf(::RoutinesViewModel)
        viewModel { WorkoutEntryViewModel(repository = get(), connectivity = get(), savedState = get()) }
        viewModelOf(::LiveSessionViewModel)
        viewModelOf(::HistoryViewModel)
        viewModel { (sessionId: String) ->
            SessionDetailViewModel(
                sessionId = sessionId,
                savedState = get(),
                repository = get(),
                exerciseRepository = get(),
                exerciseLibrary = get(),
                connectivity = get(),
                clock = get(),
            )
        }
        viewModelOf(::StrengthViewModel)
        viewModelOf(::MuscleVolumeViewModel)
        viewModel { (exerciseId: String) ->
            ExerciseProgressViewModel(
                exerciseId = exerciseId,
                workouts = get(),
                strength = get(),
                exerciseRepository = get(),
                exerciseLibrary = get(),
                clock = get(),
            )
        }
        // A blank routineId means a new routine (Koin parameters cannot be null).
        viewModel { (routineId: String) ->
            RoutineBuilderViewModel(
                routineId = routineId.ifBlank { null },
                savedState = get(),
                repository = get(),
                loadCatalog = get(),
                connectivity = get(),
            )
        }
    }
