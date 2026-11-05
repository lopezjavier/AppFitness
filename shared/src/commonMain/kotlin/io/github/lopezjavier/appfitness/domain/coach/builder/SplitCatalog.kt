package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.usecase.FolderReviewCalculator

/**
 * The five routine types of the block builder (07 section 3 "Block builder" (a)). Each holds H1 groups only, in
 * section order. [displayName] is the neutral default routine name (ux-reviewer finalises it in T-222).
 */
enum class RoutineType(val displayName: String, val groups: List<MuscleGroup>) {
    FullBody(
        "Cuerpo completo",
        listOf(
            MuscleGroup.Quads,
            MuscleGroup.Chest,
            MuscleGroup.Back,
            MuscleGroup.Hamstrings,
            MuscleGroup.Shoulders,
            MuscleGroup.Glutes,
        ),
    ),
    Upper("Torso", listOf(MuscleGroup.Chest, MuscleGroup.Back, MuscleGroup.Shoulders)),
    Lower("Pierna", listOf(MuscleGroup.Quads, MuscleGroup.Hamstrings, MuscleGroup.Glutes)),
    Push("Empuje", listOf(MuscleGroup.Chest, MuscleGroup.Shoulders, MuscleGroup.Quads)),
    Pull("Tirón", listOf(MuscleGroup.Back, MuscleGroup.Hamstrings, MuscleGroup.Glutes)),
    ;

    /** Superset pairs fixed by type (07 (b) step 2), as group pairs in section order. */
    val supersetPairs: List<Pair<MuscleGroup, MuscleGroup>>
        get() = when (this) {
            FullBody -> listOf(
                MuscleGroup.Chest to MuscleGroup.Back,
                MuscleGroup.Quads to MuscleGroup.Hamstrings,
                MuscleGroup.Shoulders to MuscleGroup.Glutes,
            )

            Upper -> listOf(MuscleGroup.Chest to MuscleGroup.Back)

            Lower -> listOf(MuscleGroup.Quads to MuscleGroup.Hamstrings)

            Push -> listOf(MuscleGroup.Chest to MuscleGroup.Quads)

            Pull -> listOf(MuscleGroup.Back to MuscleGroup.Hamstrings)
        }
}

/** The reason shown for a recommended split; Spanish copy lives with the UI (T-222). */
enum class SplitReason {
    OnlyRoutineThatTrainsEveryMuscle,
    EveryMuscleTwiceAWeek,
    EveryMuscleThreeTimesSimilarLength,
    EveryMuscleTwiceThreeExercises,
    EveryMuscleThreeTimes,
    EveryMuscleThreeTimesShortSessions,
}

/** One way to spread the routine types over the days; [reason] is set on the recommended split only. */
data class Split(
    val types: List<RoutineType>,
    val recommended: Boolean,
    val reason: SplitReason?,
    val family: SplitFamily,
) {
    /** True for the extra "Cuerpo completo" day of a Torso-Pierna / Empuje-Tirón week (3 and 5 days). */
    val hasFullBodyDay: Boolean
        get() = family != SplitFamily.FullBody && RoutineType.FullBody in types
}

/** The family of a split; a day may only take a type of its split's family (07 "Split names and day types"). */
enum class SplitFamily(val allowedTypes: Set<RoutineType>) {
    FullBody(setOf(RoutineType.FullBody)),
    UpperLower(setOf(RoutineType.Upper, RoutineType.Lower, RoutineType.FullBody)),
    PushPull(setOf(RoutineType.Push, RoutineType.Pull, RoutineType.FullBody)),
}

/** Why a routine type is disabled for a day. */
sealed interface DisabledReason {
    /** Picking the type would leave [group] with no training day. */
    data class LeavesGroupUntrained(val group: MuscleGroup) : DisabledReason

    /** Picking the type would train [group] on every one of the six days (fewer than 2 sets a session). */
    data class GroupEveryDay(val group: MuscleGroup) : DisabledReason
}

/** The type choice of one day: [strengthNote] is a plain advice for "Ganar fuerza", it never disables. */
data class DayTypeOption(
    val type: RoutineType,
    val recommended: Boolean,
    val disabledReason: DisabledReason?,
    val strengthNote: Boolean,
) {
    val enabled: Boolean get() = disabledReason == null
}

