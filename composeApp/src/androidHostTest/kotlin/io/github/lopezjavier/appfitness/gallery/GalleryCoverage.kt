package io.github.lopezjavier.appfitness.gallery

import io.github.lopezjavier.appfitness.app.AppRoute
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementDescriptors
import java.io.File

/** Pure coverage logic of the screen gallery (T-279): what must have an entry, and what is missing. */
@OptIn(ExperimentalSerializationApi::class)
@Suppress("TooManyFunctions") // Pure checks: one function per rule of the gallery.
internal object GalleryCoverage {
    private val PRIMITIVE_DECLARATION =
        Regex("""(?m)^(?:(?:internal|private|public)\s+)?fun\s+(\w*(?:Sheet|Dialog))\s*\(""")
    private val FUNCTION_DECLARATION =
        Regex("""(?m)^(?:(?:internal|private|public|inline)\s+)*fun\s+(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?(\w+)\s*\(""")
    private val CATALOG_SECTION_TAG = Regex("\"(catalog_section_[a-z0-9_]+)\"")
    private val CATALOG_TITLE = Regex("<string name=\"(catalog_section_[a-z0-9_]+)\">([^<]*)</string>")
    private val TOP_LEVEL_FUN = Regex("""^(?:internal\s+|public\s+)?fun\s+([A-Z]\w*)\s*\(""")
    private val ROOT_ROUTE_DECLARATION =
        Regex("""(?m)^\s*(?:data object|object|data class|class)\s+(\w+)[^\n]*:\s*RootRoute""")

    /** Overlay primitives that are not declared in `designsystem/` (Material and platform ones). */
    private val EXTERNAL_PRIMITIVES = setOf("ModalBottomSheet", "AlertDialog", "Dialog")

    /** Simple names of every concrete [AppRoute], read from the compiler-generated polymorphic serializer. */
    fun appRouteNames(): Set<String> = AppRoute.serializer().descriptor.elementDescriptors
        .flatMap { it.elementDescriptors }
        .filter { it.kind.toString() == "OBJECT" || it.kind.toString() == "CLASS" }
        .map { it.serialName.substringAfterLast('.') }
        .toSet()

    /** `RootRoute.X` for every declaration of `RootRoute` in `app/RootRoute.kt` (the interface is not serializable). */
    fun rootRouteNames(rootRouteSource: String): Set<String> =
        ROOT_ROUTE_DECLARATION.findAll(rootRouteSource).map { "RootRoute.${it.groupValues[1]}" }.toSet()

    /** Overlay primitives derived from the `designsystem/` sources plus the Material and platform ones. */
    fun overlayPrimitives(designsystemSources: Collection<String>): Set<String> =
        designsystemSources.flatMap { text -> PRIMITIVE_DECLARATION.findAll(text).map { it.groupValues[1] } }.toSet() +
            EXTERNAL_PRIMITIVES

    /**
     * Name -> file of every top-level function of [sources] (file name to text) whose body calls one of [primitives].
     * A function never counts for calling itself.
     */
    fun overlayComposables(sources: Map<String, String>, primitives: Set<String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for ((file, text) in sources) {
            val declarations = FUNCTION_DECLARATION.findAll(text).toList()
            declarations.forEachIndexed { index, declaration ->
                val name = declaration.groupValues[1]
                val end = declarations.getOrNull(index + 1)?.range?.first ?: text.length
                val body = text.substring(declaration.range.last, end)
                val callsPrimitive = primitives.any { it != name && Regex("""\b$it\s*\(""").containsMatchIn(body) }
                if (callsPrimitive) result.putIfAbsent(name, file)
            }
        }
        return result
    }

    /**
     * Failure messages for one kind of unit ([kind] is "route" or "overlay"): [universe] minus [covered] and [exempt]
     * must be empty; every exemption needs a reason.
     */
    fun failures(kind: String, universe: Set<String>, covered: Set<String>, exempt: Map<String, String>): List<String> =
        buildList {
            (universe - covered - exempt.keys).sorted().forEach {
                add("Gallery: $kind $it has no entry (add one in a *Gallery.kt or list it in GalleryManifest.exempt)")
            }
            exempt.filterValues { it.isBlank() }.keys.sorted().forEach {
                add("Gallery: exemption $it has no reason (write why it is not in the gallery)")
            }
        }

    /** Every `catalog_section_*` tag passed to `CatalogSection(...)` in the catalog sources, sorted and unique. */
    fun catalogSectionTags(sources: Collection<String>): List<String> = sources
        .flatMap { text -> CATALOG_SECTION_TAG.findAll(text).map { it.groupValues[1] } }
        .distinct()
        .sorted()

    /** The Spanish titles of the catalog sections: `catalog_section_x` to its string of the resources `strings.xml`. */
    fun catalogTitles(stringsXml: String): Map<String, String> =
        CATALOG_TITLE.findAll(stringsXml).associate { it.groupValues[1] to it.groupValues[2] }

    /**
     * Names of the public or internal top-level `@Composable` functions (not `private`, not `@Preview`) of the
     * design system [sources]; the annotations directly above a declaration count as its own.
     */
    fun designSystemComposables(sources: Collection<String>): Set<String> = buildSet {
        for (text in sources) {
            val lines = text.lines()
            lines.forEachIndexed { index, line ->
                val name = TOP_LEVEL_FUN.find(line)?.groupValues?.get(1) ?: return@forEachIndexed
                val annotations = mutableListOf(line)
                var above = index - 1
                while (above >= 0 && lines[above].trimStart().startsWith("@")) {
                    annotations += lines[above]
                    above--
                }
                if (annotations.any { "@Composable" in it } && annotations.none { "@Preview" in it }) add(name)
            }
        }
    }

    /**
     * Failure messages of the design system check: every [components] name must appear in the catalog text
     * ([catalogText]) or be in [exempt] with a reason. A stale exemption (shown now, or no longer a component) fails.
     */
    fun designSystemGaps(components: Set<String>, catalogText: String, exempt: Map<String, String>): List<String> =
        buildList {
            val shown = components.filter { Regex("\\b$it\\(").containsMatchIn(catalogText) }.toSet()
            (components - shown - exempt.keys).sorted().forEach {
                add(
                    "Design system: $it is not in the component catalog " +
                        "(add a section in feature/catalog or list it in GalleryManifest.designSystemExempt)",
                )
            }
            (exempt.keys intersect shown).sorted().forEach {
                add("Design system: exemption $it is shown in the catalog now (remove it from designSystemExempt)")
            }
            (exempt.keys - components).sorted().forEach {
                add(
                    "Design system: exemption $it is not a design system composable " +
                        "(remove it from designSystemExempt)",
                )
            }
            exempt.filterValues { it.isBlank() }.keys.sorted().forEach {
                add("Design system: exemption $it has no reason (write why it is not in the catalog)")
            }
        }

    /** Names an entry claims to cover that are neither a route nor a scanned overlay: a typo. */
    fun unknownCovers(covers: Set<String>, routes: Set<String>, overlays: Set<String>): List<String> =
        (covers - routes - overlays).sorted().map {
            "Gallery: an entry covers $it, which is neither a route nor an overlay composable (typo?)"
        }

    /** Reads every `.kt` file below [dir] as relative path to text. */
    fun readSources(dir: File, skip: (String) -> Boolean = { false }): Map<String, String> = dir.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { it.relativeTo(dir).path.replace('\\', '/') to it }
        .filterNot { (relative, _) -> skip(relative) }
        .associate { (relative, file) -> relative to file.readText(Charsets.UTF_8) }
}
