package io.github.lopezjavier.appfitness.gallery

import java.io.File

/** The two coverage checks of the gallery run on the real sources, registry and manifest. */
internal object GalleryChecks {
    /** Source root, relative to the module directory (`composeApp/`), where Gradle runs the host tests. */
    const val SOURCE_ROOT = "src/commonMain/kotlin/io/github/lopezjavier/appfitness"

    fun routeUniverse(): Set<String> = GalleryCoverage.appRouteNames() +
        GalleryCoverage.rootRouteNames(File("$SOURCE_ROOT/app/RootRoute.kt").readText(Charsets.UTF_8))

    fun overlayUniverse(): Set<String> {
        val base = File(SOURCE_ROOT)
        val primitives = GalleryCoverage.overlayPrimitives(
            GalleryCoverage.readSources(File(base, "designsystem")).values,
        )
        val sources = GalleryCoverage.readSources(base) {
            !(it.startsWith("feature/") || it.startsWith("app/")) || it.startsWith("feature/catalog/")
        }
        return GalleryCoverage.overlayComposables(sources, primitives).keys
    }

    fun realFailures(): List<String> {
        val routes = routeUniverse()
        val overlays = overlayUniverse()
        val covered = GalleryRegistry.all().flatMap { it.covers }.toSet()
        return GalleryCoverage.failures("route", routes, covered intersect routes, GalleryManifest.exempt) +
            GalleryCoverage.failures("overlay", overlays, covered intersect overlays, emptyMap()) +
            GalleryCoverage.unknownCovers(covered, routes, overlays)
    }

    /** The design system check on the real sources: every component is in the catalog or exempt with a reason. */
    fun designSystemFailures(): List<String> {
        val base = File(SOURCE_ROOT)
        val components = GalleryCoverage.designSystemComposables(
            GalleryCoverage.readSources(File(base, "designsystem")).values,
        )
        val catalog = GalleryCoverage.readSources(File(base, "feature/catalog")).values.joinToString("\n")
        return GalleryCoverage.designSystemGaps(components, catalog, GalleryManifest.designSystemExempt)
    }
}
