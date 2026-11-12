package io.github.lopezjavier.appfitness.gallery

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** US-131 "No screen is missing": the two coverage checks, on synthetic data and on the real sources. */
@Suppress("TooManyFunctions") // One method per scenario.
class GalleryCoverageTest {
    @Test
    fun routeCheck_namesTheRouteWithNoEntry() {
        val result = GalleryCoverage.failures("route", setOf("A", "B"), setOf("A"), emptyMap(), emptyMap())

        assertEquals(1, result.size)
        assertTrue("B" in result.single(), result.single())
    }

    @Test
    fun overlayCheck_namesTheOverlayWithNoEntry() {
        val result = GalleryCoverage.failures(
            "overlay",
            setOf("FooSheet", "BarDialog"),
            setOf("FooSheet"),
            emptyMap(),
            emptyMap(),
        )

        assertTrue(result.single().contains("BarDialog"), result.single())
    }

    @Test
    fun exemptAndPendingNames_areNotReportedMissing() {
        val result = GalleryCoverage.failures(
            "route",
            setOf("A", "B", "C"),
            setOf("A"),
            mapOf("B" to "no UI"),
            mapOf("C" to "T-280"),
        )

        assertTrue(result.isEmpty(), result.toString())
    }

    @Test
    fun pendingNameThatNowHasAnEntry_fails() {
        val result = GalleryCoverage.failures("route", setOf("A"), setOf("A"), emptyMap(), mapOf("A" to "T-280"))

        assertTrue(result.single().contains("A"), result.single())
    }

    @Test
    fun exemptionWithBlankReason_fails() {
        val result = GalleryCoverage.failures("route", setOf("A"), emptySet(), mapOf("A" to " "), emptyMap())

        assertTrue(result.any { "no reason" in it }, result.toString())
    }

    @Test
    fun coveredNameOutsideTheUniverse_isATypo() {
        val result = GalleryCoverage.unknownCovers(
            setOf("DiaryHome", "DiaryHomee"),
            setOf("DiaryHome"),
            setOf("FooSheet"),
        )

        assertTrue(result.single().contains("DiaryHomee"), result.single())
    }

    @Test
    fun overlayScan_findsAFunctionThatCallsAPrimitive() {
        val sources = mapOf(
            "feature/a/A.kt" to
                "@Composable\nfun FooSheet(x: Int) {\n    AppEditSheet(x)\n}\n\nfun Plain() {\n    Text()\n}\n",
        )

        val result = GalleryCoverage.overlayComposables(sources, setOf("AppEditSheet"))

        assertEquals(setOf("FooSheet"), result.keys)
    }

    @Test
    fun overlayPrimitives_areDerivedFromDesignsystemDeclarations() {
        val result = GalleryCoverage.overlayPrimitives(
            listOf("fun NewThingSheet(a: Int) {}\ninternal fun OtherDialog() {}"),
        )

        assertTrue(result.containsAll(setOf("NewThingSheet", "OtherDialog", "ModalBottomSheet")))
    }

    @Test
    fun realSourcesAndRegistry_haveNoGap() {
        val failures = GalleryChecks.realFailures()

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun realRouteUniverse_isTheExpectedSet() {
        val routes = GalleryChecks.routeUniverse()

        assertTrue(routes.containsAll(setOf("DiaryHome", "ProfileHome", "RootRoute.Loading", "RootRoute.ProfileError")))
        assertTrue(File(GalleryChecks.SOURCE_ROOT).isDirectory)
    }

    @Test
    fun everyEntry_hasAUniqueKeyAndAKnownArea() {
        val entries = GalleryRegistry.all()

        assertEquals(entries.size, entries.map { it.key }.toSet().size, "duplicate gallery key")
        assertTrue(entries.all { it.area in GALLERY_AREAS })
    }
}
