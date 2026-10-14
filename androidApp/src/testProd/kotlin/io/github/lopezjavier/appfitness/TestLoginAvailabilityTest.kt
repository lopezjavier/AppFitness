package io.github.lopezjavier.appfitness

import io.github.lopezjavier.appfitness.app.AppFeatures
import io.github.lopezjavier.appfitness.di.appModule
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNull

/** US-05 "Test login" does not exist in prod: the credentials are not compiled into the flavor. */
class TestLoginAvailabilityTest {
    @Test
    fun prodFlavor_hasNoTestLogin() {
        val app = koinApplication { modules(appModule) }
        try {
            assertNull(AppFeatures.from(app.koin.get()).testLogin)
        } finally {
            app.close()
        }
    }
}
