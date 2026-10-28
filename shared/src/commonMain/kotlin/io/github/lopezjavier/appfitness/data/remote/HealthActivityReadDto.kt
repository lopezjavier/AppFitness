package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.DailyActivity
import io.github.lopezjavier.appfitness.domain.model.RestingHeartRate
import io.github.lopezjavier.appfitness.domain.model.SleepSession
import io.github.lopezjavier.appfitness.domain.model.SleepStage
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Row of `public.daily_activity` limited to the columns the app reads. */
@Serializable
internal data class DailyActivityRowDto(
    val day: String,
    val steps: Int? = null,
    @SerialName("active_kcal") val activeKcal: Double? = null,
    @SerialName("total_kcal") val totalKcal: Double? = null,
) {
    fun toDomain() = DailyActivity(LocalDate.parse(day), steps, activeKcal, totalKcal)

    companion object {
        const val COLUMNS = "day,steps,active_kcal,total_kcal"
    }
}

/** Row of `public.sleep_sessions` limited to the columns the app reads. */
@Serializable
internal data class SleepSessionRowDto(
    @SerialName("external_id") val externalId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    val minutes: Int,
    val stages: List<SleepStageDto> = emptyList(),
) {
    fun toDomain() = SleepSession(
        externalId = externalId,
        startedAt = Instant.parse(startedAt),
        endedAt = Instant.parse(endedAt),
        minutes = minutes,
        stages = stages.map { SleepStage(it.stage, Instant.parse(it.startedAt), Instant.parse(it.endedAt)) },
    )

    companion object {
        const val COLUMNS = "external_id,started_at,ended_at,minutes,stages"
    }
}

/** Row of `public.resting_heart_rate` limited to the columns the app reads. */
@Serializable
internal data class RestingHeartRateRowDto(
    val day: String,
    val bpm: Int,
    @SerialName("external_id") val externalId: String? = null,
) {
    fun toDomain() = RestingHeartRate(LocalDate.parse(day), bpm, externalId)

    companion object {
        const val COLUMNS = "day,bpm,external_id"
    }
}
