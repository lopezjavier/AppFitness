package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.PreviousSet

private const val DECIMAL_SEPARATOR = ','

/** Pure helpers that turn the last performance of an exercise into the texts of a set row (US-12). */
object GhostData {
    /** The previous set with the same index, or null when last time had fewer sets (or none). */
    fun forSet(previous: List<PreviousSet>, setIndex: Int): PreviousSet? =
        previous.firstOrNull { it.setIndex == setIndex }

    /** "85 kg × 8" plus a second line "RPE 8" when last time logged one; null when there is nothing to show. */
    fun previousText(set: PreviousSet?): String? = set?.let {
        val base = "${weightText(it.weightKg)} kg × ${it.reps}"
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
