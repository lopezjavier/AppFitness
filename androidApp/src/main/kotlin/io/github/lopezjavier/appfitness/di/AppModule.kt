package io.github.lopezjavier.appfitness.di

import io.github.lopezjavier.appfitness.BuildConfig
import io.github.lopezjavier.appfitness.config.AppConfig
import org.koin.dsl.module

val appModule =
    module {
        single {
            AppConfig(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabasePublishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
                googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
                isLocal = BuildConfig.FLAVOR == "local",
            )
        }
    }
