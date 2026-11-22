@file:Suppress("TooManyFunctions") // One small mapper per card of the report.

package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.domain.model.DiscomfortFollowUp
import io.github.lopezjavier.appfitness.domain.model.DiscomfortNoteReason
import io.github.lopezjavier.appfitness.domain.model.ExerciseWeekProgress
import io.github.lopezjavier.appfitness.domain.model.LiftSeries
import io.github.lopezjavier.appfitness.domain.model.LiftStatus
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.ProgressSession
import io.github.lopezjavier.appfitness.domain.model.ProgressState
import io.github.lopezjavier.appfitness.domain.model.RecordType
import io.github.lopezjavier.appfitness.domain.model.StrengthGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.WeekProgress
import io.github.lopezjavier.appfitness.domain.model.WeekRecordBadge
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.usecase.WeightPace
import io.github.lopezjavier.appfitness.domain.usecase.WeightPaceFit
import io.github.lopezjavier.appfitness.domain.usecase.WeightPaceState

/**
 * Builds the progress-first report of [summary] for both screens (the live "Resumen semanal" and the stored past
 * report), so they show the same model. Null for a paused week, which has its own body. [live] is true only for the
 * latest finished week: it enables the health note (from [followUps]) and the live action. A stall of an exercise
 * without a name in [names] is not drawn (R-54), so it never becomes the action either. A v1 snapshot (no `progress`)
 * gives only the coach card.
 */
fun weekReportOf(
    summary: WeekSummary,
    names: Map<String, String>,
    live: Boolean,
    followUps: List<DiscomfortFollowUp> = emptyList(),
): WeekReportUi? {
    if (summary.paused) return null
    // Rows without a resolved name are not drawn (R-54), so they must not be counted either.
    val progress = summary.progress?.let { withNamedExercises(it, names) }
    val drawable = summary.copy(stalls = summary.stalls.filter { it.exerciseId in names }, progress = progress)
    return WeekReportUi(
        intention = progress?.intention ?: TrainingIntention.Maintenance,
        live = live,
        coach = weekReportHeadline(drawable, progress, live),
        progress = progress?.let { progressUi(drawable, it, live, followUps) },
    )
}

private fun withNamedExercises(progress: WeekProgress, names: Map<String, String>) = progress.copy(
    strength = progress.strength.copy(
        groups = progress.strength.groups
            .map { group -> group.copy(exercises = group.exercises.filter { it.exerciseId in names }) }
            .filter { it.exercises.isNotEmpty() },
    ),
)

private fun progressUi(
    summary: WeekSummary,
    progress: WeekProgress,
    live: Boolean,
    followUps: List<DiscomfortFollowUp>,
) = WeekProgressUi(
    strength = StrengthUi(
        blockWeek = progress.strength.blockWeek,
        groups = progress.strength.groups.map { groupUi(it, summary, if (live) followUps else emptyList()) },
        context = WeekContextUi(
            sessionsDone = progress.context.sessionsDone,
            sessionsPlanned = progress.context.sessionsPlanned,
            setsDone = progress.context.setsDone,
            setsPlanned = progress.context.setsPlanned,
        ),
        cut = progress.phase == Phase.Cut,
    ),
    lifts = progress.lifts.map(::liftUi),
    records = progress.records.map(::recordUi),
    weight = weightUi(summary, progress),
    waist = progress.waist?.let { WaistUi(it.cm, it.date, it.refCm, it.refDate, it.changeCm) },
)

private fun groupUi(group: StrengthGroup, summary: WeekSummary, followUps: List<DiscomfortFollowUp>) =
    StrengthGroupUi(group.muscleGroup, group.exercises.map { exerciseUi(it, summary, followUps) })

private fun exerciseUi(exercise: ExerciseWeekProgress, summary: WeekSummary, followUps: List<DiscomfortFollowUp>) =
    ExerciseProgressUi(
        exerciseId = exercise.exerciseId,
        mainLift = exercise.mainLift,
        state = glyphOf(exercise.state),
        baseline = loadReps(exercise.baseline),
        best = loadReps(exercise.best),
        carriedOn = exercise.best?.date?.takeIf { it < summary.startDate || it > summary.endDate },
        healthNote = if (exercise.state == ProgressState.Paused) noteOf(exercise.exerciseId, followUps) else null,
    )

