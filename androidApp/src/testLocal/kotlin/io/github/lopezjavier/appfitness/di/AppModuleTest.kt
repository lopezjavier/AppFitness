package io.github.lopezjavier.appfitness.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.port.AuthCredentialProvider
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.CompleteOnboarding
import io.github.lopezjavier.appfitness.domain.usecase.DeleteAccount
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.SavePreferences
import io.github.lopezjavier.appfitness.domain.usecase.SearchExercises
import io.github.lopezjavier.appfitness.domain.usecase.SignInWithGoogle
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the Koin graph of the `local` flavor.
 *
 * These tests assume `supabase.local.url` is absent or blank in `local.properties`, so the
 * default `http://10.0.2.2:54321` applies. A custom value makes them fail.
 */
class AppModuleTest {
    @Test
    fun appConfig_inLocalFlavor_resolvesFromBuildConfig() {
        val app = koinApplication { modules(appModule) }
        try {
            val config = app.koin.get<AppConfig>()

            assertEquals("http://10.0.2.2:54321", config.supabaseUrl)
            assertTrue(config.isLocal)
        } finally {
            app.close()
        }
    }

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun koinGraph_isConsistent() {
        appModule.verify(
            extraTypes = listOf(Context::class, String::class, Boolean::class, TestLoginCredentials::class),
        )
        sharedModule.verify(
            extraTypes =
            listOf(
                AppConfig::class,
                ConnectivityObserver::class,
                AuthCredentialProvider::class,
                ExerciseLibrary::class,
            ),
        )
        uiModule.verify(
            extraTypes =
            listOf(
                ConnectivityObserver::class,
                AuthRepository::class,
                ProfileRepository::class,
                BodyMeasurementRepository::class,
                NutritionGoalsRepository::class,
                CompleteOnboarding::class,
                DeleteAccount::class,
                SavePreferences::class,
                Clock::class,
                SavedStateHandle::class,
                ExerciseRepository::class,
                RoutineRepository::class,
                WorkoutSessionRepository::class,
                LoadExerciseCatalog::class,
                SearchExercises::class,
                Boolean::class,
                String::class,
                SignInWithGoogle::class,
                AppConfig::class,
            ),
        )
    }
}
