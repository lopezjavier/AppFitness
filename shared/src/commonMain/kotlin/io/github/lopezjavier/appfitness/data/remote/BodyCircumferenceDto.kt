package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** DB values of `public.circumference_site`. */
@Serializable
internal enum class CircumferenceSiteDto(val domain: CircumferenceSite) {
    @SerialName("neck")
    Neck(CircumferenceSite.Neck),

    @SerialName("chest")
    Chest(CircumferenceSite.Chest),

    @SerialName("waist")
    Waist(CircumferenceSite.Waist),

    @SerialName("hip")
    Hip(CircumferenceSite.Hip),

    @SerialName("arm")
    Arm(CircumferenceSite.Arm),

    @SerialName("thigh")
    Thigh(CircumferenceSite.Thigh),

    @SerialName("calf")
    Calf(CircumferenceSite.Calf),
    ;

    companion object {
        fun of(site: CircumferenceSite): CircumferenceSiteDto = entries.first { it.domain == site }
    }
}

/** Row of `public.body_circumferences` limited to the columns the app reads. `numeric(5,1)` decodes as Double. */
@Serializable
internal data class BodyCircumferenceDto(
    val id: String,
    @SerialName("measured_at") val measuredAt: String,
    val site: CircumferenceSiteDto,
    @SerialName("value_cm") val valueCm: Double,
    @SerialName("created_at") val createdAt: String,
) {
    fun toDomain(): BodyCircumference =
        BodyCircumference(id, site.domain, valueCm, Instant.parse(measuredAt), Instant.parse(createdAt))

    companion object {
        const val COLUMNS = "id,measured_at,site,value_cm,created_at"
    }
}

/** Body of `POST /body_circumferences`: `user_id` takes its default (the caller). */
@Serializable
internal data class BodyCircumferenceInsertDto(
    val site: CircumferenceSiteDto,
    @SerialName("value_cm") val valueCm: Double,
    @SerialName("measured_at") val measuredAt: String,
    val source: MeasurementSourceDto,
)

/** Body of `PATCH /body_circumferences?id=eq.<id>`. */
@Serializable
internal data class BodyCircumferenceUpdateDto(
    @SerialName("value_cm") val valueCm: Double,
    @SerialName("measured_at") val measuredAt: String,
)
