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
        val result = GalleryCoverage.failures("route", setOf("A", "B"), setOf("A"), emptyMap())

        assertEquals(1, result.size)
        assertTrue("B" in result.single(), result.single())
    }

    @Test
    fun overlayCheck_namesTheOverlayWithNoEntry() {
        val result = GalleryCoverage.failures("overlay", setOf("FooSheet", "BarDialog"), setOf("FooSheet"), emptyMap())

        assertTrue(result.single().contains("BarDialog"), result.single())
    }

    @Test
    fun exemptNames_areNotReportedMissing() {
        val result = GalleryCoverage.failures("route", setOf("A", "B"), setOf("A"), mapOf("B" to "no UI"))

        assertTrue(result.isEmpty(), result.toString())
    }

    @Test
    fun exemptionWithBlankReason_fails() {
        val result = GalleryCoverage.failures("route", setOf("A"), emptySet(), mapOf("A" to " "))

        assertTrue(result.any { "no reason" in it }, result.toString())
    }

    @Test
    fun designSystemCheck_namesAComponentMissingFromTheCatalog() {
        val components = GalleryCoverage.designSystemComposables(
            listOf("@Composable\nfun AppShownButton() {}\n\n@Composable\nfun AppMissingBadge() {}\n"),
        )

        val result = GalleryCoverage.designSystemGaps(components, "AppShownButton()", emptyMap())

        assertEquals(setOf("AppShownButton", "AppMissingBadge"), components)
        assertTrue(result.single().contains("AppMissingBadge"), result.toString())
    }

    @Test
    fun designSystemScan_skipsPrivateFunctionsAndPreviews() {
        val components = GalleryCoverage.designSystemComposables(
            listOf(
                "@Composable\nprivate fun Hidden() {}\n@Preview\n@Composable\nfun PreviewCard() {}\nfun Plain() {}\n",
            ),
        )

        assertTrue(components.isEmpty(), components.toString())
    }

    @Test
    fun designSystemCheck_aNameOnlyMentionedWithoutACall_isNotShown() {
        val mentioned = GalleryCoverage.designSystemGaps(setOf("AppChip"), "// see AppChip for details", emptyMap())
        val called = GalleryCoverage.designSystemGaps(setOf("AppChip"), "AppChip(label = \"x\")", emptyMap())

        assertEquals(1, mentioned.size, mentioned.toString())
        assertTrue(called.isEmpty(), called.toString())
    }

    @Test
    fun designSystemExemption_thatIsShownNowOrStale_fails() {
        val result = GalleryCoverage.designSystemGaps(
            setOf("AppShown"),
            "AppShown()",
            mapOf("AppShown" to "was missing", "AppGone" to "removed"),
        )

        assertTrue(result.any { "AppShown" in it && "shown" in it }, result.toString())
        assertTrue(result.any { "AppGone" in it }, result.toString())
    }

    @Test
    fun catalogSectionTags_areReadFromTheSectionLiterals() {
        val tags = GalleryCoverage.catalogSectionTags(
            listOf("CatalogSection(stringResource(x), \"catalog_section_cards\") {}\n\"catalog_section_chips\""),
        )

        assertEquals(listOf("catalog_section_cards", "catalog_section_chips"), tags)
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
    fun realDesignSystem_everyComponentIsInTheCatalogOrExempt() {
        val failures = GalleryChecks.designSystemFailures()

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun showcase_hasOneEntryPerCatalogSection() {
        val sections = GalleryCoverage.catalogSectionTags(
            GalleryCoverage.readSources(File(GalleryChecks.SOURCE_ROOT, "feature/catalog")).values,
        )

        val entries = designSystemEntries()
        assertTrue(sections.isNotEmpty())
        assertEquals(sections.size, entries.count { it.tall })
        assertTrue(entries.all { it.area == DESIGN_SYSTEM_AREA })
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
