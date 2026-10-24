package io.github.lopezjavier.appfitness.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.port.AlertSound
import io.github.lopezjavier.appfitness.domain.port.AuthCredentialProvider
import io.github.lopezjavier.appfitness.domain.port.BarcodeScanner
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.port.Haptics
import io.github.lopezjavier.appfitness.domain.port.HealthDataSource
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.CircumferenceRepository
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import io.github.lopezjavier.appfitness.domain.repository.HealthSyncRepository
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.repository.StrengthRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.repository.WeightChartPreferences
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.AddTemplateToDiary
import io.github.lopezjavier.appfitness.domain.usecase.Bmi
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.domain.usecase.BuildTemplateItems
import io.github.lopezjavier.appfitness.domain.usecase.CompleteOnboarding
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.DeleteAccount
import io.github.lopezjavier.appfitness.domain.usecase.GetAccountProtection
import io.github.lopezjavier.appfitness.domain.usecase.HealthWriteBack
import io.github.lopezjavier.appfitness.domain.usecase.InterpretMetric
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
import io.github.lopezjavier.appfitness.domain.usecase.RecalculateEntry
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.SavePreferences
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.SearchExercises
import io.github.lopezjavier.appfitness.domain.usecase.SignInWithGoogle
import io.github.lopezjavier.appfitness.domain.usecase.SyncHealthMeasurements
import io.github.lopezjavier.appfitness.domain.usecase.TargetDistance
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.ValidateCircumference
import io.github.lopezjavier.appfitness.domain.usecase.ValidateMealSlots
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.domain.usecase.WaistHipRatio
import io.github.lopezjavier.appfitness.domain.usecase.WeightTrend
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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
    @Suppress("LongMethod") // One list of extra types per module.
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
                HealthDataSource::class,
                AuthCredentialProvider::class,
                ExerciseLibrary::class,
                DataStore::class,
                TimeZone::class,
            ),
        )
        uiModule.verify(
            extraTypes =
            listOf(
                ConnectivityObserver::class,
                Haptics::class,
                BarcodeScanner::class,
                AlertSound::class,
                AuthRepository::class,
                ProfileRepository::class,
                BodyMeasurementRepository::class,
                NutritionGoalsRepository::class,
                TrainingPreferencesRepository::class,
                MealSlotRepository::class,
                FoodDiaryRepository::class,
                FoodRepository::class,
                BuildDiaryDay::class,
                ResolvePortion::class,
                RecalculateEntry::class,
                ScaleNutrition::class,
                ValidateQuantity::class,
                ValidateMealSlots::class,
                DefaultMealSlot::class,
                LocalDate::class,
                CompleteOnboarding::class,
                DeleteAccount::class,
                GetAccountProtection::class,
                SavePreferences::class,
                Clock::class,
                SavedStateHandle::class,
                ExerciseRepository::class,
                RoutineRepository::class,
                WorkoutSessionRepository::class,
                StrengthRepository::class,
                LoadExerciseCatalog::class,
                SearchExercises::class,
                Boolean::class,
                String::class,
                SignInWithGoogle::class,
                AppConfig::class,
                MealTemplateRepository::class,
                AddTemplateToDiary::class,
                BuildTemplateItems::class,
                ValidateTemplateName::class,
                MeasurementsByDay::class,
                SaveBodyMeasurement::class,
                HealthWriteBack::class,
                HealthDataSource::class,
                SyncHealthMeasurements::class,
                HealthSyncRepository::class,
                ValidateBodyMeasurement::class,
                CircumferenceRepository::class,
                ValidateCircumference::class,
                WaistHipRatio::class,
                Bmi::class,
                TargetDistance::class,
                InterpretMetric::class,
                WeightChartPreferences::class,
                WeightTrend::class,
            ),
        )
    }
}
