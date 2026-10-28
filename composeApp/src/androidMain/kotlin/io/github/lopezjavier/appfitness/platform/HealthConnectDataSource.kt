package io.github.lopezjavier.appfitness.platform

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import io.github.lopezjavier.appfitness.domain.model.ChangesResult
import io.github.lopezjavier.appfitness.domain.model.DailyActivity
import io.github.lopezjavier.appfitness.domain.model.HealthAccess
import io.github.lopezjavier.appfitness.domain.model.HealthActivityBatch
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HealthMetric
import io.github.lopezjavier.appfitness.domain.model.HealthRecord
import io.github.lopezjavier.appfitness.domain.model.RawHealthRecords
import io.github.lopezjavier.appfitness.domain.model.SleepSession
import io.github.lopezjavier.appfitness.domain.model.SleepStage
import io.github.lopezjavier.appfitness.domain.model.WatchMetric
import io.github.lopezjavier.appfitness.domain.port.HealthDataSource
import io.github.lopezjavier.appfitness.domain.usecase.RestingReading
import io.github.lopezjavier.appfitness.domain.usecase.WatchDataShaping
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass
import kotlin.time.Instant
import java.time.Instant as JavaInstant

private const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
private const val BASE_APPLICATION_ID = "io.github.lopezjavier.appfitness"

private val METRIC_TYPES: Map<HealthMetric, KClass<out Record>> = mapOf(
    HealthMetric.Weight to WeightRecord::class,
    HealthMetric.BodyFat to BodyFatRecord::class,
    HealthMetric.LeanMass to LeanBodyMassRecord::class,
    HealthMetric.Water to BodyWaterMassRecord::class,
    HealthMetric.Bone to BoneMassRecord::class,
    HealthMetric.Bmr to BasalMetabolicRateRecord::class,
)

private val WATCH_TYPES: Map<WatchMetric, KClass<out Record>> = mapOf(
    WatchMetric.Sleep to SleepSessionRecord::class,
    WatchMetric.Steps to StepsRecord::class,
    WatchMetric.ActiveCalories to ActiveCaloriesBurnedRecord::class,
    WatchMetric.TotalCalories to TotalCaloriesBurnedRecord::class,
    WatchMetric.RestingHeartRate to RestingHeartRateRecord::class,
)

/**
 * The Health Connect permission strings the app asks for: read of the six body types and the five watch types, the
 * history read (first sync reaches back 90 days), write of weight and fat only.
 */
fun healthConnectPermissions(): Set<String> =
    (METRIC_TYPES.values + WATCH_TYPES.values).map { HealthPermission.getReadPermission(it) }.toSet() +
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY +
        HealthPermission.getWritePermission(WeightRecord::class) +
        HealthPermission.getWritePermission(BodyFatRecord::class)

/**
 * [HealthDataSource] on the Health Connect client. Written copies use `Metadata.manualEntry` with the client record
 * ids `<rowId>:w` and `<rowId>:f` so an edit updates and a delete removes them. Verified by hand on a phone only.
 */
