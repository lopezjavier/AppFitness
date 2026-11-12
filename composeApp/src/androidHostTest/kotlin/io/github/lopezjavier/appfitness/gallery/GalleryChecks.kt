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
        val exempt = GalleryManifest.exempt
        val routePending = GalleryManifest.pending.filterKeys { it in routes }
        val overlayPending = GalleryManifest.pending.filterKeys { it !in routes }
        return GalleryCoverage.failures("route", routes, covered intersect routes, exempt, routePending) +
            GalleryCoverage.failures(
                "overlay",
                overlays,
                covered intersect overlays,
                emptyMap(),
                overlayPending,
            ) +
            GalleryCoverage.unknownCovers(covered, routes, overlays)
    }
}
