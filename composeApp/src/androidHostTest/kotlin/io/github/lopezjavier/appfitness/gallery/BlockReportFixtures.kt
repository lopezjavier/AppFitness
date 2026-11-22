package io.github.lopezjavier.appfitness.gallery

import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.feature.trainer.BlockCoachUi
import io.github.lopezjavier.appfitness.feature.trainer.BlockMainLiftUi
import io.github.lopezjavier.appfitness.feature.trainer.BlockProgressUi
import io.github.lopezjavier.appfitness.feature.trainer.BlockReportAction
import io.github.lopezjavier.appfitness.feature.trainer.BlockReportUi
import io.github.lopezjavier.appfitness.feature.trainer.BlockReviewUiState
import io.github.lopezjavier.appfitness.feature.trainer.BlockSentenceUi
import io.github.lopezjavier.appfitness.feature.trainer.LiftState
import io.github.lopezjavier.appfitness.feature.trainer.LiftUi
import io.github.lopezjavier.appfitness.feature.trainer.OutcomeUi
import io.github.lopezjavier.appfitness.feature.trainer.RecordKind
import io.github.lopezjavier.appfitness.feature.trainer.RecordUi
import io.github.lopezjavier.appfitness.feature.trainer.StrengthUi
import io.github.lopezjavier.appfitness.feature.trainer.WaistUi
import io.github.lopezjavier.appfitness.feature.trainer.WeekContextUi
import io.github.lopezjavier.appfitness.feature.trainer.WeightGoalUi
import io.github.lopezjavier.appfitness.feature.trainer.WeightPhaseUi
import io.github.lopezjavier.appfitness.feature.trainer.WeightSeriesPoint
import io.github.lopezjavier.appfitness.feature.trainer.WeightUi
import io.github.lopezjavier.appfitness.feature.trainer.outcomeChip
import kotlinx.datetime.LocalDate

/** Fake states of the progress-first block review (T-318): every card, gate, goal order and outcome chip. */
internal object BlockReportFixtures {
    private val f = WeekReportFixtures
    private val today = LocalDate(2026, 11, 22)
    private val maintenance = Phase.Maintenance

    private val strength = f.allStatesStrength.copy(blockWeek = 4, context = WeekContextUi(14, 16, 170, 180))

    /** The sentence repeats the cards below (C-193): the Fuerza ups, the bench change of Principales, the weight. */
    private fun sentenceOf(strength: StrengthUi, bench: LiftUi, weightChangeKg: Double?) = BlockSentenceUi(
        ups = strength.groups.sumOf { it.up },
        mainLift = bench.changeKg?.let { BlockMainLiftUi(bench.exerciseId, it, bench.changePct) },
        weightChangeKg = weightChangeKg,
    )

    private val sentence = sentenceOf(strength, f.lifts.first(), weightChangeKg = -1.8)

    /** Block change over the plan-week averages: 4 weeks, first 81,6 kg, last 79,8 kg. */
    val weight = WeightUi(
        avgKg = 79.8,
        days = 5,
        prevDays = 4,
        changeKg = -1.8,
        sinceStartKg = null,
        sinceStartWeeks = 0,
        phase = WeightPhaseUi.Cut,
        series = listOf(81.6, 81.0, 80.4, 79.8).map { WeightSeriesPoint(it, 5) },
        goal = WeightGoalUi(76.0, LocalDate(2027, 2, 15)),
        pace = f.pace(),
    )

    private val moreRecords = listOf(
        RecordUi("bench", RecordKind.E1rm, 103.0, 99.0, mainLift = true),
        RecordUi("row", RecordKind.Heaviest, 75.0, 72.5, mainLift = true),
        RecordUi("squat", RecordKind.E1rm, 130.0, 124.0, mainLift = true),
    )

    private fun progress(
        strength: StrengthUi = this.strength,
        lifts: List<LiftUi> = f.lifts,
        records: List<RecordUi> = f.records,
        recordsTotal: Int = records.size,
        weight: WeightUi? = this.weight,
        waist: WaistUi? = null,
    ) = BlockProgressUi(strength, lifts, records, recordsTotal, weight, waist)

    @Suppress("LongParameterList") // One knob per objective field of the fixture.
    private fun outcome(
        kind: OutcomeKind,
        key: String,
        target: Double,
        now: Double?,
        deadline: LocalDate,
        reached: Boolean,
    ) = OutcomeUi(kind, key, target, now, deadline, outcomeChip(reached, deadline, today))

    /** One of each chip: reached, deadline passed (No conseguido) and deadline ahead (no chip). */
    val outcomes = listOf(
        outcome(OutcomeKind.E1rm, "bench", 100.0, 103.0, LocalDate(2026, 11, 15), reached = true),
        outcome(OutcomeKind.E1rm, "row", 90.0, 81.0, LocalDate(2026, 11, 15), reached = false),
        outcome(OutcomeKind.Weight, "weight", 78.0, 79.8, LocalDate(2027, 2, 15), reached = false),
    )

    private val pendingPhase = BlockCoachUi(sentence, BlockReportAction.PendingPhase(maintenance))

    fun report(
        intention: TrainingIntention = TrainingIntention.Muscle,
        coach: BlockCoachUi = pendingPhase,
        progress: BlockProgressUi? = progress(),
        outcomes: List<OutcomeUi> = this.outcomes,
    ) = BlockReportUi(intention, coach, progress, outcomes)

