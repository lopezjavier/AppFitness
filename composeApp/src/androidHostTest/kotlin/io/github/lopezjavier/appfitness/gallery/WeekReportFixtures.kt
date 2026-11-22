package io.github.lopezjavier.appfitness.gallery

import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.NutritionDiagnosis
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.usecase.PlanPaceLine
import io.github.lopezjavier.appfitness.domain.usecase.WeightPace
import io.github.lopezjavier.appfitness.domain.usecase.WeightPaceState
import io.github.lopezjavier.appfitness.feature.trainer.CoachCardUi
import io.github.lopezjavier.appfitness.feature.trainer.CoachSentenceUi
import io.github.lopezjavier.appfitness.feature.trainer.ExerciseProgressUi
import io.github.lopezjavier.appfitness.feature.trainer.HealthNoteKind
import io.github.lopezjavier.appfitness.feature.trainer.LiftPointUi
import io.github.lopezjavier.appfitness.feature.trainer.LiftState
import io.github.lopezjavier.appfitness.feature.trainer.LiftUi
import io.github.lopezjavier.appfitness.feature.trainer.LoadReps
import io.github.lopezjavier.appfitness.feature.trainer.RecordKind
import io.github.lopezjavier.appfitness.feature.trainer.RecordUi
import io.github.lopezjavier.appfitness.feature.trainer.StrengthGroupUi
import io.github.lopezjavier.appfitness.feature.trainer.StrengthUi
import io.github.lopezjavier.appfitness.feature.trainer.WaistUi
import io.github.lopezjavier.appfitness.feature.trainer.WeekContextUi
import io.github.lopezjavier.appfitness.feature.trainer.WeekProgressUi
import io.github.lopezjavier.appfitness.feature.trainer.WeekReportAction
import io.github.lopezjavier.appfitness.feature.trainer.WeekReportUi
import io.github.lopezjavier.appfitness.feature.trainer.WeekSummaryUiState
import io.github.lopezjavier.appfitness.feature.trainer.WeightGoalUi
import io.github.lopezjavier.appfitness.feature.trainer.WeightPhaseUi
import io.github.lopezjavier.appfitness.feature.trainer.WeightSeriesPoint
import io.github.lopezjavier.appfitness.feature.trainer.WeightUi
import kotlinx.datetime.LocalDate

/** Fake states of the progress-first weekly report (T-315): every card, gate and order the gallery shows. */
internal object WeekReportFixtures {
    val names = mapOf(
        "bench" to "Press de banca",
        "ohp" to "Press militar",
        "fly" to "Aperturas",
        "row" to "Remo con barra",
        "pulldown" to "Jalón al pecho",
        "squat" to "Sentadilla",
        "rdl" to "Peso muerto rumano",
    )

    private val carriedDate = LocalDate(2026, 11, 2)

    private fun ex(
        id: String,
        state: ProgressGlyph,
        main: Boolean = false,
        from: LoadReps? = LoadReps(80.0, 8),
        to: LoadReps? = LoadReps(82.5, 6),
        carried: LocalDate? = null,
        note: HealthNoteKind? = null,
    ) = ExerciseProgressUi(id, main, state, if (state == ProgressGlyph.Uncompared) null else from, to, carried, note)

    val allStatesStrength = StrengthUi(
        blockWeek = 4,
        cut = false,
        context = WeekContextUi(3, 4, 18, 20),
        groups = listOf(
            StrengthGroupUi(
                MuscleGroup.Chest,
                listOf(
                    ex("bench", ProgressGlyph.Up, main = true),
                    ex("fly", ProgressGlyph.Same, from = LoadReps(20.0, 12), to = LoadReps(20.0, 12)),
                    ex(
                        "ohp",
                        ProgressGlyph.Down,
                        from = LoadReps(50.0, 8),
                        to = LoadReps(47.5, 8),
                        carried = carriedDate,
                    ),
                ),
            ),
            StrengthGroupUi(
                MuscleGroup.Back,
                listOf(
                    ex("row", ProgressGlyph.Stalled, main = true, from = LoadReps(70.0, 8), to = LoadReps(70.0, 8)),
                    ex("pulldown", ProgressGlyph.Uncompared),
                ),
            ),
            StrengthGroupUi(
                MuscleGroup.Quads,
                listOf(ex("squat", ProgressGlyph.Paused, main = true, note = HealthNoteKind.FourteenDays)),
            ),
        ),
    )

    private fun points(vararg e1rm: Double, hollowAt: Int = -1) =
        e1rm.mapIndexed { index, v -> LiftPointUi(v, deload = index == hollowAt) }

    private val lifts = listOf(
        LiftUi("bench", points(95.0, 97.5, 96.0, 99.0, 103.0), LiftState.Up, 8.0, 8.4),
        LiftUi("squat", points(120.0, 124.0, 126.0, 108.0, 127.0, hollowAt = 3), LiftState.Margin, 7.0, 3.1),
        LiftUi("row", points(80.0, 82.0, 81.5, 81.0), LiftState.Stalled, 1.0, 1.3),
        LiftUi("ohp", points(52.0, 54.0), LiftState.Provisional, null, null),
    )

    private val records = listOf(
        RecordUi("bench", RecordKind.E1rm, 103.0, 99.0, mainLift = true),
        RecordUi("row", RecordKind.Heaviest, 75.0, 72.5, mainLift = true),
    )

