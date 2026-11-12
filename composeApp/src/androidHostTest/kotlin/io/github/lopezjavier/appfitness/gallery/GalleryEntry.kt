package io.github.lopezjavier.appfitness.gallery

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Index areas, in the order the index lists them. */
internal val GALLERY_AREAS = listOf("Inicio", "Diario", "Entreno", "Trainer", "Perfil", "Compartido")

/** What an entry shows. */
internal enum class GalleryKind { Screen, Sheet, Dialog }

/**
 * One image of the screen gallery (T-279): [state] of [screen] in [area].
 *
 * [covers] holds route simple names (`DiaryHome`, `RootRoute.Loading`) and overlay composable names
 * (`EditEntrySheet`) that this image proves are rendered. [alsoOpenedFrom] lists other areas that open the screen.
 * [wide] renders it on a 840 dp wide window instead of 360 dp; [fontScale] scales the text; [interact] runs
 * after the screen settled (opens a sheet the screen keeps in its own state, through the production tags).
 */
internal class GalleryEntry(
    val area: String,
    val screen: String,
    val state: String,
    val kind: GalleryKind = GalleryKind.Screen,
    val covers: Set<String> = emptySet(),
    val alsoOpenedFrom: List<String> = emptyList(),
    val wide: Boolean = false,
    val fontScale: Float = 1f,
    val interact: (ComposeContentTestRule.() -> Unit)? = null,
    val content: @Composable () -> Unit,
) {
    /** Unique key and relative file stem of the image. */
    val key: String get() = "${slug(area)}/${slug(screen)}/${slug(state)}"

    private fun slug(text: String) = text.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "x" }
}
