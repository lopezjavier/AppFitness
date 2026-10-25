package io.github.lopezjavier.appfitness.di

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.SystemClock
import io.github.lopezjavier.appfitness.data.local.DataStoreWeightChartPreferences
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.data.repository.SupabaseAccountRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseAuthRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseBodyMeasurementRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseCircumferenceRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseExerciseRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseFoodDiaryRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseFoodPhotoRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseFoodPreferencesRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseFoodRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseHealthSyncRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseMealDefaultsRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseMealSlotRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseMealTemplateRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseNutritionGoalsRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseProfileRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseRoutineRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseStrengthRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseTrainingStatsRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseWorkoutSessionRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredAccountRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredAuthRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredBodyMeasurementRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredCircumferenceRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredExerciseRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredFoodDiaryRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredFoodPhotoRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredFoodPreferencesRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredFoodRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredHealthSyncRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredMealDefaultsRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredMealSlotRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredMealTemplateRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredNutritionGoalsRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredProfileRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredRoutineRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredStrengthRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredTrainingStatsRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredWorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.AccountRepository
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.CircumferenceRepository
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodPhotoRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodPreferencesRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import io.github.lopezjavier.appfitness.domain.repository.HealthSyncRepository
import io.github.lopezjavier.appfitness.domain.repository.MealDefaultsRepository
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.repository.StrengthRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingStatsRepository
import io.github.lopezjavier.appfitness.domain.repository.WeightChartPreferences
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.AddTemplateToDiary
import io.github.lopezjavier.appfitness.domain.usecase.Bmi
import io.github.lopezjavier.appfitness.domain.usecase.BuildCookedEntries
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.domain.usecase.BuildTemplateItems
import io.github.lopezjavier.appfitness.domain.usecase.CompleteOnboarding
import io.github.lopezjavier.appfitness.domain.usecase.CookedItemNutrition
import io.github.lopezjavier.appfitness.domain.usecase.CurrentWeight
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.DeleteAccount
import io.github.lopezjavier.appfitness.domain.usecase.GetAccountProtection
import io.github.lopezjavier.appfitness.domain.usecase.GroupMealEntries
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
import kotlinx.datetime.TimeZone
import org.koin.dsl.module

/**
 * Koin module of the shared data and domain layers. The Supabase client is built lazily, so a blank
 * configuration (no URL or key) never touches it: the unconfigured repositories are used instead.
 */
val sharedModule =
    module {
        single<SupabaseClient> { SupabaseClientFactory.create(get<AppConfig>()) }
        single<AuthRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                SupabaseAuthRepository(get<SupabaseClient>().auth)
            } else {
                UnconfiguredAuthRepository()
            }
        }
        single<ProfileRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseProfileRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredProfileRepository()
            }
        }
        single<BodyMeasurementRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseBodyMeasurementRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredBodyMeasurementRepository()
            }
        }
        single<HealthSyncRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseHealthSyncRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredHealthSyncRepository()
            }
        }
        single<CircumferenceRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseCircumferenceRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredCircumferenceRepository()
            }
        }
        single<NutritionGoalsRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseNutritionGoalsRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredNutritionGoalsRepository()
            }
        }
        single<TrainingPreferencesRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseTrainingPreferencesRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredTrainingPreferencesRepository()
            }
        }
        single<FoodPreferencesRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseFoodPreferencesRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredFoodPreferencesRepository()
            }
        }
        single<AccountRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                SupabaseAccountRepository(get<SupabaseClient>().functions)
            } else {
                UnconfiguredAccountRepository()
            }
        }
        single<ExerciseRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseExerciseRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredExerciseRepository()
            }
        }
        single<RoutineRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseRoutineRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredRoutineRepository()
            }
        }
        single<WorkoutSessionRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseWorkoutSessionRepository(client.auth, client.postgrest, get())
            } else {
                UnconfiguredWorkoutSessionRepository()
            }
        }
        single<StrengthRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseStrengthRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredStrengthRepository()
            }
        }
        single<TrainingStatsRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseTrainingStatsRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredTrainingStatsRepository()
            }
        }
        single<MealSlotRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseMealSlotRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredMealSlotRepository()
            }
        }
        single<MealTemplateRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseMealTemplateRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredMealTemplateRepository()
            }
        }
        single<MealDefaultsRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseMealDefaultsRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredMealDefaultsRepository()
            }
        }
        single<FoodDiaryRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseFoodDiaryRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredFoodDiaryRepository()
            }
        }
        single<FoodRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseFoodRepository(client.auth, client.postgrest, client.functions, get())
            } else {
                UnconfiguredFoodRepository()
            }
        }
        single<FoodPhotoRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseFoodPhotoRepository(client.auth, client.functions)
            } else {
                UnconfiguredFoodPhotoRepository()
            }
        }
        single<Clock> { SystemClock() }
        single<WeightChartPreferences> { DataStoreWeightChartPreferences(get()) }
        factory { WeightTrend() }
        factory { BuildDiaryDay() }
        factory { GroupMealEntries() }
        factory { ResolvePortion() }
        factory { RecalculateEntry(get(), get(), get()) }
        factory { ScaleNutrition() }
        factory { ValidateQuantity() }
        factory { DefaultMealSlot() }
        factory { ValidateTemplateName() }
        factory { BuildTemplateItems() }
        factory { BuildCookedEntries(get()) }
        factory { CookedItemNutrition(get()) }
        factory { AddTemplateToDiary(get(), get(), get()) }
        factory { ValidateMealSlots() }
        factory { SignInWithGoogle(get(), get(), get()) }
        factory { CompleteOnboarding(get(), get()) }
        factory { SavePreferences(get()) }
        factory { DeleteAccount(get(), get()) }
        factory { GetAccountProtection(get()) }
        factory { LoadExerciseCatalog(get(), get()) }
        factory { SearchExercises() }
        factory { ValidateBodyMeasurement() }
        factory { ValidateCircumference() }
        factory { WaistHipRatio() }
        factory { Bmi() }
        factory { TargetDistance() }
        factory { InterpretMetric(get()) }
        factory { MeasurementsByDay() }
        factory { CurrentWeight() }
        // The local day boundaries follow the device time zone, which is not a Koin dependency.
        // The HealthDataSource port is bound by the platform module.
        factory { HealthWriteBack(get(), get(), get()) }
        factory { SyncHealthMeasurements(get(), get(), get()) }
        factory { SaveBodyMeasurement(get(), get(), TimeZone.currentSystemDefault(), get(), get()) }
    }
