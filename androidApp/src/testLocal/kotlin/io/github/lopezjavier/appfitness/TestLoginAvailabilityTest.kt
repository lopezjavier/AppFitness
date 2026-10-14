package io.github.lopezjavier.appfitness

import io.github.lopezjavier.appfitness.app.AppFeatures
import io.github.lopezjavier.appfitness.di.appModule
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/** US-05 "Test login" (local flavor only): the seeded user's credentials reach the app features. */
class TestLoginAvailabilityTest {
    @Test
    fun localFlavor_exposesTheSeededTestUser() {
        val app = koinApplication { modules(appModule) }
        try {
            val features = AppFeatures.from(app.koin.get())

            assertEquals("test@appfitness.local", features.testLogin?.email)
        } finally {
            app.close()
        }
    }
}
