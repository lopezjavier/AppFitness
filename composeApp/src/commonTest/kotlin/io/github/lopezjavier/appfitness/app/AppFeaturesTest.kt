package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppFeaturesTest {
    private val testLogin = TestLoginCredentials("test@appfitness.local", "pw")

    private fun config(isLocal: Boolean, testLogin: TestLoginCredentials? = null) = AppConfig(
        supabaseUrl = "http://localhost",
        supabasePublishableKey = "sb_publishable_dummy",
        googleWebClientId = "",
        isLocal = isLocal,
        testLogin = testLogin,
    )

    private val nonCatalogRoutes: List<AppRoute> =
        listOf(DiaryGraph, DiaryHome, WorkoutGraph, WorkoutHome, ProgressGraph, ProgressHome, ProfileGraph, ProfileHome)

    @Test
    fun default_disablesCatalog() {
        assertFalse(AppFeatures().componentCatalog)
        assertFalse(AppFeatures().isAvailable(CatalogRoute))
    }

    @Test
    fun from_localConfig_enablesCatalog() {
        val features = AppFeatures.from(config(isLocal = true))
        assertTrue(features.componentCatalog)
        assertTrue(features.isAvailable(CatalogRoute))
    }

    @Test
    fun from_prodConfig_disablesCatalog() {
        val features = AppFeatures.from(config(isLocal = false))
        assertFalse(features.componentCatalog)
        assertFalse(features.isAvailable(CatalogRoute))
    }

    @Test
    fun isAvailable_profileAndAllTabRoutes_alwaysTrue() {
        listOf(true, false).forEach { catalog ->
            nonCatalogRoutes.forEach {
                assertTrue(AppFeatures(catalog).isAvailable(it), "$it catalog=$catalog")
            }
        }
    }

    @Test
    fun testLogin_defaultsToNull_failClosed() {
        assertNull(AppFeatures().testLogin)
        assertNull(AppFeatures.from(config(isLocal = true)).testLogin)
    }

    @Test
    fun from_configWithTestLogin_copiesIt() {
        assertEquals(testLogin, AppFeatures.from(config(isLocal = true, testLogin = testLogin)).testLogin)
    }
}
