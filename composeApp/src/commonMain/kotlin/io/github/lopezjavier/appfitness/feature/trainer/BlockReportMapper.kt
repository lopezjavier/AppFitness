@file:Suppress("TooManyFunctions") // One small mapper per card of the report.

package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockOutcome
import io.github.lopezjavier.appfitness.domain.model.BlockProgress
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.BlockWeightProgress
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.ExerciseWeekProgress
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.usecase.WeightPace
import io.github.lopezjavier.appfitness.domain.usecase.WeightPaceFit
import io.github.lopezjavier.appfitness.domain.usecase.WeightPaceState
import kotlinx.datetime.LocalDate

/**
 * Builds the progress-first model of [review] for the live review and the stored one, like `weekReportOf`. Rows
 * without a name in [names] are dropped before anything is counted (R-54). [live] is true only on the live review (it
 * enables the pending actions); [today] decides the "No conseguido" chip. A v1 review (no `progress`) gives only the
 * coach action and the outcomes.
 */
fun blockReportOf(
    review: BlockReview,
    names: Map<String, String>,
    calorieProposal: CalorieProposal?,
    live: Boolean,
    today: LocalDate,
): BlockReportUi {
    val progress = review.progress?.let { namedProgress(it, names) }
    val drawable = review.copy(progress = progress)
    return BlockReportUi(
        intention = progress?.intention ?: TrainingIntention.Maintenance,
        coach = blockReportHeadline(drawable, progress, names, calorieProposal, live),
        progress = progress?.let { progressUi(it, review.endedOn) },
        outcomes = review.outcomes.mapNotNull { outcomeUi(it, names, today) },
    )
}

private fun namedProgress(progress: BlockProgress, names: Map<String, String>): BlockProgress {
    val records = progress.records.filter { it.exerciseId in names }
    return progress.copy(
        strength = progress.strength.copy(
            groups = progress.strength.groups
                .map { group -> group.copy(exercises = group.exercises.filter { it.exerciseId in names }) }
                .filter { it.exercises.isNotEmpty() },
        ),
        lifts = progress.lifts.filter { it.exerciseId in names },
        records = records,
        // The server counts every record; the unnamed ones among the listed items are not drawn nor counted.
        recordsTotal = maxOf(progress.recordsTotal - (progress.records.size - records.size), records.size),
    )
}

// A block compares the plan weeks, not a session against the baseline of a week: nothing is carried on, no health note.
private fun exerciseUi(exercise: ExerciseWeekProgress) = ExerciseProgressUi(
    exerciseId = exercise.exerciseId,
    mainLift = exercise.mainLift,
    state = glyphOf(exercise.state),
    baseline = loadReps(exercise.baseline),
    best = loadReps(exercise.best),
    carriedOn = null,
    healthNote = null,
)

private fun progressUi(progress: BlockProgress, endedOn: LocalDate) = BlockProgressUi(
    strength = StrengthUi(
        blockWeek = progress.strength.blockWeek,
        groups = progress.strength.groups.map { group ->
            StrengthGroupUi(group.muscleGroup, group.exercises.map(::exerciseUi))
        },
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
    recordsTotal = progress.recordsTotal,
    weight = progress.weight?.let { weightUi(it, progress, endedOn) },
    waist = progress.waist?.let { WaistUi(it.cm, it.date, it.refCm, it.refDate, it.changeCm) },
)

private fun weightUi(weight: BlockWeightProgress, progress: BlockProgress, endedOn: LocalDate) = WeightUi(
    avgKg = weight.avgLastKg,
    days = weight.daysLast,
    prevDays = weight.daysFirst,
    // The server already nulls it; the gate is applied here too so the card never shows an unreliable change.
    changeKg = gatedChangeKg(weight),
    sinceStartKg = null,
    sinceStartWeeks = 0,
    phase = when (progress.phase) {
        Phase.Cut -> WeightPhaseUi.Cut
        Phase.Gain -> WeightPhaseUi.Gain
        else -> WeightPhaseUi.Maintenance
    },
    series = weight.series.map { WeightSeriesPoint(it.avgKg, it.days) },
    goal = weight.outcome?.let { WeightGoalUi(it.kg, it.deadline) },
    pace = blockPaceOf(weight, progress, endedOn),
)

/**
 * The "Tu plan" pace of the block through the shared [WeightPaceFit.verdictOf] (one pace, R10). The block has no
 * "weeks in phase" (the whole block uses one pace), so the early-cut note is never raised here. Null without a trend,
 * a plan rate or a slope.
 */
private fun blockPaceOf(weight: BlockWeightProgress, progress: BlockProgress, endedOn: LocalDate): WeightPace? {
    val trend = weight.trend
    val rate = weight.rateKgWeek
    if (trend?.slope == null || rate == null) return null
    val verdict = WeightPaceFit.verdictOf(
        slope = trend.slope,
        low = trend.low,
        high = trend.high,
        weeks = trend.weeks,
        rate = rate,
        phase = progress.phase ?: Phase.Maintenance,
        weeksInPhase = null,
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
            windowStart = weight.series.firstNotNullOfOrNull { it.startDate } ?: endedOn,
            windowWeeks = trend.weeks,
            earlyCutNote = verdict.earlyCutNote,
            weeksInPhase = null,
        )
    }
}

/** An e1RM objective whose exercise has no name is not drawn (R-54); one without a target has nothing to show. */
private fun outcomeUi(outcome: BlockOutcome, names: Map<String, String>, today: LocalDate): OutcomeUi? {
    val target = outcome.targetValue
    if (target == null || (outcome.kind == OutcomeKind.E1rm && outcome.key !in names)) return null
    val reached = outcome.reached
    return OutcomeUi(
        kind = outcome.kind,
        key = outcome.key,
        targetKg = target,
        nowKg = outcome.currentValue,
        deadline = outcome.deadline,
        // Null = the weight gate was not passed: no verdict, so never "No conseguido".
        chip = if (reached == null) OutcomeChip.None else outcomeChip(reached, outcome.deadline, today),
    )
}
