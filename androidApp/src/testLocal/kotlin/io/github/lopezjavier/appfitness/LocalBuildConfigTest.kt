package io.github.lopezjavier.appfitness

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalBuildConfigTest {
    @Test
    fun supabaseUrl_inLocalFlavor_isTheEmulatorHostAddress() {
        assertEquals("http://10.0.2.2:54321", BuildConfig.SUPABASE_URL)
    }

    @Test
    fun applicationIdAndFlavor_inLocalFlavor_useTheLocalSuffix() {
        assertEquals("io.github.lopezjavier.appfitness.local", BuildConfig.APPLICATION_ID)
        assertEquals("local", BuildConfig.FLAVOR)
    }

    @Test
    fun buildConfigEscapeProbe_withQuotesBackslashAndLineBreaks_roundTripsExactly() {
        assertEquals("a\"b\\c\nd\re", BuildConfig.BUILD_CONFIG_ESCAPE_PROBE)
    }
}
