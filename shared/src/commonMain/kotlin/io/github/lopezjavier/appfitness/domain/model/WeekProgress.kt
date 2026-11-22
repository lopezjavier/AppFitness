package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/*
 * The progress part of a weekly report (snapshot v2, `progress`; planning/07-coach-design.md section 3 "Weekly
 * report: progress first"). Built server-side from recorded numbers; the app only reads and shows it.
 */

/** Per exercise reading of the week against the block baseline. The Spanish label belongs to the UI. */
enum class ProgressState { Up, Same, Down, Stalled, Paused, Uncompared }

/** What the week lacks (`progress.missing`); the two `Deleted` kinds are informational and cannot be fixed. */
enum class MissingKind { WeighIns, Food, DeletedSessions, DeletedWeighIns }

/** State of a main lift's e1RM series. */
enum class LiftStatus { Up, Margin, Stalled, Provisional }

enum class RecordType { E1rm, Heaviest }

/** One compared session: the top working load and the reps at it, in set order. [date] is set on a carried row. */
data class ProgressSession(val date: LocalDate?, val loadKg: Double, val reps: List<Int>, val rpe: Double?)

data class ExerciseWeekProgress(
    val exerciseId: String,
    val mainLift: Boolean,
    val state: ProgressState,
    val baseline: ProgressSession?,
    val best: ProgressSession?,
    val discomfortId: String?,
)

data class StrengthGroup(val muscleGroup: MuscleGroup, val exercises: List<ExerciseWeekProgress>)

data class StrengthProgress(val blockWeek: Int, val groups: List<StrengthGroup>)

/** "3 of 4 sessions, 18 of 20 sets"; [setsPlanned] is null when the saved plan no longer matches the routines. */
data class ProgressContext(val sessionsDone: Int, val sessionsPlanned: Int, val setsDone: Int, val setsPlanned: Int?)

data class LiftWeek(val week: Int, val e1rmKg: Double?, val deload: Boolean)

data class LiftSeries(
    val exerciseId: String,
    val weeks: List<LiftWeek>,
    val state: LiftStatus,
    val changeKg: Double?,
    val changePct: Double?,
)

data class WeekRecordBadge(
    val exerciseId: String,
    val kind: RecordType,
    val valueKg: Double,
    val previousKg: Double,
    val mainLift: Boolean,
)

/** The shared trend of `04` section 7.4: [weeks] is the number of weekly averages behind the fit. */
data class WeekWeightTrend(val slope: Double?, val low: Double?, val high: Double?, val weeks: Int)

data class WeightOutcome(val kg: Double, val deadline: LocalDate)

data class WeightPoint(val startDate: LocalDate?, val avgKg: Double, val days: Int)

data class WeightProgress(
    val avgKg: Double?,
    val days: Int,
    val prevAvgKg: Double?,
    val prevDays: Int,
    /** Null under the gate (a week with fewer than 3 weigh-in days). */
    val changeKg: Double?,
    val startAvgKg: Double?,
    val phaseWeeks: Int,
    val changeSinceStartKg: Double?,
    val series: List<WeightPoint>,
    val outcome: WeightOutcome?,
    val trend: WeekWeightTrend?,
    /** The plan's signed rate; null when the plan has none. */
    val rateKgWeek: Double?,
) {
    companion object {
        val Empty = WeightProgress(
            avgKg = null,
            days = 0,
            prevAvgKg = null,
            prevDays = 0,
            changeKg = null,
            startAvgKg = null,
            phaseWeeks = 0,
            changeSinceStartKg = null,
            series = emptyList(),
            outcome = null,
            trend = null,
            rateKgWeek = null,
        )
    }
}

data class WaistProgress(
    val cm: Double,
    val date: LocalDate,
    val refCm: Double,
    val refDate: LocalDate,
    val changeCm: Double,
)

data class WeekProgress(
    val asOf: LocalDate?,
    val refreshedAt: Instant?,
    /** False while late data can still change the latest stored week (Q17). */
    val final: Boolean,
    /** Null for an unknown wire value. */
    val intention: TrainingIntention?,
    val phase: Phase?,
    val missing: List<MissingKind>,
    val context: ProgressContext,
    val strength: StrengthProgress,
    val lifts: List<LiftSeries>,
    val records: List<WeekRecordBadge>,
    val weight: WeightProgress,
    val waist: WaistProgress?,
)
