package io.github.lopezjavier.appfitness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProdBuildConfigTest {
    @Test
    fun applicationIdAndFlavor_inProdFlavor_haveNoSuffix() {
        assertEquals("io.github.lopezjavier.appfitness", BuildConfig.APPLICATION_ID)
        assertEquals("prod", BuildConfig.FLAVOR)
    }

    @Test
    fun supabaseUrl_inProdFlavor_isBlankOrHttps() {
        // The value itself is never asserted or printed (public CI logs).
        val url = BuildConfig.SUPABASE_URL
        assertTrue(url.isBlank() || url.startsWith("https://"), "prod SUPABASE_URL must be blank or use https")
    }
}
