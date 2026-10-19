package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.lopezjavier.appfitness.composeapp.resources.Res

/** Where the images of a catalog exercise live. A null URI means "no image": the UI shows its placeholder. */
interface ExerciseMediaUris {
    fun start(key: String): String?

    fun peak(key: String): String?
}

/** The bundled RepDB images (`files/exercises/images/<key>-start.webp`). A missing file gives null, never a crash. */
internal object ResourceExerciseMediaUris : ExerciseMediaUris {
    private const val DIR = "files/exercises/images"

    override fun start(key: String): String? = uri("$DIR/$key-start.webp")

    override fun peak(key: String): String? = uri("$DIR/$key-peak.webp")

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // The bundle is optional (CI, fresh clone).
    private fun uri(path: String): String? = try {
        Res.getUri(path)
    } catch (failure: Exception) {
        null
    }
}

/** Image locator of the exercise screens; tests provide a fake that needs no bundled files. */
val LocalExerciseMediaUris = staticCompositionLocalOf<ExerciseMediaUris> { ResourceExerciseMediaUris }
