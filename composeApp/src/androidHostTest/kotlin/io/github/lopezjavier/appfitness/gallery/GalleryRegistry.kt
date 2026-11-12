package io.github.lopezjavier.appfitness.gallery

/** Every entry of the gallery, in index order. */
internal object GalleryRegistry {
    fun all(): List<GalleryEntry> = inicioEntries() + diarioEntries() + perfilEntries()
}
