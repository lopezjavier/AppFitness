package io.github.lopezjavier.appfitness.config

/**
 * Per-environment configuration resolved at build time by the Android application module
 * (flavor `BuildConfig` fields) and provided to the rest of the app through Koin.
 */
data class AppConfig(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
    val googleWebClientId: String,
    val isLocal: Boolean,
) {
    /** True only when both the Supabase URL and the publishable key are set. */
    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && supabasePublishableKey.isNotBlank()
}
