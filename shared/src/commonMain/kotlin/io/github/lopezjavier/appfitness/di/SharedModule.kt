package io.github.lopezjavier.appfitness.di

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.data.repository.SupabaseAuthRepository
import io.github.lopezjavier.appfitness.data.repository.SupabaseProfileRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredAuthRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredProfileRepository
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
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
        factory { SignInWithGoogle(get(), get(), get()) }
    }
