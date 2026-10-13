package io.github.lopezjavier.appfitness.di

import io.github.lopezjavier.appfitness.app.AppViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Koin module of the UI layer (ViewModels). */
val uiModule =
    module {
        viewModelOf(::AppViewModel)
    }
