package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseChangeReason
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.StallCause
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val FULL =
    """{"id":"r1","plan_id":"p1","ended_on":"2027-01-05","decided_at":null,"next_plan_id":null,""" +
        """"phase_decision":null,"phase_proposal":{"to_phase":"maintenance","reason":"cut_length",""" +
        """"figures":{"deficit_weeks":12}},"inputs":{"adherence":{"completed":14,"planned":16,"pct":88},""" +
        """"lifts":[{"exercise_id":"e1","weeks":[{"week":1,"e1rm":100.0},{"week":2,"e1rm":102.5}],""" +
        """"first_e1rm":100.0,"last_e1rm":102.5,"change_kg":2.5,"stalled":true}],""" +
        """"groups":[{"muscle_group":"chest","sets":12.5}],""" +
        """"weight":{"first_avg_kg":80.2,"last_avg_kg":78.9,"slope_kg_week":-0.3,"interval_low":-0.4,""" +
        """"interval_high":-0.2,"target_rate_kg_week":-0.4},""" +
        """"outcomes":[{"kind":"weight","key":"weight","target_value":78,"unit":"kg","deadline":"2027-01-05",""" +
        """"current_value":78.9,"reached":false},{"kind":"mystery","key":"x","target_value":1,"reached":true}],""" +
        """"waist":{"delta_cm":-2.5,"from_date":"2026-12-01","to_date":"2027-01-05","within_margin":true},""" +
        """"stalls":[{"exercise_id":"e1","cause":"sleep","avg_sleep_h":6.2}]}}"""

private fun parse(text: String) = blockReviewOf(Json.parseToJsonElement(text))

class BlockReviewDtoTest {
    @Test
    fun fullReview_isMappedWithEveryInput() {
        val review = assertNotNull(parse(FULL))

        assertEquals("r1", review.id)
        assertEquals("p1", review.planId)
        assertEquals(LocalDate(2027, 1, 5), review.endedOn)
        assertEquals(14, review.adherence.completed)
        assertEquals(88, review.adherence.pct)
        val lift = review.lifts.single()
        assertEquals(2, lift.weeks.size)
        assertEquals(2.5, lift.changeKg)
        assertTrue(lift.stalled)
        assertEquals(MuscleGroup.Chest, review.groups.single().muscleGroup)
        assertEquals(-0.3, review.weight?.slopeKgWeek)
        assertEquals(-2.5, review.waist?.deltaCm)
        assertEquals(true, review.waist?.withinMargin)
        assertEquals(StallCause.Sleep(6.2), review.stalls.single().cause)
        assertNull(review.phaseDecision)
    }

    @Test
    fun proposal_isMappedWithReasonPhaseAndFigures() {
        val proposal = assertNotNull(assertNotNull(parse(FULL)).phaseProposal)

        assertEquals(Phase.Maintenance, proposal.toPhase)
        assertEquals(PhaseChangeReason.CutLength, proposal.reason)
        assertEquals(mapOf("deficit_weeks" to 12.0), proposal.figures)
    }

    @Test
    fun unknownOutcomeKind_isDroppedAndKnownOneKept() {
        val outcomes = assertNotNull(parse(FULL)).outcomes

        assertEquals(1, outcomes.size)
        assertEquals(OutcomeKind.Weight, outcomes.single().kind)
        assertFalse(outcomes.single().reached)
    }

    @Test
    fun jsonNull_isNoReview() {
        assertNull(parse("null"))
    }

    @Test
    fun missingIdentityKeys_isNoReview() {
        assertNull(parse("""{"plan_id":"p1","inputs":{}}"""))
        assertNull(parse("""{"id":"r1","plan_id":"p1","inputs":{}}"""))
        assertNull(parse("[]"))
    }

    @Test
    fun unknownPhase_givesNoProposalNeverACrash() {
        val review = parse(FULL.replace("\"to_phase\":\"maintenance\"", "\"to_phase\":\"bulk\""))

        assertNotNull(review)
        assertNull(review.phaseProposal)
    }

    @Test
    fun unknownReason_givesNoProposalNeverACrash() {
        val review = parse(FULL.replace("cut_length", "something_new"))

        assertNotNull(review)
        assertNull(review.phaseProposal)
    }

    @Test
    fun missingOptionalKeys_fallBackToEmptyOrNull() {
        val review = assertNotNull(parse("""{"id":"r1","plan_id":"p1","ended_on":"2027-01-05"}"""))

        assertEquals(0, review.adherence.planned)
        assertNull(review.adherence.pct)
        assertTrue(review.lifts.isEmpty())
        assertTrue(review.groups.isEmpty())
        assertNull(review.weight)
        assertNull(review.waist)
        assertTrue(review.outcomes.isEmpty())
        assertTrue(review.stalls.isEmpty())
        assertNull(review.phaseProposal)
    }

    @Test
    fun decisions_areMapped_andAnUnknownOneIsNull() {
        assertEquals(PhaseDecision.Accepted, decisionOf("accepted"))
        assertEquals(PhaseDecision.Kept, decisionOf("kept"))
        assertNull(decisionOf("later"))
    }

    private fun decisionOf(code: String) =
        parse(FULL.replace("\"phase_decision\":null", "\"phase_decision\":\"$code\""))?.phaseDecision
}
