package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SetColumns
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import kotlin.math.roundToLong

private const val METRES_PER_KM = 1000.0
private const val TENTHS = 10.0

/**
 * The one text of a logged set, shared by the ghost data, the session detail and the history: "85 kg × 8",
 * "8 reps", "01:30", "5,0 km · 30:00". Pure; units are symbols so the text needs no localisation.
 */
object SetEntryText {
    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the entry variants.
    fun format(entry: SetEntry): String = when (entry) {
        is SetEntry.WeightReps -> "${GhostData.weightText(entry.weightKg)} kg × ${entry.reps}"

        is SetEntry.RepsOnly -> repsText(entry.reps)

        is SetEntry.Duration -> DurationFormat.formatMmSs(entry.durationS)

        is SetEntry.WeightDuration ->
            "${GhostData.weightText(entry.weightKg)} kg · ${DurationFormat.formatMmSs(entry.durationS)}"

        is SetEntry.DistanceDuration ->
            "${kmText(entry.distanceM)} km · ${DurationFormat.formatMmSs(entry.durationS)}"

        is SetEntry.Bodyweight -> loadedReps("+", entry.addedKg, entry.reps)

        is SetEntry.Weighted -> loadedReps("+", entry.addedKg, entry.reps)

        is SetEntry.Assisted -> loadedReps("-", entry.assistKg, entry.reps)

        is SetEntry.StepsDuration -> "${entry.steps} pasos · ${DurationFormat.formatMmSs(entry.durationS)}"

        is SetEntry.FloorsDuration -> "${entry.floors} pisos · ${DurationFormat.formatMmSs(entry.durationS)}"
    }

    /** The ghost of [set] read as a [metricType] set. */
    fun format(set: PreviousSet, metricType: ExerciseMetricType): String = format(
        SetColumns(set.weightKg, set.reps, set.durationS, set.distanceM, set.steps, set.floors).toEntry(metricType),
    )

    /** Kilometres of a distance in metres with one decimal and a comma: 5000 gives "5,0". */
    fun kmText(distanceM: Double): String {
        val tenths = (distanceM / METRES_PER_KM * TENTHS).roundToLong()
        return "${tenths / TENTHS.toLong()},${tenths % TENTHS.toLong()}"
    }

    /** Whether sets of this metric type add to the volume (the others show "—" instead of "0 kg"). */
    fun carriesVolume(metricType: ExerciseMetricType): Boolean = when (metricType) {
        ExerciseMetricType.WeightReps,
        ExerciseMetricType.Bodyweight,
        ExerciseMetricType.Assisted,
        ExerciseMetricType.Weighted,
        -> true

        else -> false
    }

    private fun repsText(reps: Int) = "$reps reps"

    private fun loadedReps(sign: String, kg: Double, reps: Int) =
        if (kg == 0.0) repsText(reps) else "$sign${GhostData.weightText(kg)} kg × $reps"
}
