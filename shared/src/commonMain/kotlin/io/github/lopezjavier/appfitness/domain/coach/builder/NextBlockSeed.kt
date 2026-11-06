package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup

/** Pre-fills the builder of the next block from the previous one (07 section 3 "Block builder" (e)); pure. */
object NextBlockSeed {
    private fun firstWeek(block: CoachBlock): List<BlockSession> = block.weeks().firstOrNull()?.sessions.orEmpty()

    /** The routine type of each training day, from the focus groups of the stored sessions; null if one is unknown. */
    fun types(block: CoachBlock): List<RoutineType>? {
        val sessions = firstWeek(block)
        val types = sessions.map { s ->
            val focus = s.focus.map { it.lowercase() }.toSet()
            RoutineType.entries.firstOrNull { t -> t.groups.map { it.name.lowercase() }.toSet() == focus }
        }
        return if (sessions.isEmpty() || types.any { it == null }) null else types.filterNotNull()
    }

    /** The exercises that were a main lift of the previous block. */
    fun mainLiftIds(block: CoachBlock): Set<String> =
        firstWeek(block).flatMap { it.exercises }.filter { it.role == BuiltExercise.ROLE_MAIN }
            .map { it.exerciseId }.toSet()

    /**
     * Re-applies the previous picks to the empty [state]: each slot takes the first previous exercise of its group,
     * in the stored order, that the slot still accepts and the routine does not hold yet; otherwise it stays empty.
     * The planned weight is recomputed from the history, so it only carries over inside the slot's rep range.
     */
    fun apply(
        state: BuilderState,
        block: CoachBlock,
        catalogue: List<CatalogExercise>,
        ctx: PickerContext,
    ): BuilderState {
        val sessions = firstWeek(block)
        val byId = catalogue.associateBy { it.id }
        var result = state
        state.days.forEachIndexed { d, day ->
            val previous = sessions.getOrNull(d)?.exercises.orEmpty().filter { !it.isCardio }
            val used = mutableSetOf<String>()
            day.slots.forEachIndexed { i, filled ->
                val entries = SlotEligibility.picker(filled.slot, catalogue, ctx, used)
                    .filter { !it.disabled }.associateBy { it.exercise.id }
                val chosen = previous.firstOrNull {
                    it.matches(filled.slot.group, byId) && it.exerciseId in entries &&
                        it.exerciseId !in used
                }
                if (chosen != null) {
                    used += chosen.exerciseId
                    val exercise = entries.getValue(chosen.exerciseId).exercise.exercise
                    result = result.pick(d, i, Pick.of(exercise, filled.slot, ctx.history[chosen.exerciseId]))
                }
            }
        }
        return result
    }

    private fun BlockExercise.matches(group: MuscleGroup, byId: Map<String, CatalogExercise>): Boolean =
        byId[exerciseId]?.exercise?.muscleGroup == group
}