    private fun pace() = WeightPace(
        state = WeightPaceState.OnPace,
        planRateKgWeek = -0.4,
        slopeKgWeek = -0.35,
        slopeLowKgWeek = -0.55,
        slopeHighKgWeek = -0.15,
        weekly = emptyList(),
        daily = emptyList(),
        planLine = PlanPaceLine(startWeeks = 0.0, startKg = 82.0, endWeeks = 8.0, endKg = 78.0),
        windowStart = LocalDate(2026, 10, 12),
        windowWeeks = 5,
        earlyCutNote = false,
        weeksInPhase = 5,
    )

    val weight = WeightUi(
        avgKg = 80.2,
        days = 5,
        prevDays = 4,
        changeKg = -0.3,
        sinceStartKg = -2.1,
        sinceStartWeeks = 6,
        phase = WeightPhaseUi.Cut,
        series = listOf(82.3, 81.8, 81.4, 80.9, 80.5, 80.2).map { WeightSeriesPoint(it, 5) },
        goal = WeightGoalUi(76.0, LocalDate(2027, 2, 15)),
        pace = pace(),
    )

    val waist = WaistUi(82.0, LocalDate(2026, 11, 8), 83.5, LocalDate(2026, 10, 5), -1.5)

    private fun progress(
        strength: StrengthUi = allStatesStrength,
        lifts: List<LiftUi> = this.lifts,
        records: List<RecordUi> = this.records,
        weight: WeightUi = this.weight,
        waist: WaistUi? = null,
    ) = WeekProgressUi(strength, lifts, records, weight, waist)

    private val sentence = CoachSentenceUi(strengthUp = 1, weightChangeKg = -0.3, onPace = true)
    private val onPlan = CoachCardUi(sentence, WeekReportAction.OnPlan)
    private val applied = WeekReportAction.DecidedProposal(
        TrainerFixtures.calorieProposal.copy(fromKcal = 2300, toKcal = 2150),
        ProposalStatus.Applied,
    )

    fun report(
        intention: TrainingIntention = TrainingIntention.Muscle,
        live: Boolean = true,
        coach: CoachCardUi = CoachCardUi(sentence, WeekReportAction.PendingProposal(TrainerFixtures.calorieProposal)),
        progress: WeekProgressUi? = progress(),
    ) = WeekReportUi(intention, live, coach, progress)

    fun withWaist(report: WeekReportUi) = report.copy(progress = report.progress?.copy(waist = waist))

    fun content(report: WeekReportUi, extras: (WeekSummary) -> WeekSummary = { it }) =
        WeekSummaryUiState.Content(extras(TrainerFixtures.weekSummary(seen = true).copy(week = 4)), names, report)

    val week1 = report(
        coach = CoachCardUi(CoachSentenceUi(null, null, null, base = true), WeekReportAction.OnPlan),
        progress = progress(
            strength = allStatesStrength.copy(
                blockWeek = 1,
                groups = allStatesStrength.groups.map { g ->
                    g.copy(exercises = g.exercises.map { it.copy(state = ProgressGlyph.Uncompared, baseline = null) })
                },
            ),
            lifts = emptyList(),
            records = emptyList(),
        ),
    )
    val emptyStrength = report(
        progress = progress(
            strength = allStatesStrength.copy(groups = emptyList(), context = WeekContextUi(0, 4, 0, null)),
            lifts = emptyList(),
            records = emptyList(),
        ),
    )
    val noRecords = report(progress = progress(records = emptyList()), coach = onPlan)
    val gate = report(
        progress = progress(weight = weight.copy(days = 2, changeKg = null, sinceStartKg = null, pace = null)),
    )
    val noWeighIns = report(
        coach = CoachCardUi(CoachSentenceUi(1, null, null), WeekReportAction.MissingData(MissingKind.WeighIns)),
        progress = progress(
            weight = weight.copy(
                avgKg = null,
                days = 0,
                changeKg = null,
                sinceStartKg = null,
                pace = null,
                series = emptyList(),
            ),
        ),
    )
    val sameAsLast = report(progress = progress(weight = weight.copy(changeKg = 0.0)), coach = onPlan)
    val waistInMargin = report(progress = progress(waist = waist), coach = onPlan)
    val waistBeyond = report(
        progress = progress(waist = waist.copy(cm = 79.0, refCm = 83.5, changeCm = -4.5)),
        coach = onPlan,
    )
    val cutHold = report(
        intention = TrainingIntention.FatLoss,
        progress = progress(strength = allStatesStrength.copy(cut = true)),
        coach = onPlan,
    )
    val decidedPast = report(live = false, coach = CoachCardUi(sentence, applied))
    val v1Past = report(live = false, coach = CoachCardUi(null, applied), progress = null)
    val missingDeleted = report(
        live = false,
        coach = CoachCardUi(
            sentence.copy(weightChangeKg = null),
            WeekReportAction.MissingData(MissingKind.DeletedSessions),
        ),
    )

    /** Kept cards under the progress cards: diagnosis, a stall, the deload line. */
    val keptCards = content(report(coach = CoachCardUi(sentence, WeekReportAction.WeightDiagnosis))) {
        it.copy(
            deload = true,
            stalls = listOf(StallDiagnosis("row", StallCause.NoClearCause(proteinGateMissing = false))),
            nutrition = TrainerFixtures.nutrition(complete = true)
                .copy(diagnosis = NutritionDiagnosis.Plateau(-0.02, -0.2, 0.15)),
        )
    }
}
