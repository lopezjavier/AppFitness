package io.github.lopezjavier.appfitness.gallery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** `python scripts/gallery.py <Area>` renders only the entries of that area (`-Pgallery.area`). */
class GalleryAreaFilterTest {
    private val inicio = GalleryEntry(area = "Inicio", screen = "A", state = "default") {}
    private val perfil = GalleryEntry(area = "Perfil", screen = "B", state = "default") {}
    private val perfil2 = GalleryEntry(area = "Perfil", screen = "C", state = "empty") {}
    private val all = listOf(inicio, perfil, perfil2)

    @Test
    fun noArea_keepsEveryEntry() {
        assertSame(all, selectGalleryEntries(all, null))
    }

    @Test
    fun area_keepsOnlyThatAreasEntries() {
        assertEquals(listOf(perfil, perfil2), selectGalleryEntries(all, "Perfil"))
    }

    @Test
    fun areaWithNoEntries_isEmpty() {
        assertTrue(selectGalleryEntries(all, "Trainer").isEmpty())
    }
}