// Only the live report passes follow-ups; the server decides whether the note is due.
private fun noteOf(exerciseId: String, followUps: List<DiscomfortFollowUp>): HealthNoteKind? =
    followUps.firstOrNull { it.exerciseId == exerciseId && it.showNote }?.noteReason?.let {
        when (it) {
            DiscomfortNoteReason.Days14 -> HealthNoteKind.FourteenDays
            DiscomfortNoteReason.Sessions3 -> HealthNoteKind.ThreeSessions
        }
    }

private fun glyphOf(state: ProgressState) = when (state) {
    ProgressState.Up -> ProgressGlyph.Up
    ProgressState.Same -> ProgressGlyph.Same
    ProgressState.Down -> ProgressGlyph.Down
    ProgressState.Stalled -> ProgressGlyph.Stalled
    ProgressState.Paused -> ProgressGlyph.Paused
    ProgressState.Uncompared -> ProgressGlyph.Uncompared
}

// "80 kg × 8": the best set at the top load (the most reps); a session without reps has nothing to show.
private fun loadReps(session: ProgressSession?): LoadReps? =
    session?.reps?.maxOrNull()?.let { LoadReps(session.loadKg, it, session.date) }

private fun liftUi(lift: LiftSeries) = LiftUi(
    exerciseId = lift.exerciseId,
    points = lift.weeks.mapNotNull { week -> week.e1rmKg?.let { LiftPointUi(it, week.deload) } },
    state = when (lift.state) {
        LiftStatus.Up -> LiftState.Up
        LiftStatus.Margin -> LiftState.Margin
        LiftStatus.Stalled -> LiftState.Stalled
        LiftStatus.Provisional -> LiftState.Provisional
    },
    changeKg = lift.changeKg,
    changePct = lift.changePct,
)

private fun recordUi(record: WeekRecordBadge) = RecordUi(
    exerciseId = record.exerciseId,
    kind = if (record.kind == RecordType.E1rm) RecordKind.E1rm else RecordKind.Heaviest,
    valueKg = record.valueKg,
    previousKg = record.previousKg,
    mainLift = record.mainLift,
)

private fun weightUi(summary: WeekSummary, progress: WeekProgress): WeightUi {
    val weight = progress.weight
    return WeightUi(
        avgKg = weight.avgKg,
        days = weight.days,
        prevDays = weight.prevDays,
        changeKg = weight.changeKg,
        sinceStartKg = weight.changeSinceStartKg,
        sinceStartWeeks = weight.phaseWeeks,
        phase = when (progress.phase) {
            Phase.Cut -> WeightPhaseUi.Cut
            Phase.Gain -> WeightPhaseUi.Gain
            else -> WeightPhaseUi.Maintenance
        },
        series = weight.series.map { WeightSeriesPoint(it.avgKg, it.days) },
        goal = weight.outcome?.let { WeightGoalUi(it.kg, it.deadline) },
        pace = weekPaceOf(progress, summary),
    )
}

/**
 * The "Tu plan" pace of the report: the snapshot's trend through the shared [WeightPaceFit.verdictOf] (one pace, R10).
 * The report only has the trend, so [WeightPace] carries no weekly or daily points and no plan line; `PaceVerdict`
 * reads the state, the plan rate, the slope and the early-cut flag only. Null without a trend, a plan rate or a slope.
 */
internal fun weekPaceOf(progress: WeekProgress, summary: WeekSummary): WeightPace? {
    val trend = progress.weight.trend
    val rate = progress.weight.rateKgWeek
    if (trend?.slope == null || rate == null) return null
    val verdict = WeightPaceFit.verdictOf(
        slope = trend.slope,
        low = trend.low,
        high = trend.high,
        weeks = trend.weeks,
        rate = rate,
        phase = progress.phase ?: Phase.Maintenance,
        weeksInPhase = progress.weight.phaseWeeks.takeIf { it > 0 },
    )
    return if (verdict.state == WeightPaceState.NoData) {
        null
    } else {
        WeightPace(
            state = verdict.state,
            planRateKgWeek = rate,
            slopeKgWeek = trend.slope,
            slopeLowKgWeek = trend.low,
            slopeHighKgWeek = trend.high,
            weekly = emptyList(),
            daily = emptyList(),
            planLine = null,
            windowStart = summary.startDate,
            windowWeeks = trend.weeks,
            earlyCutNote = verdict.earlyCutNote,
            weeksInPhase = progress.weight.phaseWeeks.takeIf { it > 0 },
        )
    }
}