/** Splits per number of days (1-6) and the per-day type alternatives. Pure and deterministic. */
object SplitCatalog {
    const val MIN_DAYS = 1
    const val MAX_DAYS = 6

    private val fb = RoutineType.FullBody
    private val up = RoutineType.Upper
    private val lo = RoutineType.Lower
    private val pu = RoutineType.Push
    private val pl = RoutineType.Pull

    private val table: Map<Int, List<List<RoutineType>>> = mapOf(
        1 to listOf(listOf(fb)),
        2 to listOf(listOf(fb, fb), listOf(up, lo), listOf(pu, pl)),
        3 to listOf(listOf(fb, fb, fb), listOf(up, lo, fb), listOf(pu, pl, fb)),
        4 to listOf(listOf(up, lo, up, lo), listOf(pu, pl, pu, pl), listOf(fb, fb, fb, fb)),
        5 to listOf(listOf(up, lo, up, lo, fb), listOf(pu, pl, pu, pl, fb), listOf(fb, fb, fb, fb, fb)),
        6 to listOf(listOf(up, lo, up, lo, up, lo), listOf(pu, pl, pu, pl, pu, pl)),
    )

    private val reasons = mapOf(
        1 to SplitReason.OnlyRoutineThatTrainsEveryMuscle,
        2 to SplitReason.EveryMuscleTwiceAWeek,
        3 to SplitReason.EveryMuscleThreeTimesSimilarLength,
        4 to SplitReason.EveryMuscleTwiceThreeExercises,
        5 to SplitReason.EveryMuscleThreeTimes,
        6 to SplitReason.EveryMuscleThreeTimesShortSessions,
    )

    /** The splits for [days], the recommended one first. Empty outside 1-6. */
    fun splitsFor(days: Int): List<Split> = table[days].orEmpty().mapIndexed { i, types ->
        Split(types, recommended = i == 0, reason = if (i == 0) reasons[days] else null, family = familyOf(types))
    }

    private fun familyOf(types: List<RoutineType>): SplitFamily = when {
        types.any { it == up || it == lo } -> SplitFamily.UpperLower
        types.any { it == pu || it == pl } -> SplitFamily.PushPull
        else -> SplitFamily.FullBody
    }

    /** Days a group is trained in [types]. */
    fun frequency(types: List<RoutineType>, group: MuscleGroup): Int = types.count { group in it.groups }

    /**
     * The family's types for day [dayIndex] of [chosen] (the types currently picked for every day), the split's own
     * type marked recommended. A type is disabled when swapping it in would leave a group untrained or train one on
     * all six days.
     */
    fun dayOptions(
        split: Split,
        chosen: List<RoutineType>,
        dayIndex: Int,
        intention: TrainingIntention?,
    ): List<DayTypeOption> = RoutineType.entries.filter { offered(split, dayIndex, it) }.map { type ->
        val result = chosen.toMutableList().also { it[dayIndex] = type }
        val frequencies = ALL_GROUPS.associateWith { frequency(result, it) }
        val untrained = ALL_GROUPS.firstOrNull { frequencies.getValue(it) == 0 }
        val everyDay = ALL_GROUPS.firstOrNull { frequencies.getValue(it) == MAX_DAYS }
        val reason = when {
            untrained != null -> DisabledReason.LeavesGroupUntrained(untrained)
            everyDay != null -> DisabledReason.GroupEveryDay(everyDay)
            else -> null
        }
        DayTypeOption(
            type = type,
            recommended = split.types[dayIndex] == type,
            disabledReason = reason,
            strengthNote = intention == TrainingIntention.Strength && frequencies.values.any { it == 1 },
        )
    }

    /** A day offers the family's types; "Cuerpo completo" only where the split already has it, never on a Torso day. */
    private fun offered(split: Split, dayIndex: Int, type: RoutineType): Boolean {
        val fullBodyDay = split.types[dayIndex] == fb
        return type in split.family.allowedTypes && (type == fb) == fullBodyDay
    }

    /** The six H1 groups in the review's order. */
    val ALL_GROUPS: List<MuscleGroup> get() = FolderReviewCalculator.GROUPS
}
