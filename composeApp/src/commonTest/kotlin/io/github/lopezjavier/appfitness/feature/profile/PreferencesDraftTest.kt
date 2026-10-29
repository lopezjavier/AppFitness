package io.github.lopezjavier.appfitness.feature.profile

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPlace
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PreferencesDraftTest {
    private val base = PreferencesForm(
        name = "Ana",
        sex = Sex.Male,
        birthDate = LocalDate(2000, 3, 1),
        heightCm = 178,
        weightKg = 80.0,
        activity = ActivityLevel.Moderate,
        strategy = GoalStrategy.Deficit,
        adjustment = -400,
        split = MacroSplit.DEFAULT,
    )

    @Test
    fun myTrainingFields_roundTrip() {
        val form = base.copy(
            intention = TrainingIntention.Recomposition,
            place = TrainingPlace.Home,
            equipment = setOf(Equipment.Dumbbell, Equipment.Band),
            daysPerWeek = 4,
            sessionMinutes = 45,
        )

        assertEquals(form, decodeDraft(form.encodeDraft()))
    }

    @Test
    fun unsetMyTrainingFields_roundTripAsNulls() {
        val decoded = assertNotNull(decodeDraft(base.encodeDraft()))

        assertNull(decoded.intention)
        assertNull(decoded.place)
        assertEquals(emptySet(), decoded.equipment)
        assertNull(decoded.daysPerWeek)
        assertNull(decoded.sessionMinutes)
    }

    @Test
    fun anOldDraftWithoutTheFields_stillDecodes() {
        val old = base.encodeDraft()
            .replace(Regex(""","intention":[^,]*|,"place":[^,]*|,"equipment":\[[^\]]*]"""), "")
            .replace(Regex(""","daysPerWeek":[^,}]*|,"sessionMinutes":[^,}]*"""), "")

        assertEquals(base, decodeDraft(old))
    }

    @Test
    fun outOfRangeRestoredValues_areDroppedToNull() {
        val form = base.copy(daysPerWeek = 4, sessionMinutes = 45)
        val tampered = form.encodeDraft().replace("\"daysPerWeek\":4", "\"daysPerWeek\":9")
            .replace("\"sessionMinutes\":45", "\"sessionMinutes\":47")

        val decoded = assertNotNull(decodeDraft(tampered))

        assertNull(decoded.daysPerWeek)
        assertNull(decoded.sessionMinutes)
    }
}