@Suppress("TooManyFunctions") // One function per port method plus the record mapping.
class HealthConnectDataSource(private val context: Context) : HealthDataSource {
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override fun availability(): HealthAvailability =
        when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UpdateRequired
            else -> HealthAvailability.NotInstalled
        }

    override suspend fun access(): HealthAccess = safe {
        val granted = client.permissionController.getGrantedPermissions()
        HealthAccess(
            read = readableMetrics(granted),
            watchRead = readableWatchMetrics(granted),
            canWrite = HealthPermission.getWritePermission(WeightRecord::class) in granted,
        )
    }.getOrDefault(HealthAccess.NONE)

    override suspend fun newChangesToken(): Result<String> = safe {
        val types = readableMetrics(client.permissionController.getGrantedPermissions()).mapNotNull(METRIC_TYPES::get)
        client.getChangesToken(ChangesTokenRequest(recordTypes = types.toSet()))
    }

    override suspend fun readRange(from: Instant, to: Instant): Result<RawHealthRecords> = safe {
        val readable = readableMetrics(client.permissionController.getGrantedPermissions())
        suspend fun of(metric: HealthMetric, convert: (Record) -> Double?): List<HealthRecord> {
            val type = METRIC_TYPES.getValue(metric)
            return if (metric in readable) readAll(type, from, to, convert) else emptyList()
        }
        RawHealthRecords(
            weights = of(HealthMetric.Weight) { (it as WeightRecord).weight.inKilograms },
            fats = of(HealthMetric.BodyFat) { (it as BodyFatRecord).percentage.value },
            lean = of(HealthMetric.LeanMass) { (it as LeanBodyMassRecord).mass.inKilograms },
            water = of(HealthMetric.Water) { (it as BodyWaterMassRecord).mass.inKilograms },
            bone = of(HealthMetric.Bone) { (it as BoneMassRecord).mass.inKilograms },
            bmr = of(HealthMetric.Bmr) { (it as BasalMetabolicRateRecord).basalMetabolicRate.inKilocaloriesPerDay },
        )
    }

    /**
     * Steps and calories are aggregated per local day (Health Connect de-duplicates overlapping records of several
     * apps by its own priority); the app never writes these types, so no origin filter is needed. Sleep and resting
     * heart rate are read as records, own origin dropped. Each type is read from its own start and only when its
     * permission is granted.
     */
    override suspend fun readWatchData(
        from: Map<WatchMetric, Instant>,
        to: Instant,
        zone: TimeZone,
    ): Result<HealthActivityBatch> = safe {
        val granted = readableWatchMetrics(client.permissionController.getGrantedPermissions())
        val starts = from.filterKeys { it in granted }
        HealthActivityBatch(
            daily = dailyTotals(starts, to, zone),
            sleep = starts[WatchMetric.Sleep]?.let { readSleep(it, to) } ?: emptyList(),
            restingHeartRate = starts[WatchMetric.RestingHeartRate]?.let { readResting(it, to, zone) } ?: emptyList(),
        )
    }

    private suspend fun dailyTotals(
        starts: Map<WatchMetric, Instant>,
        to: Instant,
        zone: TimeZone,
    ): List<DailyActivity> {
        val steps = starts[WatchMetric.Steps]?.let { start ->
            aggregateDaily(StepsRecord.COUNT_TOTAL, start, to, zone) { it.toDouble() }
        } ?: emptyMap()
        val active = starts[WatchMetric.ActiveCalories]?.let { start ->
            aggregateDaily(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, start, to, zone) { it.inKilocalories }
        } ?: emptyMap()
        val total = starts[WatchMetric.TotalCalories]?.let { start ->
            aggregateDaily(TotalCaloriesBurnedRecord.ENERGY_TOTAL, start, to, zone) { it.inKilocalories }
        } ?: emptyMap()
        return (steps.keys + active.keys + total.keys).sorted().map { day ->
            DailyActivity(day, steps[day]?.toInt(), active[day], total[day])
        }
    }

    private suspend fun <T : Any> aggregateDaily(
        metric: AggregateMetric<T>,
        from: Instant,
        to: Instant,
        zone: TimeZone,
        convert: (T) -> Double,
    ): Map<LocalDate, Double> {
        val request = AggregateGroupByPeriodRequest(
            metrics = setOf(metric),
            timeRangeFilter = TimeRangeFilter.between(localStartOfDay(from, zone), localDateTime(to, zone)),
            timeRangeSlicer = Period.ofDays(1),
        )
        return client.aggregateGroupByPeriod(request).mapNotNull { group ->
            val value = group.result[metric] ?: return@mapNotNull null
            LocalDate(group.startTime.year, group.startTime.monthValue, group.startTime.dayOfMonth) to convert(value)
        }.toMap()
    }

    private fun localDateTime(instant: Instant, zone: TimeZone): LocalDateTime = instant.toLocalDateTime(zone).let {
        LocalDateTime.of(it.year, it.monthNumber, it.dayOfMonth, it.hour, it.minute, it.second)
    }

    private fun localStartOfDay(instant: Instant, zone: TimeZone): LocalDateTime =
        localDateTime(instant, zone).toLocalDate().atStartOfDay()

    private suspend fun readSleep(from: Instant, to: Instant): List<SleepSession> =
        readPaged(SleepSessionRecord::class, from, to).map { record ->
            val stages = record.stages.map {
                SleepStage(WatchDataShaping.stageKey(it.stage), it.startTime.toKotlin(), it.endTime.toKotlin())
            }
            val started = record.startTime.toKotlin()
            val ended = record.endTime.toKotlin()
            SleepSession(
                record.metadata.id,
                started,
                ended,
                WatchDataShaping.sleepMinutes(stages, started, ended),
                stages,
            )
        }

    private suspend fun readResting(from: Instant, to: Instant, zone: TimeZone) = WatchDataShaping.restingPerDay(
        readPaged(RestingHeartRateRecord::class, from, to).map {
            RestingReading(it.metadata.id, it.time.toKotlin(), it.beatsPerMinute.toInt())
        },
        zone,
    )

    private fun JavaInstant.toKotlin() = Instant.fromEpochMilliseconds(toEpochMilli())

    /**
     * Any failure of the Changes API (expired token, token of another install) falls back to the 90-day read, which
     * surfaces a real permission problem itself.
     */
    override suspend fun readChanges(token: String): Result<ChangesResult> = safe {
        try {
            collectChanges(token)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") failure: Exception) {
            ChangesResult.TokenExpired
        }
    }

    private suspend fun collectChanges(token: String): ChangesResult {
        var current = token
        val times = mutableListOf<Instant>()
        do {
            val response = client.getChanges(current)
            if (response.changesTokenExpired) return ChangesResult.TokenExpired
            times += response.changes.filterIsInstance<UpsertionChange>()
                .map { it.record }
                .filterNot(::isOwnOrigin)
                .mapNotNull(::timeOf)
            current = response.nextChangesToken
        } while (response.hasMore)
        return ChangesResult.Changed(times.minOrNull(), times.maxOrNull(), current)
    }

    override suspend fun writeWeighIn(
        rowId: String,
        weightKg: Double,
        bodyFatPct: Double?,
        measuredAt: Instant,
        version: Long,
    ): Result<Unit> = safe {
        val granted = client.permissionController.getGrantedPermissions()
        val time = JavaInstant.ofEpochMilli(measuredAt.toEpochMilliseconds())
        val offset = ZoneId.systemDefault().rules.getOffset(time)
        val records = mutableListOf<Record>(
            WeightRecord(
                time = time,
                zoneOffset = offset,
                weight = Mass.kilograms(weightKg),
                metadata = Metadata.manualEntry(weightClientId(rowId), version),
            ),
        )
        if (HealthPermission.getWritePermission(BodyFatRecord::class) in granted) {
            if (bodyFatPct != null) {
                records += BodyFatRecord(
                    time = time,
                    zoneOffset = offset,
                    percentage = Percentage(bodyFatPct),
                    metadata = Metadata.manualEntry(fatClientId(rowId), version),
                )
            } else {
                client.deleteRecords(BodyFatRecord::class, emptyList(), listOf(fatClientId(rowId)))
            }
        }
        client.insertRecords(records)
        Unit
    }

    override suspend fun deleteCopy(rowId: String): Result<Unit> = safe {
        client.deleteRecords(WeightRecord::class, emptyList(), listOf(weightClientId(rowId)))
        client.deleteRecords(BodyFatRecord::class, emptyList(), listOf(fatClientId(rowId)))
    }

    private fun weightClientId(rowId: String) = "$rowId:w"

    private fun fatClientId(rowId: String) = "$rowId:f"

    private fun readableMetrics(granted: Set<String>): Set<HealthMetric> =
        METRIC_TYPES.filter { (_, type) -> HealthPermission.getReadPermission(type) in granted }.keys

    private suspend fun readAll(
        type: KClass<out Record>,
        from: Instant,
        to: Instant,
        convert: (Record) -> Double?,
    ): List<HealthRecord> = readPaged(type, from, to).mapNotNull { record ->
        val time = timeOf(record)
        val value = convert(record)
        if (time != null && value != null) HealthRecord(record.metadata.id, time, value) else null
    }

    /** Every page of [type] records in `from..to`, own-origin records dropped. */
    private suspend fun <T : Record> readPaged(type: KClass<T>, from: Instant, to: Instant): List<T> {
        val filter = TimeRangeFilter.between(
            JavaInstant.ofEpochMilli(from.toEpochMilliseconds()),
            JavaInstant.ofEpochMilli(to.toEpochMilliseconds()),
        )
        val result = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, filter, pageToken = pageToken))
            result += response.records.filterNot(::isOwnOrigin)
            pageToken = response.pageToken
        } while (pageToken != null)
        return result
    }

    private fun readableWatchMetrics(granted: Set<String>): Set<WatchMetric> =
        WATCH_TYPES.filter { (_, type) -> HealthPermission.getReadPermission(type) in granted }.keys

    private fun isOwnOrigin(record: Record): Boolean {
        val origin = record.metadata.dataOrigin.packageName
        return origin == context.packageName || origin == BASE_APPLICATION_ID
    }

    private fun timeOf(record: Record): Instant? {
        val time = when (record) {
            is WeightRecord -> record.time
            is BodyFatRecord -> record.time
            is LeanBodyMassRecord -> record.time
            is BodyWaterMassRecord -> record.time
            is BoneMassRecord -> record.time
            is BasalMetabolicRateRecord -> record.time
            else -> return null
        }
        return Instant.fromEpochMilliseconds(time.toEpochMilli())
    }

    @Suppress("TooGenericExceptionCaught") // Every Health Connect failure becomes Result.failure (port contract).
    private suspend fun <T> safe(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        Result.failure(failure)
    }
}
