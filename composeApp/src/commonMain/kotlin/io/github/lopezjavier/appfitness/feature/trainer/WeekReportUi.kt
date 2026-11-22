package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.usecase.WeightPace
import kotlinx.datetime.LocalDate

/*
 * The planned UI model of the weekly report (T-315, visual-first). The designer defines it from the `progress`
 * contract of snapshot v2 (T-313); the core slice feeds it from `WeekProgress` through `WeekReportMapper`,
 * `WeekReportHeadline` and `WeekReportOrder`. Names resolve through
 * `WeekSummaryUiState.Content.exerciseNames` (R-54).
 */

/** Everything the progress-first report shows beyond the kept cards. */
data class WeekReportUi(
    val intention: TrainingIntention,
    /** True for the latest finished week: live buttons and the health note. False for a stored past report (R-60). */
    val live: Boolean,
    val coach: CoachCardUi,
    /** Null for a v1 report (stored before snapshot v2): only the coach card and the kept cards show. */
    val progress: WeekProgressUi?,
)

/** The coach card: sentence parts (`07` §3 (0)) and the first action that applies. */
data class CoachCardUi(val sentence: CoachSentenceUi?, val action: WeekReportAction)

/** Parts of "Esta semana: 3 ejercicios suben, peso −0,3 kg; vas al ritmo previsto." Null parts are left out. */
data class CoachSentenceUi(
    val strengthUp: Int?,
    val weightChangeKg: Double?,
    /** True: "vas al ritmo previsto"; false: another pace; null: no verdict. */
    val onPace: Boolean?,
    /** Week 1 of the block: the sentence is the base message instead. */
    val base: Boolean = false,
)

/** Cards of the progress part; Strength, Lifts and Records are one block that is never split (`weekReportOrder`). */
enum class ReportCardKind { Strength, Lifts, Records, Weight, Waist }

data class WeekProgressUi(
    val strength: StrengthUi,
    val lifts: List<LiftUi>,
    val records: List<RecordUi>,
    val weight: WeightUi,
    val waist: WaistUi?,
)

/** A load and the reps at it: "80 kg × 8". */
data class LoadReps(val loadKg: Double, val reps: Int, val date: LocalDate? = null)

data class ExerciseProgressUi(
    val exerciseId: String,
    val mainLift: Boolean,
    val state: ProgressGlyph,
    val baseline: LoadReps?,
    val best: LoadReps?,
    /** Set when the compared session is an earlier one (the exercise was not trained that week). */
    val carriedOn: LocalDate? = null,
    /** The `07` §7 note due on a paused exercise. Shown on the live report only. */
    val healthNote: HealthNoteKind? = null,
)

enum class HealthNoteKind { FourteenDays, ThreeSessions }

data class StrengthGroupUi(val group: MuscleGroup, val exercises: List<ExerciseProgressUi>) {
    val compared: Int get() = exercises.count { it.state != ProgressGlyph.Uncompared }
    val up: Int get() = exercises.count { it.state == ProgressGlyph.Up }
}

/** "3 de 4 entrenos · 18 de 20 series"; [setsPlanned] null hides the series part. */
data class WeekContextUi(val sessionsDone: Int, val sessionsPlanned: Int, val setsDone: Int, val setsPlanned: Int?)

data class StrengthUi(
    val blockWeek: Int,
    val groups: List<StrengthGroupUi>,
    val context: WeekContextUi,
    /** A cut reads Igual as "Mantienes" (`07` §3 (1)). */
    val cut: Boolean,
)

enum class LiftState { Up, Margin, Stalled, Provisional }

data class LiftPointUi(val e1rmKg: Double, val deload: Boolean)

data class LiftUi(
    val exerciseId: String,
    val points: List<LiftPointUi>,
    val state: LiftState,
    val changeKg: Double?,
    val changePct: Double?,
)

enum class RecordKind { E1rm, Heaviest }

data class RecordUi(
    val exerciseId: String,
    val kind: RecordKind,
    val valueKg: Double,
    val previousKg: Double,
    val mainLift: Boolean,
)

enum class WeightPhaseUi { Cut, Gain, Maintenance }

data class WeightSeriesPoint(val avgKg: Double, val days: Int)

data class WeightGoalUi(val kg: Double, val deadline: LocalDate)

data class WeightUi(
    val avgKg: Double?,
    val days: Int,
    val prevDays: Int,
    /** Null when either week has fewer than 3 weigh-in days (the gate). Rounds to 0: "igual que la semana pasada". */
    val changeKg: Double?,
    val sinceStartKg: Double?,
    val sinceStartWeeks: Int,
    val phase: WeightPhaseUi,
    val series: List<WeightSeriesPoint>,
    val goal: WeightGoalUi?,
    /** The "Tu plan" verdict reads from the same shared pace (R10). */
    val pace: WeightPace?,
)

data class WaistUi(val cm: Double, val date: LocalDate, val refCm: Double, val refDate: LocalDate, val changeCm: Double)

const val WEIGH_IN_GATE = 3
const val WAIST_MARGIN_CM = 3.0
