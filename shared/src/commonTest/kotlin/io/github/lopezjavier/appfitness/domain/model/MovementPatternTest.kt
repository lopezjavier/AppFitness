package io.github.lopezjavier.appfitness.domain.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MovementPatternTest {
    @Test
    fun shoulderAbduction_decodesFromItsDatabaseValue() {
        assertEquals(
            MovementPattern.ShoulderAbduction,
            Json.decodeFromString<MovementPattern>("\"shoulder_abduction\""),
        )
    }

    @Test
    fun shoulderAbduction_encodesToItsDatabaseValue() {
        assertEquals("\"shoulder_abduction\"", Json.encodeToString(MovementPattern.ShoulderAbduction))
    }
}
