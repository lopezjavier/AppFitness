package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.domain.model.DiscomfortFollowUp
import io.github.lopezjavier.appfitness.domain.model.DiscomfortNoteReason
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.ProgressState
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import io.github.lopezjavier.appfitness.domain.model.StrengthGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.usecase.WeightPaceState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class WeekReportMapperTest {
    private val names = mapOf("id-bench" to "Press banca", "id-fly" to "Aperturas", "id-press" to "Press")

    private fun report(
        summary: WeekSummary = v2Summary(),
        live: Boolean = true,
        followUps: List<DiscomfortFollowUp> = emptyList(),
    ) = assertNotNull(weekReportOf(summary, names, live, followUps))

    private fun followUp(
        id: String,
        show: Boolean = true,
        reason: DiscomfortNoteReason? = DiscomfortNoteReason.Days14,
    ) = DiscomfortFollowUp(
        id,
        "d-$id",
        Instant.parse("2026-11-01T10:00:00Z"),
        offerSwap = false,
        showNote = show,
        noteReason = reason,
    )

    private fun pausedProgress() = weekProgress(
        groups = listOf(
            StrengthGroup(
                MuscleGroup.Chest,
                listOf(
                    exerciseProgress("id-bench", ProgressState.Paused),
                    exerciseProgress("id-fly", ProgressState.Up),
                ),
            ),
        ),
    )

    @Test
    fun groupCounts_onlyComparedExercisesCount() {
        val group = report().progress!!.strength.groups.single()

        assertEquals(2, group.compared)
        assertEquals(1, group.up)
        assertEquals(3, group.exercises.size)
        assertEquals(ProgressGlyph.Uncompared, group.exercises.last().state)
    }

    @Test
    fun pausedExercise_keepsItsGlyphInTheGroup() {
        val group = report(v2Summary(pausedProgress())).progress!!.strength.groups.single()

        assertEquals(ProgressGlyph.Paused, group.exercises.first().state)
    }

    @Test
    fun healthNote_liveOnlyForAPausedExerciseWithDueNote() {
        val summary = v2Summary(pausedProgress())
        val due = listOf(followUp("id-bench"), followUp("id-fly"))

        val live = report(summary, live = true, followUps = due).progress!!.strength.groups.single().exercises

        assertEquals(HealthNoteKind.FourteenDays, live[0].healthNote)
        assertNull(live[1].healthNote) // Not paused: no note even with a follow-up row.
    }

    @Test
    fun healthNote_neverOnAPastReport() {
        val past = report(v2Summary(pausedProgress()), live = false, followUps = listOf(followUp("id-bench")))

        assertTrue(past.progress!!.strength.groups.single().exercises.all { it.healthNote == null })
        assertEquals(false, past.live)
    }

    @Test
    fun healthNote_needsShowNoteAndMapsTheReason() {
        val summary = v2Summary(pausedProgress())

        fun note(f: DiscomfortFollowUp) =
            report(summary, followUps = listOf(f)).progress!!.strength.groups.single().exercises.first().healthNote

        assertNull(note(followUp("id-bench", show = false, reason = null)))
        assertEquals(
            HealthNoteKind.ThreeSessions,
            note(followUp("id-bench", reason = DiscomfortNoteReason.Sessions3)),
        )
    }

    @Test
    fun loadReps_usesTheBestSetAndCarriedRowsKeepTheirDate() {
        val carried =
            exerciseProgress(
                "id-bench",
                ProgressState.Up,
                best = progressSession(82.5, 6, 7, date = LocalDate(2026, 11, 2)),
            )
        val inWeek =
            exerciseProgress(
                "id-fly",
                ProgressState.Up,
                best = progressSession(20.0, 10, date = LocalDate(2026, 11, 5)),
            )
        val progress = weekProgress(listOf(StrengthGroup(MuscleGroup.Chest, listOf(carried, inWeek))))

        val rows = report(v2Summary(progress)).progress!!.strength.groups.single().exercises

        assertEquals(7, rows[0].best?.reps)
        assertEquals(80.0, rows[0].baseline?.loadKg)
        assertEquals(LocalDate(2026, 11, 2), rows[0].carriedOn) // Before the week (3-9 Nov).
        assertNull(rows[1].carriedOn)
    }

    @Test
    fun contextAndCutFlag_comeFromTheProgress() {
        val strength = report().progress!!.strength

        assertEquals(WeekContextUi(3, 4, 18, 20), strength.context)
        assertTrue(strength.cut)
        assertEquals(false, report(v2Summary(weekProgress(phase = Phase.Maintenance))).progress!!.strength.cut)
    }

    @Test
    fun weight_goalUsesTheOutcomeDeadline_andGateComesFromTheDays() {
        val weight = report().progress!!.weight

        assertEquals(WeightGoalUi(75.0, LocalDate(2027, 5, 1)), weight.goal)
        assertEquals(4, weight.days)
        assertEquals(-0.3, weight.changeKg)
        assertEquals(3, weight.sinceStartWeeks)
        assertEquals(WeightPhaseUi.Cut, weight.phase)
        assertEquals(listOf(82.0, 80.2), weight.series.map { it.avgKg })
    }

    @Test
    fun weight_withoutOutcomeHasNoGoal() {
        val progress = weekProgress(weight = weekWeight(outcome = null))

        assertNull(report(v2Summary(progress)).progress!!.weight.goal)
    }

    @Test
    fun pace_isBuiltFromTheTrendThroughTheSharedVerdict() {
        val pace = assertNotNull(report().progress!!.weight.pace)

        assertEquals(WeightPaceState.Soon, pace.state) // The band of 80, 79, 79, 78 still holds zero.
        assertEquals(-0.6, pace.slopeKgWeek)
        assertEquals(-0.5, pace.planRateKgWeek)
        assertTrue(pace.weekly.isEmpty())
    }

    @Test
    fun pace_missingWithoutAPlanRateOrASlope() {
        assertNull(report(v2Summary(weekProgress(weight = weekWeight(rate = null)))).progress!!.weight.pace)
        assertNull(
            report(
                v2Summary(
                    weekProgress(
                        weight = weekWeight(
                            trend = io.github.lopezjavier.appfitness.domain.model.WeekWeightTrend(null, null, null, 1),
                        ),
                    ),
                ),
            ).progress!!.weight.pace,
        )
    }

    @Test
    fun liftPoints_keepDeloadWeeksHollowAndDropWeeksWithoutValue() {
        val lift = report().progress!!.lifts.single()

        assertEquals(listOf(100.0, 104.0), lift.points.map { it.e1rmKg })
        assertEquals(LiftState.Up, lift.state)
    }

    @Test
    fun intention_unknownReadsAsMaintenance() {
        assertEquals(TrainingIntention.FatLoss, report().intention)
        assertEquals(TrainingIntention.Maintenance, report(v2Summary(weekProgress(intention = null))).intention)
    }

    @Test
    fun v1Report_isOnlyTheCoachCard() {
        val v1 = weekSummary()

        val result = report(v1)

        assertNull(result.progress)
        assertNull(result.coach.sentence)
        assertEquals(WeekReportAction.OnPlan, result.coach.action)
    }

    @Test
    fun pausedWeek_hasNoReport() {
        val paused = WeekSummary.pausedWeek("p", LocalDate(2026, 11, 3), LocalDate(2026, 11, 9))

        assertNull(weekReportOf(paused, names, live = true))
    }

    @Test
    fun stallOfAnUnnamedExercise_isNotTheAction() {
        val summary = v2Summary().copy(stalls = listOf(StallDiagnosis("id-unknown", StallCause.Sleep(6.0))))

        assertEquals(WeekReportAction.OnPlan, report(summary).coach.action)
        assertEquals(
            WeekReportAction.Stall,
            report(summary.copy(stalls = listOf(StallDiagnosis("id-bench", StallCause.Sleep(6.0))))).coach.action,
        )
    }

    @Test
    fun exercisesWithoutAResolvedName_areNeitherListedNorCounted() {
        val unnamed = names - "id-fly" - "id-press"

        val group = assertNotNull(weekReportOf(v2Summary(), unnamed, live = true)).progress!!.strength.groups.single()

        assertEquals(listOf("id-bench"), group.exercises.map { it.exerciseId })
        assertEquals(1, group.compared)
        assertEquals(1, group.up)
    }

    @Test
    fun groupWithNoNamedExercise_isDropped_andTheSentenceCountsNamedOnly() {
        val none = weekReportOf(v2Summary(), emptyMap(), live = true)!!

        assertTrue(none.progress!!.strength.groups.isEmpty())
        assertNull(none.coach.sentence!!.strengthUp)
        assertEquals(
            1,
            weekReportOf(v2Summary(), names - "id-fly", live = true)!!.coach.sentence!!.strengthUp,
        )
    }

    @Test
    fun defaultFixtureTrend_isSoonBecauseTheBandHoldsZero() {
        assertEquals(WeightPaceState.Soon, report().progress!!.weight.pace!!.state)
    }
}
