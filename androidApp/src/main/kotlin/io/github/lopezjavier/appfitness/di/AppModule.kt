package io.github.lopezjavier.appfitness.di

import io.github.lopezjavier.appfitness.BuildConfig
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.domain.port.AlertSound
import io.github.lopezjavier.appfitness.domain.port.AuthCredentialProvider
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.Haptics
import io.github.lopezjavier.appfitness.platform.AndroidAlertSound
import io.github.lopezjavier.appfitness.platform.AndroidConnectivityObserver
import io.github.lopezjavier.appfitness.platform.AndroidHaptics
import io.github.lopezjavier.appfitness.platform.CurrentActivityHolder
import io.github.lopezjavier.appfitness.platform.GoogleAuthCredentialProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule =
    module {
        single {
            AppConfig(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabasePublishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
                googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
                isLocal = BuildConfig.FLAVOR == "local",
                testLogin = flavorTestLogin(),
            )
        }
        single<ConnectivityObserver> { AndroidConnectivityObserver(androidContext()) }
        single<Haptics> { AndroidHaptics(androidContext()) }
        single<AlertSound> { AndroidAlertSound(androidContext()) }
        single { CurrentActivityHolder() }
        single<AuthCredentialProvider> { GoogleAuthCredentialProvider(get(), get()) }
    }
