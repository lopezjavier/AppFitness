package io.github.lopezjavier.appfitness.di

import io.github.lopezjavier.appfitness.app.AppViewModel
import io.github.lopezjavier.appfitness.feature.auth.WelcomeViewModel
import io.github.lopezjavier.appfitness.feature.onboarding.OnboardingViewModel
import io.github.lopezjavier.appfitness.feature.profile.ProfileViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Koin module of the UI layer (ViewModels). */
val uiModule =
    module {
        viewModelOf(::AppViewModel)
        viewModelOf(::WelcomeViewModel)
        viewModelOf(::ProfileViewModel)
        viewModelOf(::OnboardingViewModel)
    }
