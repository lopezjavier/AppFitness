package io.github.lopezjavier.appfitness.domain.port

import io.github.lopezjavier.appfitness.domain.model.ExerciseInfo

/** The client-side exercise library (names, instructions) shipped inside the APK. */
interface ExerciseLibrary {
    /** Entries by catalog key. Empty when the bundle is absent or malformed; never throws. */
    suspend fun load(): Map<String, ExerciseInfo>
}
