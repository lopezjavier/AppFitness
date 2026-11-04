package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.usecase.FolderReviewCalculator
import io.github.lopezjavier.appfitness.domain.usecase.countsForVolume

/** The last 8 weeks of one exercise (from `coach_builder_inputs`). */
data class ExerciseHistory(val sessions8w: Int, val lastWeightKg: Double?, val lastReps: Int?)

/** What the device reads before building: history per exercise id, the cardio options and the default one. */
data class BuilderInputs(
    val history: Map<String, ExerciseHistory>,
    val cardioOptions: List<String>,
    val cardioDefault: String?,
)

/** What the picker knows about the user. An empty [equipment] filters nothing (as the server). */
data class PickerContext(
    val equipment: Set<Equipment>,
    val history: Map<String, ExerciseHistory>,
    val favouriteIds: Set<String>,
)

/** One row of a slot's picker; [disabled] when the exercise is already used in the same routine. */
data class PickerEntry(val exercise: CatalogExercise, val disabled: Boolean)

/** Which exercises a slot accepts and in which order (07 (d)); pure. Discomfort is not an input. */
object SlotEligibility {
    private fun insideEquipment(e: CatalogExercise, ctx: PickerContext) =
        ctx.equipment.isEmpty() || e.exercise.equipment in ctx.equipment

    /** Whether the equipment offers at least one main lift for [group] (a strength main slot needs one). */
    fun hasMainLift(group: MuscleGroup, catalogue: List<CatalogExercise>, ctx: PickerContext): Boolean =
        catalogue.any { it.exercise.muscleGroup == group && isEligible(it, group, SlotKind.Main, ctx) }

    private fun isEligible(e: CatalogExercise, group: MuscleGroup, kind: SlotKind, ctx: PickerContext): Boolean {
        val x = e.exercise
        return x.muscleGroup == group && x.countsForVolume() && insideEquipment(e, ctx) &&
            (kind != SlotKind.Main || x.movementPattern in FolderReviewCalculator.MAIN_PATTERNS)
    }

    /**
     * The picker of [slot]: eligible exercises (an Extra accepts any non-cardio exercise of any group) ordered by
     * favourite, sessions in the last 8 weeks, equipment order, name. [usedInRoutine] are the exercises the other
     * slots of the same routine already hold; they stay listed, disabled.
     */
    fun picker(
        slot: Slot,
        catalogue: List<CatalogExercise>,
        ctx: PickerContext,
        usedInRoutine: Set<String>,
    ): List<PickerEntry> = catalogue
        .filter { e ->
            if (slot.kind == SlotKind.Extra) {
                e.exercise.movementPattern != MovementPattern.Cardio && insideEquipment(e, ctx)
            } else {
                isEligible(e, slot.group, slot.kind, ctx)
            }
        }
        .sortedWith(
            compareByDescending<CatalogExercise> { it.id in ctx.favouriteIds }
                .thenByDescending { ctx.history[it.id]?.sessions8w ?: 0 }
                .thenBy { it.exercise.equipment.ordinal }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id },
        )
        .map { PickerEntry(it, it.id in usedInRoutine) }
}
