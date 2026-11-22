package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.LiftStatus
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseChangeReason
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.ProgressState
import io.github.lopezjavier.appfitness.domain.model.RecordType
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
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

private const val PROGRESS =
    """"progress":{"as_of":"2027-01-05","intention":"fat_loss","phase":"cut","missing":["weigh_ins","diary","x"],""" +
        """"headline":{"ups":3,"total":5,"main_lift":{"exercise_id":"e1","change_kg":5,"change_pct":6.2},""" +
        """"weight_change_kg":-1.8},"context":{"sessions_done":14,"sessions_planned":16,"sets_done":170,""" +
        """"sets_planned":null},"strength":{"block_week":4,"groups":[{"muscle_group":"chest","exercises":[""" +
        """{"exercise_id":"e1","main_lift":true,"state":"up"},{"exercise_id":"e2","state":"weird"}]}]},""" +
        """"lifts":[{"exercise_id":"e1","state":"up","weeks":[{"week":1,"e1rm_kg":100}],"change_kg":5,""" +
        """"change_pct":6.2},{"exercise_id":"e2","state":"odd","weeks":[]}],""" +
        """"records":{"items":[{"exercise_id":"e1","kind":"e1rm","value_kg":105,"previous_kg":100,""" +
        """"main_lift":true},{"exercise_id":"e1","kind":"nope","value_kg":1,"previous_kg":1}],"total":7},""" +
        """"weight":{"avg_first_kg":81.6,"avg_last_kg":79.8,"days_first":5,"days_last":2,"change_kg":null,""" +
        """"series":[{"start_date":"2026-12-01","avg_kg":81.6,"days":5}],"outcome":{"kg":78,""" +
        """"deadline":"2027-02-15"},"trend":{"slope":-0.3,"low":-0.4,"high":-0.2,"weeks":4},""" +
        """"rate_kg_week":-0.4},"waist":{"cm":80,"date":"2027-01-01","ref_cm":82,"ref_date":"2026-12-01",""" +
        """"change_cm":-2}}"""

private fun withProgress(body: String) = FULL.replace("\"inputs\":{", "\"inputs\":{$body,")

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
        assertEquals(false, outcomes.single().reached)
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

    @Test
    fun v2Progress_isMappedWithEverySection() {
        val progress = assertNotNull(assertNotNull(parse(withProgress(PROGRESS))).progress)

        assertEquals(LocalDate(2027, 1, 5), progress.asOf)
        assertEquals(TrainingIntention.FatLoss, progress.intention)
        assertEquals(Phase.Cut, progress.phase)
        assertEquals(listOf(MissingKind.WeighIns, MissingKind.Food), progress.missing)
        assertEquals(3, progress.headline?.ups)
        assertEquals("e1", progress.headline?.mainLift?.exerciseId)
        assertEquals(-1.8, progress.headline?.weightChangeKg)
        assertEquals(14, progress.context.sessionsDone)
        assertNull(progress.context.setsPlanned)
        assertEquals(4, progress.strength.blockWeek)
        assertEquals(ProgressState.Up, progress.strength.groups.single().exercises.single().state)
        assertEquals(LiftStatus.Up, progress.lifts.single().state)
        assertEquals(RecordType.E1rm, progress.records.single().kind)
        assertEquals(7, progress.recordsTotal)
        assertEquals(-2.0, progress.waist?.changeCm)
    }

    @Test
    fun v2Weight_keepsGateAndPlanWeekFigures() {
        val weight = assertNotNull(assertNotNull(parse(withProgress(PROGRESS))).progress?.weight)

        assertEquals(5, weight.daysFirst)
        assertEquals(2, weight.daysLast)
        assertNull(weight.changeKg)
        assertEquals(81.6, weight.series.single().avgKg)
        assertEquals(78.0, weight.outcome?.kg)
        assertEquals(4, weight.trend?.weeks)
        assertEquals(-0.4, weight.rateKgWeek)
    }

    @Test
    fun v2NullWeight_givesNoBlockWeight() {
        val progress = assertNotNull(assertNotNull(parse(withProgress("\"progress\":{\"weight\":null}"))).progress)

        assertNull(progress.weight)
    }

    @Test
    fun v1AndNonObjectProgress_giveNullProgressWithV1KeysIntact() {
        listOf(FULL, withProgress("\"progress\":null"), withProgress("\"progress\":[]")).forEach {
            val review = assertNotNull(parse(it))

            assertNull(review.progress)
            assertEquals(88, review.adherence.pct)
        }
    }

    @Test
    fun nullReached_staysNull() {
        val review = assertNotNull(parse(FULL.replace("\"reached\":false", "\"reached\":null")))

        assertNull(review.outcomes.single().reached)
    }
}
