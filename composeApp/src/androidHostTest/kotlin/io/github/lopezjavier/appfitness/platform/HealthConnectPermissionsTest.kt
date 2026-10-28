package io.github.lopezjavier.appfitness.platform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PREFIX = "android.permission.health."
private val MANIFEST = listOf(
    File("../androidApp/src/main/AndroidManifest.xml"),
    File("androidApp/src/main/AndroidManifest.xml"),
).first { it.exists() }

class HealthConnectPermissionsTest {
    private val requested = healthConnectPermissions()

    @Test
    fun requestsTheElevenReadPermissionsAndTheHistoryPermission() {
        val expectedReads = listOf(
            "READ_WEIGHT", "READ_BODY_FAT", "READ_LEAN_BODY_MASS", "READ_BODY_WATER_MASS", "READ_BONE_MASS",
            "READ_BASAL_METABOLIC_RATE", "READ_SLEEP", "READ_STEPS", "READ_ACTIVE_CALORIES_BURNED",
            "READ_TOTAL_CALORIES_BURNED", "READ_RESTING_HEART_RATE",
        ).map { PREFIX + it }.toSet()

        val reads = requested.filter { it.startsWith(PREFIX + "READ_") }.toSet()

        assertEquals(expectedReads + (PREFIX + "READ_HEALTH_DATA_HISTORY"), reads)
    }

    @Test
    fun writesOnlyWeightAndBodyFat() {
        val writes = requested.filter { it.startsWith(PREFIX + "WRITE_") }.toSet()

        assertEquals(setOf(PREFIX + "WRITE_WEIGHT", PREFIX + "WRITE_BODY_FAT"), writes)
    }

    @Test
    fun manifestDeclaresEveryRequestedPermission_andNoOtherWrite() {
        val declared = Regex("""android:name="(android\.permission\.health\.[A-Z_]+)"""")
            .findAll(MANIFEST.readText())
            .map { it.groupValues[1] }
            .toSet()

        assertTrue(declared.containsAll(requested), "missing in manifest: ${requested - declared}")
        assertEquals(
            setOf(PREFIX + "WRITE_WEIGHT", PREFIX + "WRITE_BODY_FAT"),
            declared.filter { it.startsWith(PREFIX + "WRITE_") }.toSet(),
        )
    }
}
