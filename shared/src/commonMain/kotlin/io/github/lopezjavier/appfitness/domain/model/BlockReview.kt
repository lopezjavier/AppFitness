package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** Sessions done against planned over every week of the block (a deload week included); [pct] is null without plan. */
data class BlockAdherence(val completed: Int, val planned: Int, val pct: Int?)

/** Best e1RM (kg) of a lift in a plan [week]; deload weeks are left out by the server. */
data class BlockLiftWeek(val week: Int, val e1rm: Double)

/** One main lift of the block with its weekly e1RM; the figures are null when it was never logged. */
data class BlockLift(
    val exerciseId: String,
    val weeks: List<BlockLiftWeek>,
    val firstE1rm: Double?,
    val lastE1rm: Double?,
    val changeKg: Double?,
    val stalled: Boolean,
)

/** Weight figures of the block; any of them is null when the trend lacks data. */
data class BlockWeight(
    val firstAvgKg: Double?,
    val lastAvgKg: Double?,
    val slopeKgWeek: Double?,
    val intervalLow: Double?,
    val intervalHigh: Double?,
    val targetRateKgWeek: Double?,
)

/** A plan outcome at the end of the block; [reached] is informational only (04 section 8.10), never a trigger. */
data class BlockOutcome(
    val kind: OutcomeKind,
    val key: String,
    val targetValue: Double?,
    val unit: String?,
    val deadline: LocalDate?,
    val currentValue: Double?,
    /** Null while the weight gate is not passed (no verdict yet): no chip. */
    val reached: Boolean?,
)

/** Waist change over at least 28 days; [withinMargin] is true below 3 cm (measurement margin). Never a trigger. */
data class BlockWaist(val deltaCm: Double, val from: LocalDate, val to: LocalDate, val withinMargin: Boolean)

/** Why the server proposes a different phase (07 section 12.3). */
enum class PhaseChangeReason { CutLength, FloorLimited, BackToGoal }

/** The phase change the review offers; [figures] are the numbers behind the reason, keyed as the server stores them. */
data class PhaseProposal(val toPhase: Phase, val reason: PhaseChangeReason, val figures: Map<String, Double>)

/** What the user chose on the proposal. */
enum class PhaseDecision { Accepted, Kept }

/**
 * The end-of-block review (US-101, US-103): inputs frozen when it first opens, plus at most one [phaseProposal] and the
 * user's [phaseDecision] once taken.
 */
data class BlockReview(
    val id: String?,
    val planId: String,
    val endedOn: LocalDate,
    val adherence: BlockAdherence,
    val lifts: List<BlockLift>,
    val groups: List<GroupSets>,
    val weight: BlockWeight?,
    val outcomes: List<BlockOutcome>,
    val waist: BlockWaist?,
    val stalls: List<StallDiagnosis>,
    val phaseProposal: PhaseProposal?,
    val phaseDecision: PhaseDecision?,
    val early: Boolean = false,
    /** True once the user opened the review (`seen_at` set): the block-end notice is no longer listed. */
    val seen: Boolean = false,
    /** The progress-first part (schema v2, T-317); null for a v1 review stored before it. */
    val progress: BlockProgress? = null,
)

/** The best main-lift change of the block (server `headline.main_lift`); the app builds its own from named data. */
data class BlockMainLift(val exerciseId: String, val changeKg: Double?, val changePct: Double?)

/** The server's block sentence parts, for the teasers; [ups] and [total] are null in plan week 1. */
data class BlockHeadline(val ups: Int?, val total: Int?, val mainLift: BlockMainLift?, val weightChangeKg: Double?)

/**
 * Weight over the plan-week averages of the block: [daysFirst] and [daysLast] are the weigh-in days of the first and
 * last plan week, [changeKg] is null under the 3-day gate. [outcome] is the plan's weight goal.
 */
data class BlockWeightProgress(
    val avgFirstKg: Double?,
    val avgLastKg: Double?,
    val daysFirst: Int,
    val daysLast: Int,
    val changeKg: Double?,
    val series: List<WeightPoint>,
    val outcome: WeightOutcome?,
    val trend: WeekWeightTrend?,
    val rateKgWeek: Double?,
)

/** `inputs.progress` of a block review (schema v2, `07` section 3 "Other reports: progress first"). */
data class BlockProgress(
    val asOf: LocalDate?,
    val intention: TrainingIntention?,
    val phase: Phase?,
    val missing: List<MissingKind>,
    val headline: BlockHeadline?,
    val context: ProgressContext,
    val strength: StrengthProgress,
    val lifts: List<LiftSeries>,
    val records: List<WeekRecordBadge>,
    val recordsTotal: Int,
    val weight: BlockWeightProgress?,
    val waist: WaistProgress?,
)
