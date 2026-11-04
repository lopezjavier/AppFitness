package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.FolderFalta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A `needs_changes` failure of the server's block validation; read by [CoachErrorParser] (T-224 retires it). */
@Serializable
internal data class FolderFaltaDto(
    val code: String,
    @SerialName("routine_index") val routineIndex: Int? = null,
    @SerialName("exercise_key") val exerciseKey: String? = null,
    val data: JsonObject = JsonObject(emptyMap()),
) {
    fun toDomain(): FolderFalta = FolderFalta(
        code,
        routineIndex,
        exerciseKey,
        data.mapValues { (_, value) -> (value as? JsonPrimitive)?.content ?: value.toString() },
    )
}
