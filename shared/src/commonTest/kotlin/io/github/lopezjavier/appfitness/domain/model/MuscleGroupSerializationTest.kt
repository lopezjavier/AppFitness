package io.github.lopezjavier.appfitness.domain.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MuscleGroupSerializationTest {
    @Test
    fun adductorsDecodesFromItsDbValue() {
        assertEquals(MuscleGroup.Adductors, Json.decodeFromString<MuscleGroup>("\"adductors\""))
    }

    @Test
    fun adductorsEncodesToItsDbValue() {
        assertEquals("\"adductors\"", Json.encodeToString(MuscleGroup.Adductors))
    }

    @Test
    fun adductorsSitsBeforeFullBodyAndAfterCalves() {
        val entries = MuscleGroup.entries
        assertTrue(entries.indexOf(MuscleGroup.Calves) < entries.indexOf(MuscleGroup.Adductors))
        assertEquals(entries.size - 2, entries.indexOf(MuscleGroup.Adductors))
        assertEquals(MuscleGroup.FullBody, entries.last())
    }
}
