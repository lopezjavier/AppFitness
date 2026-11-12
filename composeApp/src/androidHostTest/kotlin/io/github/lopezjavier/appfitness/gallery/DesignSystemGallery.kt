package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import io.github.lopezjavier.appfitness.feature.catalog.ComponentCatalogScreen
import java.io.File

internal const val DESIGN_SYSTEM_AREA = "Design system"

/** The catalog's scrolling list; a production test tag. */
private const val CATALOG_LIST_TAG = "catalog_screen"

/** The Spanish strings of the app; the catalog section titles are read from here. */
private const val STRINGS_XML = "src/commonMain/composeResources/values/strings.xml"

/** Sections that also get a tablet image (840 dp window, the tablet type scale): buttons, cards, rows and charts. */
private val TABLET_TAGS = setOf(
    "catalog_section_buttons",
    "catalog_section_cards",
    "catalog_section_rows",
    "catalog_section_line_chart",
    "catalog_section_bar_chart",
)

/**
 * One image per catalog section (T-280). The showcase is the debug catalog itself: the section tags are read from the
 * catalog sources, so a new section appears here with no gallery edit. The screen name is the Spanish catalog title
 * from `strings.xml` (the tag, humanised, when a section has no title). Each image scrolls the catalog to its section;
 * the default image is tall so the section is not cut, and a few sections also get a tablet image.
 */
internal fun designSystemEntries(): List<GalleryEntry> {
    val catalog = File(GalleryChecks.SOURCE_ROOT, "feature/catalog")
    val tags = GalleryCoverage.catalogSectionTags(GalleryCoverage.readSources(catalog).values)
    val titles = GalleryCoverage.catalogTitles(File(STRINGS_XML).readText(Charsets.UTF_8))
    return tags.flatMap { tag ->
        val screen = titles[tag] ?: humanise(tag)
        val scroll: ComposeContentTestRule.() -> Unit = {
            onNodeWithTag(CATALOG_LIST_TAG).performScrollToNode(hasTestTag(tag))
        }
        buildList {
            add(
                GalleryEntry(
                    area = DESIGN_SYSTEM_AREA,
                    screen = screen,
                    state = "default",
                    tall = true,
                    interact = scroll,
                ) { ComponentCatalogScreen() },
            )
            if (tag in TABLET_TAGS) {
                add(
                    GalleryEntry(
                        area = DESIGN_SYSTEM_AREA,
                        screen = screen,
                        state = "tablet",
                        wide = true,
                        interact = scroll,
                    ) { ComponentCatalogScreen() },
                )
            }
        }
    }
}

/** `catalog_section_set_type` to "Set type", for a section without a Spanish title. */
private fun humanise(tag: String): String = tag.removePrefix("catalog_section_")
    .replace('_', ' ')
    .replaceFirstChar { it.uppercase() }
