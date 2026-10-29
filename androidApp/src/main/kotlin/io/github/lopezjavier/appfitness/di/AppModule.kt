package io.github.lopezjavier.appfitness.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.lopezjavier.appfitness.BuildConfig
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.domain.model.BuildInfo
import io.github.lopezjavier.appfitness.domain.port.AlertSound
import io.github.lopezjavier.appfitness.domain.port.AuthCredentialProvider
import io.github.lopezjavier.appfitness.domain.port.BarcodeScanner
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.Haptics
import io.github.lopezjavier.appfitness.domain.port.HealthDataSource
import io.github.lopezjavier.appfitness.domain.port.NotificationPermission
import io.github.lopezjavier.appfitness.domain.port.PhotoCapture
import io.github.lopezjavier.appfitness.domain.port.RestAlertScheduler
import io.github.lopezjavier.appfitness.platform.AndroidAlertSound
import io.github.lopezjavier.appfitness.platform.AndroidConnectivityObserver
import io.github.lopezjavier.appfitness.platform.AndroidHaptics
import io.github.lopezjavier.appfitness.platform.AndroidNotificationPermission
import io.github.lopezjavier.appfitness.platform.AndroidPhotoCapture
import io.github.lopezjavier.appfitness.platform.AndroidRestAlertScheduler
import io.github.lopezjavier.appfitness.platform.CurrentActivityHolder
import io.github.lopezjavier.appfitness.platform.GoogleAuthCredentialProvider
import io.github.lopezjavier.appfitness.platform.HealthConnectDataSource
import io.github.lopezjavier.appfitness.platform.MlKitBarcodeScanner
import io.github.lopezjavier.appfitness.platform.RestAlertHandler
import io.github.lopezjavier.appfitness.platform.RestAlertNotifier
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
        single {
            BuildInfo(
                appName = "AppFitness",
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                flavor = BuildConfig.FLAVOR,
            )
        }
        single<ConnectivityObserver> { AndroidConnectivityObserver(androidContext()) }
        single<Haptics> { AndroidHaptics(androidContext()) }
        single<AlertSound> { AndroidAlertSound(androidContext()) }
        single<RestAlertScheduler> { AndroidRestAlertScheduler(androidContext()) }
        single { AndroidNotificationPermission(androidContext()) }
        single<NotificationPermission> { get<AndroidNotificationPermission>() }
        single { RestAlertNotifier(androidContext()) }
        single { RestAlertHandler(get(), isForeground = { get<CurrentActivityHolder>().get() != null }) }
        single<DataStore<Preferences>> {
            PreferenceDataStoreFactory.create {
                androidContext().filesDir.resolve("datastore/appfitness.preferences_pb")
            }
        }
        single<HealthDataSource> { HealthConnectDataSource(androidContext()) }
        single { CurrentActivityHolder() }
        single<BarcodeScanner> { MlKitBarcodeScanner(get()) }
        single<PhotoCapture> { AndroidPhotoCapture(androidContext().cacheDir, get()) }
        single<AuthCredentialProvider> { GoogleAuthCredentialProvider(get(), get()) }
    }
