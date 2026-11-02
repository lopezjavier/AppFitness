package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPlace
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun ezBarAndCardioMachine_roundTrip() {
        val prefs = decode("""{"equipment":["ez_bar","cardio_machine","machine"]}""")

        assertEquals(setOf(Equipment.EzBar, Equipment.CardioMachine, Equipment.Machine), prefs.equipment)

        val body = json.encodeToString(
            MyTrainingUpsertDto.serializer(),
            MyTrainingUpsertDto("u", null, null, listOf(Equipment.EzBar, Equipment.CardioMachine), null, null),
        )
        assertTrue(body.contains(""""equipment":["ez_bar","cardio_machine"]"""), body)
    }

    @Test
    fun legacyCardioValues_decodeAsTheCardioMachine() {
        val prefs = decode(
            """{"equipment":["cardio_bike","treadmill","elliptical","rower","cardio_other","machine"]}""",
        )

        assertEquals(setOf(Equipment.CardioMachine, Equipment.Machine), prefs.equipment)
    }

    @Test
    fun unknownEquipmentValueInPreferences_isSkippedWithoutThrowing() {
        assertEquals(setOf(Equipment.Dumbbell), decode("""{"equipment":["spaceship","dumbbell"]}""").equipment)
    }

    @Test
    fun unknownEquipmentValueInExercise_decodesAsOther() {
        val dto = json.decodeFromString<ExerciseDto>(
            """{"id":"1","muscle_group":"chest","equipment":"spaceship"}""",
        )

        assertEquals(Equipment.Other, dto.equipment)
    }

    @Test
    fun legacyCardioValueInExercise_decodesAsTheCardioMachine() {
        val dto = json.decodeFromString<ExerciseDto>(
            """{"id":"1","muscle_group":"chest","equipment":"treadmill"}""",
        )

        assertEquals(Equipment.CardioMachine, dto.equipment)
    }

    @Test
    fun equipmentOrder_matchesTheCoachRules() {
        assertEquals(
            listOf(
                Equipment.Barbell, Equipment.EzBar, Equipment.Dumbbell, Equipment.Machine, Equipment.Cable,
                Equipment.Kettlebell, Equipment.Bodyweight, Equipment.Band, Equipment.BandLong,
                Equipment.CardioMachine, Equipment.Other,
            ),
            Equipment.entries,
        )
    }

    @Test
    fun cardioEquipment_isSeparatedFromLiftingEquipment() {
        assertEquals(listOf(Equipment.CardioMachine), Equipment.cardio)
        assertEquals(Equipment.entries - Equipment.cardio.toSet(), Equipment.lifting)
        assertEquals(false, Equipment.Machine.isCardio)
    }
}
