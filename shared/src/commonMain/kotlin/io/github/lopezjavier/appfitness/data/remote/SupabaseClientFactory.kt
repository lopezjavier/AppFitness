package io.github.lopezjavier.appfitness.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.ktor.client.engine.HttpClientEngine

/**
 * Builds the single Supabase client from [AppConfig] (URL + publishable key; ADR-17) with Auth and Postgrest.
 * [engine] and [sessionManager] are test seams: a custom session manager is never auto-loaded from storage.
 */
object SupabaseClientFactory {
    fun create(
        config: AppConfig,
        engine: HttpClientEngine? = null,
        sessionManager: SessionManager? = null,
    ): SupabaseClient = createSupabaseClient(config.supabaseUrl, config.supabasePublishableKey) {
        if (engine != null) httpEngine = engine
        install(Auth) {
            if (sessionManager != null) {
                this.sessionManager = sessionManager
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                // No Android lifecycle observer: host tests have no main Looper.
                enableLifecycleCallbacks = false
            }
        }
        install(Postgrest)
    }
}
