package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextLine
import io.github.lopezjavier.appfitness.domain.model.FreeTextStatus
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val KIND_WIRE = mapOf(
    FreeTextKind.Intake to "intake",
    FreeTextKind.CheckIn to "check_in",
    FreeTextKind.Discomfort to "discomfort",
    FreeTextKind.Review to "review",
)

private val STATUS_WIRE = mapOf(
    "sent" to FreeTextStatus.Sent,
    "excluded" to FreeTextStatus.Excluded,
    "over_limit" to FreeTextStatus.OverLimit,
)

/**
 * A `coach_user_intake` row (owner-select RLS) or the jsonb `save_coach_intake` returns. Columns dropped by T-198
 * (situation, injured area, bests, cutting calories) are ignored by [coachPlanJson] if an old row still has them.
 */
@Serializable
internal data class CoachIntakeDto(
    @SerialName("training_years") val trainingYears: Double? = null,
    @SerialName("known_maintenance_kcal") val knownMaintenanceKcal: Int? = null,
    @SerialName("free_text") val freeText: String? = null,
    @SerialName("maintenance_handover_at") val maintenanceHandoverAt: String? = null,
) {
    fun toDomain() = CoachIntake(
        trainingYears = trainingYears,
        knownMaintenanceKcal = knownMaintenanceKcal,
        freeText = freeText,
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
            is IntakeUpdate.FreeText -> "free_text" to update.text?.let(::JsonPrimitive)
        }
        return JsonObject(mapOf("p" to JsonObject(mapOf(key to (value ?: JsonNull)))))
    }
}

@Serializable
internal data class FreeTextLineDto(
    val kind: String,
    @SerialName("source_id") val sourceId: String,
    val date: LocalDate,
    val text: String,
    val status: String,
) {
    fun toDomain() = FreeTextLine(
        kind = KIND_WIRE.entries.firstOrNull { it.value == kind }?.key ?: FreeTextKind.Unknown,
        sourceId = sourceId,
        date = date,
        text = text,
        // An unknown status is never treated as sent: the user must not believe a line is read when it may not be.
        status = STATUS_WIRE[status] ?: FreeTextStatus.OverLimit,
    )
}

@Serializable
internal data class FreeTextLinesParams(@SerialName("p_today") val today: LocalDate)

@Serializable
internal data class ExcludeLineParams(
    @SerialName("p_kind") val kind: String,
    @SerialName("p_id") val id: String,
    @SerialName("p_excluded") val excluded: Boolean,
) {
    companion object {
        fun of(kind: FreeTextKind, id: String, excluded: Boolean) =
            ExcludeLineParams(KIND_WIRE[kind] ?: "unknown", id, excluded)
    }
}
