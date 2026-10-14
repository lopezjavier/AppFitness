package io.github.lopezjavier.appfitness.config

import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials

/**
 * Per-environment configuration resolved at build time by the Android application module
 * (flavor `BuildConfig` fields) and provided to the rest of the app through Koin.
 *
 * [testLogin] is non-null only in the `local` flavor (planning/08-environment-setup.md §3.2); the default
 * is null so every other construction fails closed.
 */
data class AppConfig(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
    val googleWebClientId: String,
    val isLocal: Boolean,
    val testLogin: TestLoginCredentials? = null,
) {
    /** True only when both the Supabase URL and the publishable key are set. */
    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && supabasePublishableKey.isNotBlank()
}