    private fun withCoach(action: BlockReportAction, sentence: BlockSentenceUi? = this.sentence) =
        report(coach = BlockCoachUi(sentence, action))

    val createNext = withCoach(BlockReportAction.CreateNext)
    val calorieAction = withCoach(BlockReportAction.PendingCalorie(TrainerFixtures.calorieProposal))
    val stallAction = withCoach(BlockReportAction.Stall)

    private val firstBlockStrength = strength.copy(
        groups = strength.groups.map { g ->
            g.copy(
                exercises = g.exercises.map {
                    if (it.mainLift) it else it.copy(state = ProgressGlyph.Uncompared, baseline = null)
                },
            )
        },
    )

    /** First block ever: its own first session is the baseline, so some exercises still read "Sin comparar". */
    val firstBlock = report(
        // Under the weight gate the action is the missing weigh-ins, not "Crea el siguiente bloque".
        coach = BlockCoachUi(
            BlockSentenceUi(firstBlockStrength.groups.sumOf { it.up }, null, null),
            BlockReportAction.MissingData(MissingKind.WeighIns),
        ),
        progress = progress(
            strength = firstBlockStrength,
            lifts = f.lifts.take(2).map { it.copy(state = LiftState.Provisional, changeKg = null, changePct = null) },
            records = emptyList(),
            weight = weight.copy(changeKg = null, days = 2, prevDays = 2, series = weight.series.take(1), pace = null),
        ),
    )

    private val deloadStrength = strength.copy(
        groups = strength.groups.map { g ->
            g.copy(exercises = g.exercises.map { it.copy(carriedOn = LocalDate(2026, 11, 2)) })
        },
    )
    private val deloadLifts = listOf(
        LiftUi("bench", f.points(95.0, 97.5, 99.0, 90.0, hollowAt = 3), LiftState.Up, 4.0, 4.2),
        LiftUi("squat", f.points(120.0, 124.0, 126.0, 108.0, hollowAt = 3), LiftState.Margin, 6.0, 5.0),
    )

    /** The last plan week is a deload: the compared session is carried and the last point is hollow. */
    val deloadLast = report(
        coach = BlockCoachUi(
            sentenceOf(deloadStrength, deloadLifts.first(), weightChangeKg = -1.8),
            pendingPhase.action,
        ),
        progress = progress(
            strength = deloadStrength,
            lifts = deloadLifts,
        ),
    )

    val noRecords = withCoach(BlockReportAction.CreateNext).copy(progress = progress(records = emptyList()))
    val manyRecords = report(progress = progress(records = moreRecords, recordsTotal = 7))
    val gate = report(
        progress = progress(
            weight = weight.copy(days = 5, prevDays = 2, changeKg = null, pace = null),
        ),
        coach = BlockCoachUi(sentence.copy(weightChangeKg = null), BlockReportAction.MissingData(MissingKind.WeighIns)),
    )
    val noWeighIns = report(
        progress = progress(weight = null),
        coach = BlockCoachUi(
            sentence.copy(weightChangeKg = null),
            BlockReportAction.MissingData(MissingKind.WeighIns),
        ),
    )
    val sameAsStart = report(progress = progress(weight = weight.copy(changeKg = 0.0)), coach = pendingPhase)
    val waist = f.waist
    val withWaist = report(progress = progress(waist = waist))

    fun order(intention: TrainingIntention, waist: Boolean = false) =
        report(intention, progress = progress(waist = if (waist) this.waist else null))

    val cut = report(
        TrainingIntention.FatLoss,
        progress = progress(strength = strength.copy(cut = true)),
    )

    val stored = report(
        coach = BlockCoachUi(sentence, BlockReportAction.DecidedPhase(PhaseDecision.Accepted, maintenance)),
    )
    val storedKept = report(
        coach = BlockCoachUi(sentence, BlockReportAction.DecidedPhase(PhaseDecision.Kept, maintenance)),
    )

    /** A v1 review (stored before schema v2): no progress figures, no sentence; the kept cards still show. */
    val v1 = report(
        coach = BlockCoachUi(null, BlockReportAction.DecidedPhase(PhaseDecision.Accepted, maintenance)),
        progress = null,
        outcomes = emptyList(),
    )
    val v1Live = report(coach = BlockCoachUi(null, BlockReportAction.PendingPhase(maintenance)), progress = null)

    fun content(
        report: BlockReportUi?,
        decision: PhaseDecision? = null,
        deciding: PhaseDecision? = null,
        decisionFailed: PhaseDecision? = null,
        calorie: Boolean = false,
        stalled: Boolean = false,
        seen: Boolean = false,
    ) = BlockReviewUiState.Content(
        review = TrainerFixtures.blockReview(decision = decision, seen = seen).let {
            if (stalled) {
                it.copy(stalls = listOf(StallDiagnosis("row", StallCause.NoClearCause(proteinGateMissing = false))))
            } else {
                it
            }
        },
        exerciseNames = f.names,
        calorieProposal = if (calorie) TrainerFixtures.calorieProposal else null,
        deciding = deciding,
        decisionFailed = decisionFailed,
        report = report,
    )
}
