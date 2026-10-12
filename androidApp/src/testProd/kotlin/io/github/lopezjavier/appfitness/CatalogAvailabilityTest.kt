package io.github.lopezjavier.appfitness

import io.github.lopezjavier.appfitness.app.AppFeatures
import io.github.lopezjavier.appfitness.app.CatalogRoute
import io.github.lopezjavier.appfitness.app.isAvailable
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.di.appModule
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertFalse

class CatalogAvailabilityTest {
    @Test
    fun catalog_inProdFlavor_isNotAvailable() {
        val app = koinApplication { modules(appModule) }
        try {
            val features = AppFeatures.from(app.koin.get<AppConfig>())

            assertFalse(features.componentCatalog)
            assertFalse(features.isAvailable(CatalogRoute))
        } finally {
            app.close()
        }
    }
}
