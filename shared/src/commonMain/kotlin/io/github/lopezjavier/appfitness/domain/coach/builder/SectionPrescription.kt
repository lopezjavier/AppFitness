package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention

/** What a slot is for. [Standard] is any slot of a non-strength intention. */
enum class SlotKind { Main, Accessory, Standard, Extra }

/**
 * One exercise slot of a routine with its prescription (07 (b)). [downgraded] marks a strength main slot that became an
 * accessory because the user's equipment offers no main lift for the group (07 (d)).
 */
data class Slot(
    val group: MuscleGroup,
    val kind: SlotKind,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val rpe: Int = SectionPrescription.RPE,
    val downgraded: Boolean = false,
)

/** The muscle sections of one routine as slots, in the order they are done. */
data class RoutinePlan(val type: RoutineType, val slots: List<Slot>)

/** Per-section prescription of the block builder (07 section 3 "Block builder" (b)); pure. */
object SectionPrescription {
    const val WEEKLY_SETS = 10
    const val RPE = 8
    const val MAX_SETS_PER_SLOT = 5
    const val MAIN_SETS_CAP = 4
    const val MAIN_SETS_WHEN_SPLIT = 3
    const val EXTRA_SETS = 2
    private const val HYPERTROPHY_MIN = 8
    private const val HYPERTROPHY_MAX = 12
    private const val HYPERTROPHY_REST = 120
    private const val MAIN_MIN = 4
    private const val MAIN_MAX = 6
    private const val MAIN_REST = 180
    private const val ACCESSORY_MIN = 6
    private const val ACCESSORY_MAX = 8

    /**
     * Sets on the [position]th (0-based) training day of a group trained [frequency] days: floor(t/f) + rest, with
     * t = [weeklySets] (10 unless the next-block builder raises or lowers the group's target).
     */
    fun setsPerSession(frequency: Int, position: Int, weeklySets: Int = WEEKLY_SETS): Int =
        weeklySets / frequency + if (position < weeklySets % frequency) 1 else 0

    /** [total] sets in as few slots as the 5-set cap allows, as even as possible, larger first. */
    fun distribute(total: Int): List<Int> {
        if (total <= 0) return emptyList()
        val slots = (total + MAX_SETS_PER_SLOT - 1) / MAX_SETS_PER_SLOT
        return List(slots) { i -> total / slots + if (i < total % slots) 1 else 0 }
    }

    /**
     * The routines of [types] with their slots before any pick. [hasMainLift] says whether the equipment offers a main
     * lift for a group; a strength main slot without one becomes an accessory slot. [targets] are the weekly sets per
     * group; a group without an entry keeps 10.
     */
    fun prescribe(
        types: List<RoutineType>,
        intention: TrainingIntention?,
        targets: Map<MuscleGroup, Int> = emptyMap(),
        hasMainLift: (MuscleGroup) -> Boolean = { true },
    ): List<RoutinePlan> {
        val strength = intention == TrainingIntention.Strength
        return types.mapIndexed { day, type ->
            val sections = type.groups.map { group ->
                val position = types.take(day).count { group in it.groups }
                val weekly = targets[group] ?: WEEKLY_SETS
                val sets = setsPerSession(SplitCatalog.frequency(types, group), position, weekly)
                if (strength) strengthSlots(group, sets, hasMainLift(group)) else standardSlots(group, sets)
            }
            val slots = if (strength) {
                sections.flatten().filter { it.kind == SlotKind.Main } +
                    sections.flatten().filter { it.kind != SlotKind.Main }
            } else {
                sections.flatten()
            }
            RoutinePlan(type, slots)
        }
    }

    /** The slot of an extra: 2 sets with the routine's accessory values. */
    fun extraSlot(group: MuscleGroup, intention: TrainingIntention?): Slot =
        if (intention == TrainingIntention.Strength) {
            Slot(group, SlotKind.Extra, EXTRA_SETS, ACCESSORY_MIN, ACCESSORY_MAX, HYPERTROPHY_REST)
        } else {
            Slot(group, SlotKind.Extra, EXTRA_SETS, HYPERTROPHY_MIN, HYPERTROPHY_MAX, HYPERTROPHY_REST)
        }

    private fun standardSlots(group: MuscleGroup, sets: Int): List<Slot> = distribute(sets).map {
        Slot(group, SlotKind.Standard, it, HYPERTROPHY_MIN, HYPERTROPHY_MAX, HYPERTROPHY_REST)
    }

    private fun strengthSlots(group: MuscleGroup, sets: Int, mainAvailable: Boolean): List<Slot> {
        val mainSets = if (sets <= MAIN_SETS_CAP) sets else MAIN_SETS_WHEN_SPLIT
        val main = if (mainAvailable) {
            Slot(group, SlotKind.Main, mainSets, MAIN_MIN, MAIN_MAX, MAIN_REST)
        } else {
            accessory(group, mainSets, downgraded = true)
        }
        val accessories = distribute(sets - mainSets).map { accessory(group, it, downgraded = false) }
        return listOf(main) + accessories
    }

    private fun accessory(group: MuscleGroup, sets: Int, downgraded: Boolean) = Slot(
        group,
        SlotKind.Accessory,
        sets,
        ACCESSORY_MIN,
        ACCESSORY_MAX,
        HYPERTROPHY_REST,
        downgraded = downgraded,
    )
}
