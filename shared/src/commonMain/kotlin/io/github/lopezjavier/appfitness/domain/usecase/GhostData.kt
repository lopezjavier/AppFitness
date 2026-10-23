package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode

private const val DECIMAL_SEPARATOR = ','

/** Pure helpers that turn the last performance of an exercise into the texts of a set row (US-12). */
object GhostData {
    /**
     * The routine id the previous values are restricted to: only in [PreviousValuesMode.Routine] mode and for a
     * session started from a routine. Overall mode and free sessions look at every session (null).
     */
    fun routineFilter(mode: PreviousValuesMode, routineId: String?): String? =
        if (mode == PreviousValuesMode.Routine) routineId else null

    /** The previous set with the same index, or null when last time had fewer sets (or none). */
    fun forSet(previous: List<PreviousSet>, setIndex: Int): PreviousSet? =
        previous.firstOrNull { it.setIndex == setIndex }

    /**
     * The set as [SetEntryText] writes it ("85 kg × 8", "01:30", "5,0 km · 30:00") plus a second line "RPE 8" when
     * last time logged one; null when there is nothing to show.
     */
    fun previousText(set: PreviousSet?, metricType: ExerciseMetricType = ExerciseMetricType.WeightReps): String? =
        set?.let {
            val base = SetEntryText.format(it, metricType)
            rpeText(it)?.let { rpe -> "$base\n$rpe" } ?: base
        }

    /** "RPE 8", or null when the previous set had no RPE. */
    fun rpeText(set: PreviousSet?): String? = set?.rpe?.let { "RPE ${RpeScale.text(it)}" }

    /** The weight without a trailing zero and with a decimal comma: "60", "82,5". */
    fun weightText(weightKg: Double): String {
        if (weightKg == weightKg.toLong().toDouble()) return weightKg.toLong().toString()
        return weightKg.toString().trimEnd('0').trimEnd('.').replace('.', DECIMAL_SEPARATOR)
    }

    /** Weight and reps placeholders (the previous values as the number fields take them). */
    fun placeholders(set: PreviousSet?): Pair<String, String>? =
        set?.let { weightText(it.weightKg) to it.reps.toString() }
}
