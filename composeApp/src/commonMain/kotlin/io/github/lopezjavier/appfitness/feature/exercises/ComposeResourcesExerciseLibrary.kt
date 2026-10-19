package io.github.lopezjavier.appfitness.feature.exercises

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.data.library.ExerciseLibraryParser
import io.github.lopezjavier.appfitness.domain.model.ExerciseInfo
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The RepDB library bundled as a Compose resource (`files/exercises/library.json`, generated and gitignored). Read
 * once off the main thread and cached; a missing or malformed bundle gives an empty library.
 */
class ComposeResourcesExerciseLibrary : ExerciseLibrary {
    private var cached: Map<String, ExerciseInfo>? = null

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // A missing bundle is expected (CI, fresh clone).
    override suspend fun load(): Map<String, ExerciseInfo> = cached ?: withContext(Dispatchers.Default) {
        try {
            ExerciseLibraryParser.parse(Res.readBytes("files/exercises/library.json").decodeToString())
        } catch (failure: Exception) {
            emptyMap()
        }
    }.also { cached = it }
}
