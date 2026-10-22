package io.github.lopezjavier.appfitness.domain.model

import kotlin.time.Instant

/** The body metrics the app reads from Health Connect. */
enum class HealthMetric { Weight, BodyFat, LeanMass, Water, Bone, Bmr }

/** Whether Health Connect can be used on this device. */
enum class HealthAvailability { Available, NotInstalled, UpdateRequired }

/** What the user granted: the metrics that can be read and whether the app may write weight and fat. */
data class HealthAccess(val read: Set<HealthMetric>, val canWrite: Boolean) {
    companion object {
        val NONE = HealthAccess(emptySet(), canWrite = false)
    }
}

/** One raw Health Connect record already converted to the app's unit (kg, %, kcal/day). */
data class HealthRecord(val id: String, val time: Instant, val value: Double)

/** The records of the six types read from Health Connect, own-origin records already removed. */
data class RawHealthRecords(
    val weights: List<HealthRecord> = emptyList(),
    val fats: List<HealthRecord> = emptyList(),
    val lean: List<HealthRecord> = emptyList(),
    val water: List<HealthRecord> = emptyList(),
    val bone: List<HealthRecord> = emptyList(),
    val bmr: List<HealthRecord> = emptyList(),
)

/** One weigh-in: a weight record with the fat and composition records of the same moment attached. */
data class HealthReading(
    val externalId: String,
    val measuredAt: Instant,
    val weightKg: Double,
    val bodyFatPct: Double? = null,
    val leanMassKg: Double? = null,
    val waterKg: Double? = null,
    val boneKg: Double? = null,
    val bmrKcal: Int? = null,
)

/** The user's stored sync position: when the last sync ran and the Health Connect changes token to continue from. */
data class HealthSyncState(val lastSyncedAt: Instant, val changesToken: String?)

/** Answer of `readChanges`: the time range the changed records touch (null when none), or an expired token. */
sealed interface ChangesResult {
    data class Changed(val touchedFrom: Instant?, val touchedTo: Instant?, val nextToken: String) : ChangesResult

    data object TokenExpired : ChangesResult
}

/** Result of one manual sync. */
sealed interface SyncOutcome {
    data class Synced(val imported: Int, val syncedAt: Instant) : SyncOutcome

    /** Read access to the weight is missing: the UI must ask for it. */
    data object PermissionNeeded : SyncOutcome

    data class Unavailable(val availability: HealthAvailability) : SyncOutcome
}
