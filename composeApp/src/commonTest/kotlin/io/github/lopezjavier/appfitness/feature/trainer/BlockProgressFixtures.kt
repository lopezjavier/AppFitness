package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockProgress
import io.github.lopezjavier.appfitness.domain.model.BlockWeightProgress
import io.github.lopezjavier.appfitness.domain.model.LiftSeries
import io.github.lopezjavier.appfitness.domain.model.LiftStatus
import io.github.lopezjavier.appfitness.domain.model.LiftWeek
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.ProgressContext
import io.github.lopezjavier.appfitness.domain.model.ProgressState
import io.github.lopezjavier.appfitness.domain.model.RecordType
import io.github.lopezjavier.appfitness.domain.model.StrengthGroup
import io.github.lopezjavier.appfitness.domain.model.StrengthProgress
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.WeekRecordBadge
import io.github.lopezjavier.appfitness.domain.model.WeekWeightTrend
import io.github.lopezjavier.appfitness.domain.model.WeightOutcome
import io.github.lopezjavier.appfitness.domain.model.WeightPoint
import kotlinx.datetime.LocalDate

/** The names the fixtures resolve; "id-ghost" is deliberately missing. */
internal val blockNames = mapOf("id-bench" to "Press banca", "id-fly" to "Aperturas", "id-row" to "Remo")

/** A week 4 of a cut: bench up, fly same, row up; the ghost exercise has no name (R-54). */
@Suppress("LongParameterList") // A fixture with one knob per section.
internal fun blockProgress(
    blockWeek: Int = 4,
    missing: List<MissingKind> = emptyList(),
    exercises: List<Pair<String, ProgressState>> = listOf(
        "id-bench" to ProgressState.Up,
        "id-fly" to ProgressState.Same,
        "id-row" to ProgressState.Up,
        "id-ghost" to ProgressState.Up,
    ),
    lifts: List<LiftSeries> = listOf(
        LiftSeries("id-bench", listOf(LiftWeek(1, 100.0, false), LiftWeek(4, 105.0, false)), LiftStatus.Up, 5.0, 5.0),
        LiftSeries("id-row", listOf(LiftWeek(1, 80.0, false), LiftWeek(4, 88.0, false)), LiftStatus.Up, 8.0, 10.0),
        LiftSeries("id-ghost", emptyList(), LiftStatus.Up, 20.0, 50.0),
    ),
    records: List<WeekRecordBadge> = emptyList(),
    recordsTotal: Int = records.size,
    weight: BlockWeightProgress? = blockWeight(),
    phase: Phase? = Phase.Cut,
) = BlockProgress(
    asOf = LocalDate(2026, 10, 18),
    intention = TrainingIntention.FatLoss,
    phase = phase,
    missing = missing,
    headline = null,
    context = ProgressContext(14, 16, 170, 180),
    strength = StrengthProgress(
        blockWeek,
        listOf(
            StrengthGroup(
                MuscleGroup.Chest,
                exercises.map { (id, state) -> exerciseProgress(id, state) },
            ),
        ),
    ),
    lifts = lifts,
    records = records,
    recordsTotal = recordsTotal,
    weight = weight,
    waist = null,
)

internal fun recordBadge(id: String) = WeekRecordBadge(id, RecordType.E1rm, 105.0, 100.0, mainLift = true)

/** The `04` section 7.4 trend (80, 79, 79, 78) over four plan weeks. */
internal fun blockWeight(
    daysFirst: Int = 5,
    daysLast: Int = 4,
    changeKg: Double? = -1.8,
    trend: WeekWeightTrend? = WeekWeightTrend(-0.6, -1.209, 0.009, 4),
    rate: Double? = -0.5,
) = BlockWeightProgress(
    avgFirstKg = 81.6,
    avgLastKg = 79.8,
    daysFirst = daysFirst,
    daysLast = daysLast,
    changeKg = changeKg,
    series = listOf(81.6, 81.0, 80.4, 79.8).map { WeightPoint(null, it, 5) },
    outcome = WeightOutcome(76.0, LocalDate(2027, 2, 15)),
    trend = trend,
    rateKgWeek = rate,
)
