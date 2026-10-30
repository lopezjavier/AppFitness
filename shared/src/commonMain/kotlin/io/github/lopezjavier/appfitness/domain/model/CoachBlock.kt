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
)

/** What `save_coach_block` created in Entreno. */
data class BlockSaved(val folderId: String, val routineIds: List<String>)
