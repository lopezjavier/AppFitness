package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

private const val MONTHS_1 = 1
private const val MONTHS_3 = 3
private const val MONTHS_6 = 6
private const val MONTHS_12 = 12

/** Highest repetitions of one completed session of an exercise (any load, warm-ups excluded). */
data class MaxRepsPoint(val sessionId: String, val date: LocalDate, val maxReps: Int)

/** One value of a progress chart; [isRecord] marks a session that set an all-time record of the family. */
data class ChartPoint(val date: LocalDate, val value: Double, val isRecord: Boolean = false)

/** Look-back windows of the progress charts; [AllTime] has no lower bound. */
enum class ProgressRange(private val months: Int?) {
    OneMonth(MONTHS_1),
    ThreeMonths(MONTHS_3),
    SixMonths(MONTHS_6),
    OneYear(MONTHS_12),
    AllTime(null),
    ;

    /** First day included in the window ending [today]; null when the window has no lower bound. */
    fun from(today: LocalDate): LocalDate? = months?.let { today.minus(it, DateTimeUnit.MONTH) }
}

/** 1RM record of an exercise and how much it grew in the last 30 days; the delta is null without older data. */
data class StrengthSummary(val recordOneRepMax: Double?, val monthlyDelta: Double?)

/**
 * What the primary progress chart plots. [MaxReps] is the fallback for load exercises never logged with load;
 * the rest follow the exercise metric type.
 */
enum class ProgressMetric { OneRepMax, MaxReps, Reps, Duration, Distance, Steps, Floors }

/**
 * Series of an exercise inside a [ProgressRange]. [primary] is the series of [mode]; [volume] is the second chart of
 * the weight families and empty otherwise. [hasEnoughData] is false with fewer than 2 points to compare.
 */
data class ExerciseProgress(val mode: ProgressMetric, val primary: List<ChartPoint>, val volume: List<ChartPoint>) {
    val hasEnoughData: Boolean get() = primary.size >= 2
    val isEmpty: Boolean get() = primary.isEmpty()
}
