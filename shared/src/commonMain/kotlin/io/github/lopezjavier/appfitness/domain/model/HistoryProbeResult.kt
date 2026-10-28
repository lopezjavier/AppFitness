package io.github.lopezjavier.appfitness.domain.model

import kotlin.time.Instant

/** What one record type holds in one time window: the number of records and the earliest start time (null if none). */
data class WindowStats(val recordCount: Int, val oldest: Instant?) {
    companion object {
        val EMPTY = WindowStats(0, null)
    }
}

/** The 0-30 days ago ([recent]) and 60-90 days ago ([old]) windows of one record type. */
data class HistoryWindows(val recent: WindowStats, val old: WindowStats)

/**
 * Outcome of the T-139 diagnostic: whether Health Connect offers reading history older than 30 days
 * ([featureAvailable]), whether the user granted it ([historyGranted]), and what the steps and weight reads returned.
 */
data class HistoryProbeResult(
    val featureAvailable: Boolean,
    val historyGranted: Boolean,
    val steps: HistoryWindows,
    val weight: HistoryWindows,
)
