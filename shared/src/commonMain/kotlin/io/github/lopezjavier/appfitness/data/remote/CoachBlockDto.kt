package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderInputs
import io.github.lopezjavier.appfitness.domain.coach.builder.BuiltBlock
import io.github.lopezjavier.appfitness.domain.coach.builder.BuiltExercise
import io.github.lopezjavier.appfitness.domain.coach.builder.BuiltSession
import io.github.lopezjavier.appfitness.domain.coach.builder.ExerciseHistory
import io.github.lopezjavier.appfitness.domain.model.BlockCardio
import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.BlockSaved
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.BlockSet
import io.github.lopezjavier.appfitness.domain.model.BlockSetType
import io.github.lopezjavier.appfitness.domain.model.BlockTraining
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

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
    @SerialName("split_reason") val splitReason: String? = null,
    @SerialName("changes_vs_last_block") val changesVsLastBlock: String? = null,
    val notes: List<String> = emptyList(),
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
        splitReason = splitReason,
        changesVsLastBlock = changesVsLastBlock,
        notes = notes,
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
    @SerialName("plan_id") val planId: String? = null,
) {
    fun toDomain() = BlockSaved(folderId, routineIds, planId)
}

@Serializable
internal data class BuilderInputsParams(@SerialName("p_today") val today: LocalDate)

/** Answer of `coach_builder_inputs`. */
@Serializable
internal data class BuilderInputsDto(
    val history: List<BuilderHistoryDto> = emptyList(),
    @SerialName("cardio_options") val cardioOptions: List<String> = emptyList(),
    @SerialName("cardio_default") val cardioDefault: String? = null,
) {
    fun toDomain() = BuilderInputs(
        history = history.associate { it.exerciseId to ExerciseHistory(it.sessions8w, it.lastWeightKg, it.lastReps) },
        cardioOptions = cardioOptions,
        cardioDefault = cardioDefault,
    )
}

@Serializable
internal data class BuilderHistoryDto(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("sessions_8w") val sessions8w: Int = 0,
    @SerialName("last_weight_kg") val lastWeightKg: Double? = null,
    @SerialName("last_reps") val lastReps: Int? = null,
)

private fun TrainingIntention.wire() = when (this) {
    TrainingIntention.Strength -> "strength"
    TrainingIntention.Muscle -> "muscle"
    TrainingIntention.FatLoss -> "fat_loss"
    TrainingIntention.Recomposition -> "recomposition"
    TrainingIntention.Maintenance -> "maintenance"
}

/**
 * Parameters of `save_built_coach_block`, built as JSON by hand: the server validates a key's type when the key is
 * present, so absent values (no planned weight, no superset) are left out instead of sent as null.
 */
internal class BuiltBlockParams(
    private val planId: String,
    private val block: BuiltBlock,
    private val folderName: String?,
    private val previousPlanId: String? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("p_plan_id", planId)
        put("p_training", training())
        put("p_sessions", buildJsonArray { block.sessions.forEach { add(session(it)) } })
        if (folderName != null) put("p_folder_name", folderName)
        if (previousPlanId != null) put("p_previous_plan_id", previousPlanId)
    }

    private fun training() = buildJsonObject {
        put("days_per_week", block.daysPerWeek)
        put("block_weeks", BuiltBlock.BLOCK_WEEKS)
        block.intention?.let { put("intention", it.wire()) }
        put("equipment", buildJsonArray { block.equipment.forEach { add(JsonPrimitive(it.wire)) } })
        put("session_minutes", block.sessionMinutes)
        put("routine_total_min", buildJsonArray { block.routineTotalMin.forEach { add(JsonPrimitive(it)) } })
        put(
            "cardio",
            buildJsonObject {
                put("target_min_week", block.cardio.targetMinWeek)
                put("planned_min_week", block.cardio.plannedMinWeek)
                put("on_your_own_min", block.cardio.onYourOwnMin)
                put("per_routine_min", buildJsonArray { block.cardio.perRoutineMin.forEach { add(JsonPrimitive(it)) } })
                put("pace", BuiltExercise.PACE)
            },
        )
        put("source", block.source)
    }

    private fun session(s: BuiltSession) = buildJsonObject {
        put("name", s.name)
        put("focus", buildJsonArray { s.focus.forEach { add(JsonPrimitive(it)) } })
        put("exercises", buildJsonArray { s.exercises.forEach { add(exercise(it)) } })
    }

    private fun exercise(e: BuiltExercise) = buildJsonObject {
        put("role", e.role)
        put("exercise_id", e.exerciseId)
        put("sets", e.sets)
        if (e.role == BuiltExercise.ROLE_CARDIO) {
            e.durationMin?.let { put("duration_min", it) }
            e.pace?.let { put("pace", it) }
        } else {
            put("rep_min", e.repMin)
            put("rep_max", e.repMax)
            put("rest_seconds", e.restSeconds)
            put("target_rpe", e.targetRpe)
            e.plannedWeightKg?.let { put("planned_weight_kg", it) }
            e.supersetGroup?.let { put("superset_group", it) }
        }
    }
}
