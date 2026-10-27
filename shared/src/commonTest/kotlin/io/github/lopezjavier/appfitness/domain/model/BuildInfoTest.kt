package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildInfoTest {
    @Test
    fun flavorLabel_inProd_isNull() {
        val info = BuildInfo("AppFitness", "1.0.0", 3L, "prod")

        assertTrue(info.isProd)
        assertNull(info.flavorLabel)
    }

    @Test
    fun flavorLabel_inLocal_isTheFlavor() {
        val info = BuildInfo("AppFitness", "1.0.0", 3L, "local")

        assertFalse(info.isProd)
        assertEquals("local", info.flavorLabel)
    }

    @Test
    fun flavorLabel_whenFlavorIsBlank_isNull() {
        assertNull(BuildInfo("AppFitness", "1.0.0", 3L, "").flavorLabel)
    }
}
