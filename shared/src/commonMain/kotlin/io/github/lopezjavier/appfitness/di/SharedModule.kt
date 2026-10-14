package io.github.lopezjavier.appfitness.di

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.SystemClock
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.data.repository.SupabaseAuthRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseBodyMeasurementRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseNutritionGoalsRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseProfileRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredAuthRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredBodyMeasurementRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredNutritionGoalsRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredProfileRepository
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.usecase.CompleteOnboarding
import io.github.lopezjavier.appfitness.domain.usecase.SignInWithGoogle
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
        single<Clock> { SystemClock() }
        factory { SignInWithGoogle(get(), get(), get()) }
        factory { CompleteOnboarding(get(), get()) }
    }
