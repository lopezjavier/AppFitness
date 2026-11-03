package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.BlockCardio
import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.BlockNotes
import io.github.lopezjavier.appfitness.domain.model.BlockSaved
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.BlockSet
import io.github.lopezjavier.appfitness.domain.model.BlockSetType
import io.github.lopezjavier.appfitness.domain.model.BlockTraining
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.SplitOption
import io.github.lopezjavier.appfitness.domain.model.SplitSession
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

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
    val cardio: BlockCardioDto? = null,
    @SerialName("routine_total_min") val routineTotalMin: List<Int> = emptyList(),
    val intention: String? = null,
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
        cardio = cardio?.toDomain(),
        routineTotalMin = routineTotalMin,
        intention = intention,
    )
}

@Serializable
internal data class BlockCardioDto(
    @SerialName("target_min_week") val targetMinWeek: Int = 0,
    @SerialName("planned_min_week") val plannedMinWeek: Int = 0,
    @SerialName("on_your_own_min") val onYourOwnMin: Int = 0,
    @SerialName("per_routine_min") val perRoutineMin: List<Int> = emptyList(),
    val pace: String = "",
) {
    fun toDomain() = BlockCardio(targetMinWeek, plannedMinWeek, onYourOwnMin, perRoutineMin, pace)
}

@Serializable
internal data class BlockExerciseDto(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("exercise_key") val exerciseKey: String? = null,
    val role: String = "",
    @SerialName("muscle_group") val muscleGroup: String = "",
    /** A plain set count (cardio, older blocks) or the array of planned sets (T-205 lifting). */
    val sets: JsonElement = JsonPrimitive(0),
    @SerialName("rep_min") val repMin: Int = 0,
    @SerialName("rep_max") val repMax: Int = 0,
    @SerialName("rest_seconds") val restSeconds: Int = 0,
    val reason: String = "",
    @SerialName("duration_min") val durationMin: Int? = null,
    val pace: String? = null,
    @SerialName("target_rpe") val targetRpe: Double? = null,
    @SerialName("superset_group") val supersetGroup: Int? = null,
    @SerialName("planned_weight_kg") val plannedWeightKg: Double? = null,
    @SerialName("technique_note") val techniqueNote: String? = null,
) {
    fun toDomain(): BlockExercise {
        val plan = (sets as? JsonArray)?.map { coachPlanJson.decodeFromJsonElement<BlockSetDto>(it).toDomain() }
            .orEmpty()
        val count = if (sets is JsonArray) plan.size else (sets as? JsonPrimitive)?.intOrNull ?: 0
        return BlockExercise(
            exerciseId = exerciseId,
            exerciseKey = exerciseKey,
            role = role,
            muscleGroup = muscleGroup,
            sets = count,
            repMin = repMin,
            repMax = repMax,
            restSeconds = restSeconds,
            reason = reason,
            durationMin = durationMin,
            pace = pace,
            setPlan = plan,
            targetRpe = targetRpe,
            supersetGroup = supersetGroup,
            plannedWeightKg = plannedWeightKg,
            techniqueNote = techniqueNote,
        )
    }
}

@Serializable
internal data class BlockSetDto(
    val type: String = "working",
    @SerialName("rep_min") val repMin: Int = 0,
    @SerialName("rep_max") val repMax: Int = 0,
    val rpe: Double? = null,
    @SerialName("rest_seconds") val restSeconds: Int = 0,
) {
    fun toDomain() = BlockSet(
        type = if (type == "drop") BlockSetType.Drop else BlockSetType.Working,
        repMin = repMin,
        repMax = repMax,
        rpe = rpe,
        restSeconds = restSeconds,
    )
}

@Serializable
internal data class SplitSessionDto(val name: String = "", val focus: List<String> = emptyList()) {
    fun toDomain() = SplitSession(name, focus)
}

@Serializable
internal data class SplitOptionDto(
    val id: String,
    val name: String = "",
    val recommended: Boolean = false,
    val reason: String = "",
    val sessions: List<SplitSessionDto> = emptyList(),
) {
    fun toDomain() = SplitOption(id, name, recommended, reason, sessions.map(SplitSessionDto::toDomain))
}

/** Final payload of `block_splits`. */
@Serializable
internal data class SplitOptionsDto(val options: List<SplitOptionDto> = emptyList())

/** The extras of the `plan_block` final payload that are not stored with the block. */
@Serializable
internal data class PlanBlockResponseDto(
    @SerialName("split_reason") val splitReason: String = "",
    @SerialName("changes_vs_last_block") val changesVsLastBlock: String = "",
    val notes: List<String> = emptyList(),
) {
    fun toDomain() = BlockNotes(splitReason, changesVsLastBlock, notes)
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
internal data class BlockRequestBody(val kind: String, val input: BlockRequestInput, val model: String? = null)

@Serializable
internal data class BlockRequestInput(
    val today: LocalDate,
    @SerialName("plan_id") val planId: String,
    val split: BlockSplitInput,
)

/** The chosen split sent to `plan_block`: its name and sessions (the server stores nothing about the options). */
@Serializable
internal data class BlockSplitInput(val name: String, val sessions: List<SplitSessionDto>)

@Serializable
internal data class SplitRequestBody(val kind: String, val input: SplitRequestInput, val model: String? = null)

@Serializable
internal data class SplitRequestInput(val today: LocalDate)

@Serializable
internal data class RemoveBlockParams(@SerialName("p_plan_id") val planId: String)

@Serializable
internal data class SaveBlockParams(
    @SerialName("p_plan_id") val planId: String,
    @SerialName("p_edits") val edits: List<BlockEditDto>,
)

@Serializable
internal data class BlockEditDto(
    @SerialName("session_index") val sessionIndex: Int,
    val position: Int,
    @SerialName("exercise_id") val exerciseId: String,
)

@Serializable
internal data class SaveBlockResultDto(
    @SerialName("folder_id") val folderId: String,
    @SerialName("routine_ids") val routineIds: List<String> = emptyList(),
) {
    fun toDomain() = BlockSaved(folderId, routineIds)
}
