package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedBiometricsTest {
    private val full = SavedBiometrics(Sex.Female, LocalDate(1999, 6, 15), 165, ActivityLevel.Light)

    @Test
    fun complete_becomesBiometrics() {
        assertEquals(
            Biometrics(Sex.Female, LocalDate(1999, 6, 15), 165, ActivityLevel.Light),
            full.toBiometricsOrNull(),
        )
    }

    @Test
    fun anyMissingField_isNull() {
        assertNull(full.copy(sex = null).toBiometricsOrNull())
        assertNull(full.copy(birthDate = null).toBiometricsOrNull())
        assertNull(full.copy(heightCm = null).toBiometricsOrNull())
        assertNull(full.copy(activityLevel = null).toBiometricsOrNull())
    }
}
