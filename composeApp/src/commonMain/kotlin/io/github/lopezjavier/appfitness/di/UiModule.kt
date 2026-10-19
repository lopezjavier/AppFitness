package io.github.lopezjavier.appfitness.di

import io.github.lopezjavier.appfitness.app.AppViewModel
import io.github.lopezjavier.appfitness.feature.auth.WelcomeViewModel
import io.github.lopezjavier.appfitness.feature.onboarding.OnboardingViewModel
import io.github.lopezjavier.appfitness.feature.profile.PreferencesViewModel
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootViewModel
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
    }
