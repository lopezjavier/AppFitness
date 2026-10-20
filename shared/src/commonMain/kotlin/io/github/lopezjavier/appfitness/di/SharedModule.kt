package io.github.lopezjavier.appfitness.di

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.SystemClock
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.data.repository.SupabaseAccountRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseAuthRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseBodyMeasurementRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseExerciseRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseFoodDiaryRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseFoodRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseMealSlotRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseNutritionGoalsRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseProfileRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseRoutineRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseStrengthRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseWorkoutSessionRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredAccountRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredAuthRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredBodyMeasurementRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredExerciseRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredFoodDiaryRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredFoodRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredMealSlotRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredNutritionGoalsRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredProfileRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredRoutineRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredStrengthRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredWorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.AccountRepository
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.repository.StrengthRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.domain.usecase.CompleteOnboarding
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.DeleteAccount
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.SavePreferences
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.SearchExercises
import io.github.lopezjavier.appfitness.domain.usecase.SignInWithGoogle
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
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
        single<NutritionGoalsRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseNutritionGoalsRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredNutritionGoalsRepository()
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
                SupabaseWorkoutSessionRepository(client.auth, client.postgrest)
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
        single<MealSlotRepository> {
            if (get<AppConfig>().isSupabaseConfigured) {
                val client = get<SupabaseClient>()
                SupabaseMealSlotRepository(client.auth, client.postgrest)
            } else {
                UnconfiguredMealSlotRepository()
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
        single<Clock> { SystemClock() }
        factory { BuildDiaryDay() }
        factory { ResolvePortion() }
        factory { ScaleNutrition() }
        factory { ValidateQuantity() }
        factory { DefaultMealSlot() }
        factory { SignInWithGoogle(get(), get(), get()) }
        factory { CompleteOnboarding(get(), get()) }
        factory { SavePreferences(get()) }
        factory { DeleteAccount(get(), get()) }
        factory { LoadExerciseCatalog(get(), get()) }
        factory { SearchExercises() }
    }
