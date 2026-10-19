package io.github.lopezjavier.appfitness.di

import io.github.lopezjavier.appfitness.app.AppViewModel
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.feature.analytics.ExerciseProgressViewModel
import io.github.lopezjavier.appfitness.feature.analytics.StrengthViewModel
import io.github.lopezjavier.appfitness.feature.auth.WelcomeViewModel
import io.github.lopezjavier.appfitness.feature.exercises.ComposeResourcesExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseDetailViewModel
import io.github.lopezjavier.appfitness.feature.exercises.ExercisePickerViewModel
import io.github.lopezjavier.appfitness.feature.onboarding.OnboardingViewModel
import io.github.lopezjavier.appfitness.feature.profile.PreferencesViewModel
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootViewModel
import io.github.lopezjavier.appfitness.feature.routines.RoutineBuilderViewModel
import io.github.lopezjavier.appfitness.feature.routines.RoutinesViewModel
import io.github.lopezjavier.appfitness.feature.workout.HistoryViewModel
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionViewModel
import io.github.lopezjavier.appfitness.feature.workout.SessionDetailViewModel
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryViewModel
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
            )
        }
        viewModel { (exerciseId: String) -> ExerciseDetailViewModel(exerciseId, get()) }
        viewModelOf(::RoutinesViewModel)
        viewModelOf(::WorkoutEntryViewModel)
        viewModelOf(::LiveSessionViewModel)
        viewModelOf(::HistoryViewModel)
        viewModel { (sessionId: String) ->
            SessionDetailViewModel(
                sessionId = sessionId,
                repository = get(),
                exerciseRepository = get(),
                exerciseLibrary = get(),
                connectivity = get(),
                clock = get(),
            )
        }
        viewModelOf(::StrengthViewModel)
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
