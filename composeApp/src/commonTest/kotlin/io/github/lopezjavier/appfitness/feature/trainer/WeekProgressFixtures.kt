package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.ExerciseWeekProgress
import io.github.lopezjavier.appfitness.domain.model.LiftSeries
import io.github.lopezjavier.appfitness.domain.model.LiftStatus
import io.github.lopezjavier.appfitness.domain.model.LiftWeek
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.ProgressContext
import io.github.lopezjavier.appfitness.domain.model.ProgressSession
import io.github.lopezjavier.appfitness.domain.model.ProgressState
import io.github.lopezjavier.appfitness.domain.model.StrengthGroup
import io.github.lopezjavier.appfitness.domain.model.StrengthProgress
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.WeekProgress
import io.github.lopezjavier.appfitness.domain.model.WeekWeightTrend
import io.github.lopezjavier.appfitness.domain.model.WeightOutcome
import io.github.lopezjavier.appfitness.domain.model.WeightPoint
import io.github.lopezjavier.appfitness.domain.model.WeightProgress
import kotlinx.datetime.LocalDate

internal fun progressSession(load: Double, vararg reps: Int, date: LocalDate? = null) =
    ProgressSession(date, load, reps.toList(), null)

internal fun exerciseProgress(
    id: String,
    state: ProgressState,
    baseline: ProgressSession? = progressSession(80.0, 8, 8),
    best: ProgressSession? = progressSession(82.5, 6, 6),
) = ExerciseWeekProgress(id, mainLift = true, state = state, baseline = baseline, best = best, discomfortId = null)

/** A week 3 of a cut with one chest group (bench up, fly same, press uncompared). */
internal fun weekProgress(
    groups: List<StrengthGroup> = listOf(
        StrengthGroup(
            MuscleGroup.Chest,
            listOf(
                exerciseProgress("id-bench", ProgressState.Up),
                exerciseProgress("id-fly", ProgressState.Same),
                exerciseProgress("id-press", ProgressState.Uncompared, baseline = null, best = null),
            ),
        ),
    ),
    blockWeek: Int = 3,
    phase: Phase? = Phase.Cut,
    intention: TrainingIntention? = TrainingIntention.FatLoss,
    missing: List<MissingKind> = emptyList(),
    weight: WeightProgress = weekWeight(),
) = WeekProgress(
    asOf = LocalDate(2026, 11, 9),
    refreshedAt = null,
    final = false,
    intention = intention,
    phase = phase,
    missing = missing,
    context = ProgressContext(3, 4, 18, 20),
    strength = StrengthProgress(blockWeek, groups),
    lifts = listOf(
        LiftSeries(
            "id-bench",
            listOf(LiftWeek(1, 100.0, false), LiftWeek(2, null, true), LiftWeek(3, 104.0, false)),
            LiftStatus.Up,
            4.0,
            4.0,
        ),
    ),
    records = emptyList(),
    weight = weight,
    waist = null,
)

/** The `04` section 7.4 trend (80, 79, 79, 78): the band [-1.209, 0.009] holds the cut rate and zero, so Soon. */
internal fun weekWeight(
    days: Int = 4,
    changeKg: Double? = -0.3,
    trend: WeekWeightTrend? = WeekWeightTrend(-0.6, -1.209, 0.009, 4),
    rate: Double? = -0.5,
    outcome: WeightOutcome? = WeightOutcome(75.0, LocalDate(2027, 5, 1)),
) = WeightProgress(
    avgKg = 80.2,
    days = days,
    prevAvgKg = 80.5,
    prevDays = 5,
    changeKg = changeKg,
    startAvgKg = 82.0,
    phaseWeeks = 3,
    changeSinceStartKg = -1.8,
    series = listOf(WeightPoint(null, 82.0, 4), WeightPoint(null, 80.2, 4)),
    outcome = outcome,
    trend = trend,
    rateKgWeek = rate,
)

internal fun v2Summary(progress: WeekProgress? = weekProgress(), deload: Boolean = false) =
    weekSummary(deload = deload, nutrition = weekNutrition()).copy(progress = progress, schemaVersion = 2)
