package io.github.lopezjavier.appfitness.config

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppConfigTest {
    private val configured =
        AppConfig(
            supabaseUrl = "https://example.supabase.co",
            supabasePublishableKey = "sb_publishable_dummy",
            googleWebClientId = "",
            isLocal = false,
        )

    @Test
    fun isSupabaseConfigured_whenUrlAndKeyAreNotBlank_isTrue() {
        assertTrue(configured.isSupabaseConfigured)
    }

    @Test
    fun isSupabaseConfigured_whenUrlIsBlank_isFalse() {
        assertFalse(configured.copy(supabaseUrl = "").isSupabaseConfigured)
        assertFalse(configured.copy(supabaseUrl = "  ").isSupabaseConfigured)
    }

    @Test
    fun isSupabaseConfigured_whenKeyIsBlank_isFalse() {
        assertFalse(configured.copy(supabasePublishableKey = "").isSupabaseConfigured)
        assertFalse(configured.copy(supabasePublishableKey = "  ").isSupabaseConfigured)
    }
}
