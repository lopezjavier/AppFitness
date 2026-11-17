package io.github.lopezjavier.appfitness.domain.model

/**
 * Display names by exercise id (catalogue and custom). An id without an entry has no name to show.
 * [imageKeyById] holds the bundled-picture key (the library `key`) of the catalogue rows only; a custom row has none.
 */
class ExerciseNames(val byId: Map<String, String>, val imageKeyById: Map<String, String> = emptyMap()) {
    operator fun get(id: String): String? = byId[id]

    /** The bundled-picture key of a catalogue exercise, or null (custom row, or no name to show). */
    fun imageKeyOf(id: String): String? = imageKeyById[id]

    override fun equals(other: Any?): Boolean =
        other is ExerciseNames && other.byId == byId && other.imageKeyById == imageKeyById

    override fun hashCode(): Int = 31 * byId.hashCode() + imageKeyById.hashCode()
}
