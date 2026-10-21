package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.ManualWeightSave
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** DB values of `public.measurement_source`. */
@Serializable
internal enum class MeasurementSourceDto(val domain: MeasurementSource) {
    @SerialName("manual")
    Manual(MeasurementSource.Manual),

    @SerialName("health_connect")
    HealthConnect(MeasurementSource.HealthConnect),
}

/** Row of `public.body_measurements` limited to the columns the app reads. `numeric(5,2)` decodes as Double. */
@Serializable
internal data class BodyMeasurementDto(
    val id: String,
    @SerialName("measured_at") val measuredAt: String,
    @SerialName("weight_kg") val weightKg: Double,
    val source: MeasurementSourceDto,
    @SerialName("body_fat_pct") val bodyFatPct: Double? = null,
) {
    fun toDomain(): BodyMeasurement =
        BodyMeasurement(id, Instant.parse(measuredAt), weightKg, source.domain, bodyFatPct)

    companion object {
        const val COLUMNS = "id,measured_at,weight_kg,source,body_fat_pct"
    }
}

/** Body of `POST /body_measurements`: `user_id` takes its default (the caller). */
@Serializable
internal data class BodyMeasurementInsertDto(
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("measured_at") val measuredAt: String,
    val source: MeasurementSourceDto,
)

/** Body of `PATCH /body_measurements?id=eq.<id>`. */
@Serializable
internal data class BodyMeasurementUpdateDto(
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("measured_at") val measuredAt: String,
)

/** Arguments of the `save_body_measurement` RPC; null `p_id` and `p_body_fat_pct` are sent explicitly. */
@Serializable
internal data class SaveBodyMeasurementParams(
    @SerialName("p_id") val id: String?,
    @SerialName("p_weight_kg") val weightKg: Double,
    @SerialName("p_body_fat_pct") val bodyFatPct: Double?,
    @SerialName("p_measured_at") val measuredAt: String,
    @SerialName("p_day_start") val dayStart: String,
    @SerialName("p_day_end") val dayEnd: String,
) {
    companion object {
        fun from(save: ManualWeightSave) = SaveBodyMeasurementParams(
            id = save.id,
            weightKg = save.weightKg,
            bodyFatPct = save.bodyFatPct,
            measuredAt = save.measuredAt.toString(),
            dayStart = save.dayStart.toString(),
            dayEnd = save.dayEnd.toString(),
        )
    }
}
