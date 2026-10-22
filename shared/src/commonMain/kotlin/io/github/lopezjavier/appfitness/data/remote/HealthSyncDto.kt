package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.HealthReading
import io.github.lopezjavier.appfitness.domain.model.HealthSyncState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Row of `public.health_sync_state` limited to the columns the app reads. */
@Serializable
internal data class HealthSyncStateDto(
    @SerialName("last_synced_at") val lastSyncedAt: String,
    @SerialName("changes_token") val changesToken: String? = null,
) {
    fun toDomain() = HealthSyncState(Instant.parse(lastSyncedAt), changesToken)

    companion object {
        const val COLUMNS = "last_synced_at,changes_token"
    }
}

/** One element of the `p_rows` array of `import_health_measurements`. */
@Serializable
internal data class HealthReadingDto(
    @SerialName("external_id") val externalId: String,
    @SerialName("measured_at") val measuredAt: String,
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("body_fat_pct") val bodyFatPct: Double? = null,
    @SerialName("lean_mass_kg") val leanMassKg: Double? = null,
    @SerialName("water_kg") val waterKg: Double? = null,
    @SerialName("bone_kg") val boneKg: Double? = null,
    @SerialName("bmr_kcal") val bmrKcal: Int? = null,
) {
    companion object {
        fun from(reading: HealthReading) = HealthReadingDto(
            externalId = reading.externalId,
            measuredAt = reading.measuredAt.toString(),
            weightKg = reading.weightKg,
            bodyFatPct = reading.bodyFatPct,
            leanMassKg = reading.leanMassKg,
            waterKg = reading.waterKg,
            boneKg = reading.boneKg,
            bmrKcal = reading.bmrKcal,
        )
    }
}

/** Arguments of the `import_health_measurements` RPC. */
@Serializable
internal data class ImportHealthParams(
    @SerialName("p_rows") val rows: List<HealthReadingDto>,
    @SerialName("p_synced_at") val syncedAt: String,
    @SerialName("p_changes_token") val changesToken: String?,
)
