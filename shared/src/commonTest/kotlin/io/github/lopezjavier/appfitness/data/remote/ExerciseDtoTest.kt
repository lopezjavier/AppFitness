package io.github.lopezjavier.appfitness.data.remote

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(extra: String) = json.decodeFromString<ExerciseDto>(
        """{"id":"1","muscle_group":"chest","equipment":"barbell"$extra}""",
    ).toDomain()

    @Test
    fun countsAsStrength_defaultsToTrue() {
        assertTrue(decode("").countsAsStrength)
    }

    @Test
    fun countsAsStrength_readsTheColumn() {
        assertFalse(decode(""","counts_as_strength":false""").countsAsStrength)
        assertEquals(true, decode(""","counts_as_strength":true""").countsAsStrength)
    }
}
