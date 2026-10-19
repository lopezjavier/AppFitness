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

/** One value of a progress chart. */
data class ChartPoint(val date: LocalDate, val value: Double)

/** Look-back windows of the progress charts. */
enum class ProgressRange(private val months: Int) {
    OneMonth(MONTHS_1),
    ThreeMonths(MONTHS_3),
    SixMonths(MONTHS_6),
    OneYear(MONTHS_12),
    ;

    /** First day included in the window ending [today]. */
    fun from(today: LocalDate): LocalDate = today.minus(months, DateTimeUnit.MONTH)
}

/** 1RM record of an exercise and how much it grew in the last 30 days; the delta is null without older data. */
data class StrengthSummary(val recordOneRepMax: Double?, val monthlyDelta: Double?)

/** What a progress chart plots: estimated 1RM, or max repetitions for exercises logged without load. */
enum class ProgressMode { OneRepMax, MaxReps }

/**
 * Series of an exercise inside a [ProgressRange]. [primary] is the 1RM or max-reps series by [mode]; [volume] is
 * empty in [ProgressMode.MaxReps]. [hasEnoughData] is false with fewer than 2 points to compare.
 */
data class ExerciseProgress(val mode: ProgressMode, val primary: List<ChartPoint>, val volume: List<ChartPoint>) {
    val hasEnoughData: Boolean get() = primary.size >= 2
    val isEmpty: Boolean get() = primary.isEmpty()
}
