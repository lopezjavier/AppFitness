package io.github.lopezjavier.appfitness.platform

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.lopezjavier.appfitness.domain.model.HistoryProbeResult
import io.github.lopezjavier.appfitness.domain.model.HistoryWindows
import io.github.lopezjavier.appfitness.domain.model.WindowStats
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.HealthHistoryProbe
import kotlinx.coroutines.CancellationException
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import java.time.Instant as JavaInstant

private const val RECENT_DAYS = 30
private const val OLD_FROM_DAYS = 90
private const val OLD_TO_DAYS = 60

/** The permissions the T-139 diagnostic asks for: steps, weight and the history read. */
fun healthHistoryPermissions(): Set<String> = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(WeightRecord::class),
    HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
)

/**
 * [HealthHistoryProbe] on the Health Connect client (T-139, temporary). Raw counts, no origin filter. A window whose
 * read throws counts as empty; the probe fails only when the client fails or every attempted read failed.
 */
class HealthConnectHistoryProbe(private val context: Context, private val clock: Clock) : HealthHistoryProbe {
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun probe(): Result<HistoryProbeResult> = safe {
        val featureAvailable =
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        val granted = client.permissionController.getGrantedPermissions()
        val now = clock.now()
        val outcomes = listOf(StepsRecord::class, WeightRecord::class).map { type ->
            if (HealthPermission.getReadPermission(type) !in granted) {
                null
            } else {
                Windows(
                    recent = window(type, now - RECENT_DAYS.days, now),
                    old = window(type, now - OLD_FROM_DAYS.days, now - OLD_TO_DAYS.days),
                )
            }
        }
        val attempted = outcomes.filterNotNull().flatMap { listOf(it.recent, it.old) }
        if (attempted.isNotEmpty() && attempted.all { it == null }) error("Every Health Connect read failed")
        val (steps, weight) = outcomes.map { it.toWindows() }
        HistoryProbeResult(
            featureAvailable = featureAvailable,
            historyGranted = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted,
            steps = steps,
            weight = weight,
        )
    }

    private class Windows(val recent: WindowStats?, val old: WindowStats?)

    private fun Windows?.toWindows() = HistoryWindows(
        recent = this?.recent ?: WindowStats.EMPTY,
        old = this?.old ?: WindowStats.EMPTY,
    )

    /** Null when the read failed (for example a missing history permission on the older window). */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // A failed window is reported as not read.
    private suspend fun window(type: KClass<out Record>, from: Instant, to: Instant): WindowStats? = try {
        readStats(type, from, to)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        null
    }

    private suspend fun readStats(type: KClass<out Record>, from: Instant, to: Instant): WindowStats {
        val filter = TimeRangeFilter.between(
            JavaInstant.ofEpochMilli(from.toEpochMilliseconds()),
            JavaInstant.ofEpochMilli(to.toEpochMilliseconds()),
        )
        var count = 0
        var oldest: JavaInstant? = null
        var pageToken: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, filter, pageToken = pageToken))
            count += response.records.size
            val pageOldest = response.records.mapNotNull(::startOf).minOrNull()
            if (pageOldest != null && (oldest == null || pageOldest < oldest)) oldest = pageOldest
            pageToken = response.pageToken
        } while (pageToken != null)
        return WindowStats(count, oldest?.let { Instant.fromEpochMilliseconds(it.toEpochMilli()) })
    }

    private fun startOf(record: Record): JavaInstant? = when (record) {
        is StepsRecord -> record.startTime
        is WeightRecord -> record.time
        else -> null
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
