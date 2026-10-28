package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.DailyActivity
import io.github.lopezjavier.appfitness.domain.model.HealthActivityBatch
import io.github.lopezjavier.appfitness.domain.model.HealthActivityImportResult
import io.github.lopezjavier.appfitness.domain.model.HealthReading
import io.github.lopezjavier.appfitness.domain.model.HealthSyncState
import io.github.lopezjavier.appfitness.domain.model.RestingHeartRate
import io.github.lopezjavier.appfitness.domain.model.SleepSession
import io.github.lopezjavier.appfitness.domain.model.SleepStage
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Row of `public.health_sync_state` limited to the columns the app reads. */
@Serializable
internal data class HealthSyncStateDto(
    @SerialName("last_synced_at") val lastSyncedAt: String,
    @SerialName("changes_token") val changesToken: String? = null,
    @SerialName("last_auto_sync_date") val lastAutoSyncDate: String? = null,
) {
    fun toDomain() = HealthSyncState(Instant.parse(lastSyncedAt), changesToken, lastAutoSyncDate?.let(LocalDate::parse))

    companion object {
        const val COLUMNS = "last_synced_at,changes_token,last_auto_sync_date"
    }
}

/** One element of the `p_daily` array of `import_health_activity`. */
@Serializable
internal data class DailyActivityDto(
    val day: String,
    val steps: Int? = null,
    @SerialName("active_kcal") val activeKcal: Double? = null,
    @SerialName("total_kcal") val totalKcal: Double? = null,
) {
    companion object {
        fun from(activity: DailyActivity) = DailyActivityDto(
            day = activity.day.toString(),
            steps = activity.steps,
            activeKcal = activity.activeKcal,
            totalKcal = activity.totalKcal,
        )
    }
}

/** One stage inside the `stages` array of a [SleepSessionDto]. */
@Serializable
internal data class SleepStageDto(
    val stage: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
) {
    companion object {
        fun from(stage: SleepStage) = SleepStageDto(stage.stage, stage.startedAt.toString(), stage.endedAt.toString())
    }
}

/** One element of the `p_sleep` array of `import_health_activity`. */
@Serializable
internal data class SleepSessionDto(
    @SerialName("external_id") val externalId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    val minutes: Int,
    val stages: List<SleepStageDto>,
) {
    companion object {
        fun from(session: SleepSession) = SleepSessionDto(
            externalId = session.externalId,
            startedAt = session.startedAt.toString(),
            endedAt = session.endedAt.toString(),
            minutes = session.minutes,
            stages = session.stages.map(SleepStageDto::from),
        )
    }
}

/** One element of the `p_resting_hr` array of `import_health_activity`. */
@Serializable
internal data class RestingHeartRateDto(
    val day: String,
    val bpm: Int,
    @SerialName("external_id") val externalId: String? = null,
) {
    companion object {
        fun from(rate: RestingHeartRate) = RestingHeartRateDto(rate.day.toString(), rate.bpm, rate.externalId)
    }
}

/** Arguments of the `import_health_activity` RPC. */
@Serializable
internal data class ImportHealthActivityParams(
    @SerialName("p_daily") val daily: List<DailyActivityDto>,
    @SerialName("p_sleep") val sleep: List<SleepSessionDto>,
    @SerialName("p_resting_hr") val restingHeartRate: List<RestingHeartRateDto>,
) {
    companion object {
        fun from(batch: HealthActivityBatch) = ImportHealthActivityParams(
            daily = batch.daily.map(DailyActivityDto::from),
            sleep = batch.sleep.map(SleepSessionDto::from),
            restingHeartRate = batch.restingHeartRate.map(RestingHeartRateDto::from),
        )
    }
}

/** Result of the `import_health_activity` RPC. */
@Serializable
internal data class ImportHealthActivityResultDto(
    val daily: Int,
    val sleep: Int,
    @SerialName("resting_hr") val restingHr: Int,
) {
    fun toDomain() = HealthActivityImportResult(daily, sleep, restingHr)
}

/** Arguments of the `mark_health_auto_sync` RPC. */
@Serializable
internal data class MarkAutoSyncParams(@SerialName("p_day") val day: String)

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
