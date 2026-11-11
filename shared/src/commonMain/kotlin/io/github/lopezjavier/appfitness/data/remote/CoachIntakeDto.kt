package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A `coach_user_intake` row (owner-select RLS) or the jsonb `save_coach_intake` returns. Columns dropped by T-198
 * (situation, injured area, bests, cutting calories) and the free-text note removed by T-277 are ignored by
 * [coachPlanJson] if the server still sends them.
 */
@Serializable
internal data class CoachIntakeDto(
    @SerialName("training_years") val trainingYears: Double? = null,
    @SerialName("known_maintenance_kcal") val knownMaintenanceKcal: Int? = null,
    @SerialName("maintenance_handover_at") val maintenanceHandoverAt: String? = null,
) {
    fun toDomain() = CoachIntake(
        trainingYears = trainingYears,
        knownMaintenanceKcal = knownMaintenanceKcal,
        maintenanceHandoverPending = knownMaintenanceKcal != null && maintenanceHandoverAt == null,
    )
}

/**
 * Body of `save_coach_intake`: only the edited key is present (the server keeps absent keys); an explicit null
 * clears the field.
 */
internal object SaveIntakeParams {
    fun of(update: IntakeUpdate): JsonObject {
        val (key, value) = when (update) {
            is IntakeUpdate.TrainingYears -> "training_years" to update.years?.let(::JsonPrimitive)
            is IntakeUpdate.KnownMaintenance -> "known_maintenance_kcal" to update.kcal?.let(::JsonPrimitive)
        }
        return JsonObject(mapOf("p" to JsonObject(mapOf(key to (value ?: JsonNull)))))
    }
}
