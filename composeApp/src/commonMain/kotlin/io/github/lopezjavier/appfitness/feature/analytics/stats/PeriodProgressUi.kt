package io.github.lopezjavier.appfitness.feature.analytics.stats

import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph

/**
 * One exercise of "Tu progreso en este periodo" (`07` §3 (D1)). [state] is Up, Same (within the margin), Down or
 * Uncompared (a single session); [changePct] and [lastKg] are null when uncompared. [firstKg] and [lastKg] are the
 * estimated 1RM of the first and last session in the range.
 */
data class PeriodExerciseUi(
    val exerciseId: String,
    val name: String,
    val mainLift: Boolean,
    val sessions: Int,
    val state: ProgressGlyph,
    val firstKg: Double,
    val lastKg: Double? = null,
    val changePct: Double? = null,
    val imageKey: String? = null,
)

/** The progress block: the exercises (main lifts first, as the backend returns them) and the header counts. */
data class PeriodProgressUi(val exercises: List<PeriodExerciseUi>) {
    val compared: Int get() = exercises.count { it.state != ProgressGlyph.Uncompared }
    val ups: Int get() = exercises.count { it.state == ProgressGlyph.Up }
}

/** A record of the range: [e1rm] tells the kind (estimated 1RM or heaviest weight). */
data class PeriodRecordUi(
    val exerciseId: String,
    val name: String,
    val e1rm: Boolean,
    val valueKg: Double,
    val previousKg: Double,
)

/** At most 3 [items] and the [total] number of records of the range (`07` §3 (D2)). No record: no card. */
data class PeriodRecordsUi(val items: List<PeriodRecordUi>, val total: Int)
