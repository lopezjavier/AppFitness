package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.TrainingIntention

/** Slots at [first] and [second] (indexes into the fitted slots) are done as a superset. */
data class SupersetPair(val first: Int, val second: Int)

sealed interface FitResult

/** The routine fits the session length. [cutSets] is true when a set was removed ("Por debajo de lo recomendado"). */
data class Fit(val slots: List<Slot>, val supersets: List<SupersetPair>, val cutSets: Boolean, val seconds: Int) :
    FitResult

/** Even with 2 sets per slot the routine needs [minutesNeeded] minutes and the session has [limitMinutes]. */
data class DoesNotFit(val limitMinutes: Int, val minutesNeeded: Int) : FitResult

/**
 * Time fit of one routine (07 section 3 "Block builder" (b)); port of `routineSeconds` of the former
 * server routine checker (04 section 9.6, removed in T-224) with the slot values, not picked exercises.
 * A cut on a superset slot also cuts its partner so the pair keeps equal sets (the server refuses a mismatch).
 */
object TimeFit {
    const val SECONDS_PER_REP = 4
    const val SETUP_SECONDS = 30
    const val MIN_SETS = 2
    private const val STRENGTH_MAIN_REST = 180
    private const val STRENGTH_REDUCED_REST = 120
    private const val SECONDS_PER_MINUTE = 60

    private fun work(slot: Slot) = (slot.repMin + slot.repMax + 1) / 2 * SECONDS_PER_REP

    /** Seconds of [slots] with the [supersets] pairs; every set carries its rest, one setup per exercise. */
    fun seconds(slots: List<Slot>, supersets: List<SupersetPair> = emptyList()): Int {
        val paired = supersets.flatMap { listOf(it.first, it.second) }.toSet()
        val single = slots.withIndex().filter { it.index !in paired }
            .sumOf { (_, s) -> SETUP_SECONDS + s.sets * (work(s) + s.restSeconds) }
        val pairs = supersets.sumOf { p ->
            val a = slots[p.first]
            val b = slots[p.second]
            2 * SETUP_SECONDS + maxOf(a.sets, b.sets) * (work(a) + work(b) + maxOf(a.restSeconds, b.restSeconds))
        }
        return single + pairs
    }

    /** Minutes by which the [extras] push a fitted routine past [limitMinutes]; 0 when it still fits. */
    fun overMinutes(fit: Fit, extras: List<Slot>, limitMinutes: Int): Int {
        val total = fit.seconds + seconds(extras)
        val over = total - limitMinutes * SECONDS_PER_MINUTE
        return if (over <= 0) 0 else (over + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount") // The ordered steps of 07 (b), each may end the fit.
    fun fit(plan: RoutinePlan, intention: TrainingIntention?, limitMinutes: Int): FitResult {
        val limit = limitMinutes * SECONDS_PER_MINUTE
        var slots = plan.slots
        var pairs = emptyList<SupersetPair>()
        var cut = false
        fun fits() = seconds(slots, pairs) <= limit
        fun result() = Fit(slots, pairs, cut, seconds(slots, pairs))

        if (fits()) return result()
        if (intention == TrainingIntention.Strength) {
            slots =
                slots.map {
                    if (it.restSeconds ==
                        STRENGTH_MAIN_REST
                    ) {
                        it.copy(restSeconds = STRENGTH_REDUCED_REST)
                    } else {
                        it
                    }
                }
            if (fits()) return result()
        } else {
            for (pair in candidatePairs(plan.type, slots)) {
                pairs = pairs + pair
                if (fits()) return result()
            }
        }
        while (!fits()) {
            val target = cutTarget(slots) ?: return DoesNotFit(limitMinutes, minutesCeil(seconds(slots, pairs)))
            val partner = pairs.firstOrNull { target == it.first || target == it.second }
                ?.let { if (it.first == target) it.second else it.first }
            slots = slots.mapIndexed { i, s -> if (i == target || i == partner) s.copy(sets = s.sets - 1) else s }
            cut = true
        }
        return result()
    }

    private fun minutesCeil(seconds: Int) = (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE

    /** Fixed pairs of the type: the k-th slot of the first group with the k-th of the second when sets are equal. */
    private fun candidatePairs(type: RoutineType, slots: List<Slot>): List<SupersetPair> =
        type.supersetPairs.flatMap { (a, b) ->
            val first = slots.withIndex().filter { it.value.group == a }.map { it.index }
            val second = slots.withIndex().filter { it.value.group == b }.map { it.index }
            first.zip(second).filter { (i, j) -> slots[i].sets == slots[j].sets }.map { (i, j) -> SupersetPair(i, j) }
        }

    /** The slot to lose a set: most sets, accessory before main, later first; null when all are at the minimum. */
    private fun cutTarget(slots: List<Slot>): Int? = slots.withIndex()
        .filter { it.value.sets > MIN_SETS }
        .sortedWith(
            compareByDescending<IndexedValue<Slot>> { it.value.sets }
                .thenBy { if (it.value.kind == SlotKind.Main) 1 else 0 }
                .thenByDescending { it.index },
        )
        .firstOrNull()?.index
}
