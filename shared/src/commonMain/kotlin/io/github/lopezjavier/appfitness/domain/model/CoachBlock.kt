package io.github.lopezjavier.appfitness.domain.model

/** The four-week training block the coach proposed for a plan (US-72), as stored; [saved] once it became routines. */
data class CoachBlock(
    val planId: String,
    val training: BlockTraining,
    val sessions: List<BlockSession>,
    val saved: Boolean,
) {
    /**
     * The sessions grouped by week, weeks ascending and sessions by index. The stored block repeats the same sessions
     * every week, so each week lists the same workouts.
     */
    fun weeks(): List<BlockWeek> = sessions
        .groupBy { it.week }
        .entries
        .sortedBy { it.key }
        .map { (week, list) -> BlockWeek(week, list.sortedBy { it.sessionIndex }) }
}

/** One week of a [CoachBlock]. */
data class BlockWeek(val week: Int, val sessions: List<BlockSession>)

/** `coach_plans.training`: the frequency the block uses and the weekly work per muscle group. */
data class BlockTraining(
    val daysPerWeek: Int,
    /** True when the coach chose [daysPerWeek] instead of the user. */
    val daysChosen: Boolean,
    val sessionMinutes: Int,
    val repScheme: String,
    val blockWeeks: Int,
    val equipment: List<String>,
    /** Weekly sets by muscle group key. */
    val weeklySets: Map<String, Int>,
    /** Days per week each muscle group is trained, by muscle group key. */
    val sessionsPerMuscle: Map<String, Int>,
    /** The cardio plan of the block; null for a block stored before cardio closed every routine. */
    val cardio: BlockCardio? = null,
    /** Total minutes of each routine (lifting plus cardio), by session index; empty without cardio. */
    val routineTotalMin: List<Int> = emptyList(),
    /** Training intention the block was built for; null for an older block. */
    val intention: String? = null,
)

/** `coach_plans.training.cardio`: the weekly minutes, how the routines hold them and the pace. */
data class BlockCardio(
    val targetMinWeek: Int,
    val plannedMinWeek: Int,
    /** Minutes of the target the routines do not hold: the user does them on their own. */
    val onYourOwnMin: Int,
    val perRoutineMin: List<Int>,
    val pace: String,
)

/** One workout of the block (a row of `coach_plan_sessions`); [routineId] is set once the block was saved. */
data class BlockSession(
    val week: Int,
    val sessionIndex: Int,
    val name: String,
    val focus: List<String>,
    val exercises: List<BlockExercise>,
    val routineId: String?,
)

data class BlockExercise(
    val exerciseId: String,
    /** Catalog key, for the thumbnail; null when the server did not send it. */
    val exerciseKey: String?,
    val role: String,
    val muscleGroup: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    /** Why the coach chose this exercise (Spanish text from the function). */
    val reason: String,
    /** Planned minutes of a cardio slot; null for lifting. */
    val durationMin: Int? = null,
    /** Cardio intensity code (e.g. "suave"); null for lifting. */
    val pace: String? = null,
    /** The planned sets one by one; empty for cardio and for blocks stored with a plain set count. */
    val setPlan: List<BlockSet> = emptyList(),
    /** Target effort (RPE) of the working sets; null for cardio or older blocks. */
    val targetRpe: Double? = null,
    /** Exercises of the same routine sharing a group number are done as a superset; null when not in one. */
    val supersetGroup: Int? = null,
    /** Planned working weight from the user's history; null when there is none. */
    val plannedWeightKg: Double? = null,
    /** One-line technique tip for an exercise new to the user. */
    val techniqueNote: String? = null,
) {
    /** Cardio slots carry minutes, not sets and reps. */
    val isCardio: Boolean get() = role == CARDIO_ROLE

    companion object {
        const val CARDIO_ROLE = "cardio"
    }
}

enum class BlockSetType { Working, Drop }

/** One planned set of a lifting exercise. */
data class BlockSet(val type: BlockSetType, val repMin: Int, val repMax: Int, val rpe: Double?, val restSeconds: Int)

/** A way to split the training days into sessions, offered before the block is built (`block_splits`). */
data class SplitOption(
    val id: String,
    val name: String,
    val recommended: Boolean,
    val reason: String,
    val sessions: List<SplitSession>,
)

/** One session of a [SplitOption] and the muscle group keys it focuses on. */
data class SplitSession(val name: String, val focus: List<String>)

/** What the generator says about a block beyond its sessions (the `plan_block` response). */
data class BlockNotes(
    val splitReason: String = "",
    val changesVsLastBlock: String = "",
    /** Checker notes such as `below_recommended`. */
    val notes: List<String> = emptyList(),
)

/**
 * A pending replacement applied by "Guardar bloque": the exercise at [position] (1-based) of workout [sessionIndex]
 * becomes [exerciseId] in every week.
 */
data class BlockEdit(val sessionIndex: Int, val position: Int, val exerciseId: String)

/** What `save_coach_block` created in Entreno. */
data class BlockSaved(val folderId: String, val routineIds: List<String>)
