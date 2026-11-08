package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val NEW_BLOCK =
    """{"review":{"id":null,"plan_id":"p1","ended_on":"2027-01-03","early":true,"phase_proposal":null,""" +
        """"phase_decision":null,"inputs":{"adherence":{"completed":3,"planned":4,"pct":75}}},""" +
        """"goal":{"changed":true,"plan_intention":"fat_loss","saved_intention":"muscle_gain","current_kcal":2150}}"""

private const val STORED_REVIEW =
    """{"id":"r1","plan_id":"p1","ended_on":"2027-01-05","phase_decision":null,"phase_proposal":null,""" +
        """"inputs":{"adherence":{"completed":14,"planned":16,"pct":88}}}"""

private fun json(text: String) = Json.parseToJsonElement(text)

class NewBlockDtoTest {
    @Test
    fun fullEntry_mapsTheEarlyReviewAndTheGoal() {
        val entry = assertNotNull(newBlockEntryOf(json(NEW_BLOCK)))

        assertNull(entry.review.id)
        assertTrue(entry.review.early)
        assertNull(entry.review.phaseProposal)
        assertEquals(75, entry.review.adherence.pct)
        assertEquals(LocalDate(2027, 1, 3), entry.review.endedOn)
        assertTrue(entry.goal.changed)
        assertEquals("fat_loss", entry.goal.planIntention)
        assertEquals("muscle_gain", entry.goal.savedIntention)
        assertEquals(2150, entry.goal.currentKcal)
    }

    @Test
    fun storedReview_afterTheBlockEnd_keepsItsIdAndIsNotEarly() {
        val entry = assertNotNull(newBlockEntryOf(json("""{"review":$STORED_REVIEW,"goal":{"changed":false}}""")))

        assertEquals("r1", entry.review.id)
        assertFalse(entry.review.early)
        assertFalse(entry.goal.changed)
        assertNull(entry.goal.currentKcal)
    }

    @Test
    fun nullOrMalformedEntry_isNull() {
        assertNull(newBlockEntryOf(json("null")))
        assertNull(newBlockEntryOf(json("[]")))
        assertNull(newBlockEntryOf(json("""{"goal":{"changed":true}}""")))
        assertNull(newBlockEntryOf(json("""{"review":{"id":null},"goal":{}}""")))
    }

    @Test
    fun missingGoal_readsAsNotChanged() {
        val entry = assertNotNull(
            newBlockEntryOf(json("""{"review":{"plan_id":"p1","ended_on":"2027-01-03","early":true}}""")),
        )

        assertFalse(entry.goal.changed)
        assertEquals(0, entry.review.adherence.planned)
    }

    @Test
    fun preview_mapsThePlanIdAndTheCalorieChange() {
        val preview = assertNotNull(
            goalSwitchPreviewOf(
                json("""{"plan_id":"p9","preview":{"current_kcal":2150,"new_kcal":2790.4,"phase":"gain"}}"""),
            ),
        )

        assertEquals("p9", preview.planId)
        assertEquals(2150, preview.currentKcal)
        assertEquals(2790, preview.newKcal)
        assertEquals(Phase.Gain, preview.phase)
    }

    @Test
    fun preview_withoutPlanId_isNull_andMissingFiguresAreNull() {
        assertNull(goalSwitchPreviewOf(json("""{"preview":{}}""")))
        val bare = assertNotNull(goalSwitchPreviewOf(json("""{"plan_id":"p9","preview":{"phase":"x"}}""")))
        assertNull(bare.newKcal)
        assertNull(bare.phase)
    }

    @Test
    fun weeksInPhase_mapsPhaseAndWeeks() {
        assertEquals(PhaseWeeks(Phase.Cut, 9), phaseWeeksOf(json("""{"phase":"cut","weeks":9}""")))
    }

    @Test
    fun weeksInPhase_nullOrUnknownPhase_isNull_andMissingWeeksIsZero() {
        assertNull(phaseWeeksOf(json("null")))
        assertNull(phaseWeeksOf(json("""{"phase":"bulk","weeks":3}""")))
        assertEquals(PhaseWeeks(Phase.Gain, 0), phaseWeeksOf(json("""{"phase":"gain"}""")))
    }
}
