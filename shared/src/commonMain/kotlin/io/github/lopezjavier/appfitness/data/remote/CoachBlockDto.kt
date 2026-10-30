package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.BlockSaved
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.BlockTraining
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `coach_plans.training` (shape in planning/03-data-model.md). */
@Serializable
internal data class BlockTrainingDto(
    @SerialName("days_per_week") val daysPerWeek: Int = 0,
    @SerialName("days_chosen") val daysChosen: Boolean = false,
    @SerialName("session_minutes") val sessionMinutes: Int = 0,
    @SerialName("rep_scheme") val repScheme: String = "",
    @SerialName("block_weeks") val blockWeeks: Int = 4,
    val equipment: List<String> = emptyList(),
    @SerialName("weekly_sets") val weeklySets: Map<String, Int> = emptyMap(),
    @SerialName("sessions_per_muscle") val sessionsPerMuscle: Map<String, Int> = emptyMap(),
) {
    fun toDomain() = BlockTraining(
        daysPerWeek = daysPerWeek,
        daysChosen = daysChosen,
        sessionMinutes = sessionMinutes,
        repScheme = repScheme,
        blockWeeks = blockWeeks,
        equipment = equipment,
        weeklySets = weeklySets,
        sessionsPerMuscle = sessionsPerMuscle,
    )
}

@Serializable
internal data class BlockExerciseDto(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("exercise_key") val exerciseKey: String? = null,
    val role: String = "",
    @SerialName("muscle_group") val muscleGroup: String = "",
    val sets: Int = 0,
    @SerialName("rep_min") val repMin: Int = 0,
    @SerialName("rep_max") val repMax: Int = 0,
    @SerialName("rest_seconds") val restSeconds: Int = 0,
    val reason: String = "",
) {
    fun toDomain() = BlockExercise(
        exerciseId = exerciseId,
        exerciseKey = exerciseKey,
        role = role,
        muscleGroup = muscleGroup,
        sets = sets,
        repMin = repMin,
        repMax = repMax,
        restSeconds = restSeconds,
        reason = reason,
    )
}

/** Row of `coach_plan_sessions` (owner select). */
@Serializable
internal data class BlockSessionDto(
    val week: Int,
    @SerialName("session_index") val sessionIndex: Int,
    val name: String = "",
    val focus: List<String> = emptyList(),
    val exercises: List<BlockExerciseDto> = emptyList(),
    @SerialName("routine_id") val routineId: String? = null,
) {
    fun toDomain() = BlockSession(week, sessionIndex, name, focus, exercises.map(BlockExerciseDto::toDomain), routineId)
}

/** The block columns of a `coach_plans` row; `training` is null until the coach proposed a block. */
@Serializable
internal data class CoachBlockPlanDto(
    val id: String,
    val training: BlockTrainingDto? = null,
    @SerialName("folder_id") val folderId: String? = null,
)

/** A block is saved when its folder exists or any session already points to a routine. */
internal fun blockOf(plan: CoachBlockPlanDto, sessions: List<BlockSessionDto>): CoachBlock? {
    val training = plan.training
    if (training == null || sessions.isEmpty()) return null
    return CoachBlock(
        planId = plan.id,
        training = training.toDomain(),
        sessions = sessions.map(BlockSessionDto::toDomain),
        saved = plan.folderId != null || sessions.any { it.routineId != null },
    )
}

@Serializable
internal data class BlockRequestBody(val kind: String, val input: BlockRequestInput)

@Serializable
internal data class BlockRequestInput(val today: LocalDate, @SerialName("plan_id") val planId: String)

@Serializable
internal data class SaveBlockParams(@SerialName("p_plan_id") val planId: String)

@Serializable
internal data class SaveBlockResultDto(
    @SerialName("folder_id") val folderId: String,
    @SerialName("routine_ids") val routineIds: List<String> = emptyList(),
) {
    fun toDomain() = BlockSaved(folderId, routineIds)
}
