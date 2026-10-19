package io.github.lopezjavier.appfitness.data.library

import io.github.lopezjavier.appfitness.domain.model.ExerciseInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Parses the generated `library.json` (`schema_version` 1) into library entries. */
object ExerciseLibraryParser {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Bundle(val exercises: Map<String, Entry> = emptyMap())

    @Serializable
    private data class Entry(
        val name: String = "",
        val description: String = "",
        val instructions: List<String> = emptyList(),
        val tips: List<String> = emptyList(),
        val poses: List<String>? = null,
    )

    /** Entries by key; entries without a name are skipped. Malformed or empty input gives an empty map. */
    // Any decoding problem must degrade to "no library", never crash.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun parse(text: String): Map<String, ExerciseInfo> = try {
        json.decodeFromString<Bundle>(text).exercises
            .filterValues { it.name.isNotBlank() }
            .mapValues { (key, e) ->
                ExerciseInfo(
                    key,
                    e.name.trim(),
                    e.description,
                    e.instructions,
                    e.tips,
                    e.poses?.contains("peak") ?: true,
                )
            }
    } catch (failure: Exception) {
        emptyMap()
    }
}
