package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPlace
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TrainingPreferencesDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(text: String) = json.decodeFromString<TrainingPreferencesDto>(text).toDomain()

    @Test
    fun fullRow_mapsEveryNewField() {
        val prefs = decode(
            """{"intention":"fat_loss","place":"home","equipment":["dumbbell","band"],""" +
                """"days_per_week":4,"session_minutes":45}""",
        )

        assertEquals(TrainingIntention.FatLoss, prefs.intention)
        assertEquals(TrainingPlace.Home, prefs.place)
        assertEquals(setOf(Equipment.Dumbbell, Equipment.Band), prefs.equipment)
        assertEquals(4, prefs.daysPerWeek)
        assertEquals(45, prefs.sessionMinutes)
    }

    @Test
    fun nullsAndMissingColumns_giveDefaults() {
        val explicit = decode(
            """{"intention":null,"place":null,"equipment":[],"days_per_week":null,"session_minutes":null}""",
        )
        val missing = decode("""{}""")

        assertEquals(explicit, missing)
        assertNull(missing.intention)
        assertNull(missing.place)
        assertEquals(emptySet(), missing.equipment)
        assertNull(missing.daysPerWeek)
        assertNull(missing.sessionMinutes)
    }

    @Test
    fun everyIntentionAndPlaceValue_decodes() {
        val intentions = listOf("strength", "muscle", "fat_loss", "recomposition", "maintenance")
            .map { decode("""{"intention":"$it"}""").intention }
        val places = listOf("home", "gym", "other").map { decode("""{"place":"$it"}""").place }

        assertEquals(TrainingIntention.entries, intentions)
        assertEquals(TrainingPlace.entries, places)
    }

    @Test
    fun unknownEquipmentValue_isRejected() {
        assertFailsWith<SerializationException> { decode("""{"equipment":["spaceship"]}""") }
    }
}
