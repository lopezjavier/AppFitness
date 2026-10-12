package io.github.lopezjavier.appfitness.di

import io.github.lopezjavier.appfitness.config.AppConfig
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppModuleTest {
    @Test
    fun appConfig_inLocalFlavor_resolvesFromBuildConfig() {
        val app = koinApplication { modules(appModule) }
        try {
            val config = app.koin.get<AppConfig>()

            assertEquals("http://10.0.2.2:54321", config.supabaseUrl)
            assertTrue(config.isLocal)
        } finally {
            app.close()
        }
    }
}
