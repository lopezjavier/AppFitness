package io.github.lopezjavier.appfitness.domain.model

/** Display names by exercise id (catalogue and custom). An id without an entry has no name to show. */
class ExerciseNames(val byId: Map<String, String>) {
    operator fun get(id: String): String? = byId[id]

    override fun equals(other: Any?): Boolean = other is ExerciseNames && other.byId == byId

    override fun hashCode(): Int = byId.hashCode()
}
