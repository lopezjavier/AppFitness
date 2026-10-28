package io.github.lopezjavier.appfitness.domain.port

import io.github.lopezjavier.appfitness.domain.model.RestSound
import kotlinx.coroutines.flow.Flow

/** Local settings of the end-of-rest alert. */
interface RestAlertPreferences {
    /** The chosen sound ([RestSound.Default] until one is chosen). */
    val sound: Flow<RestSound>

    suspend fun setSound(sound: RestSound)

    /** Whether the one-time "enable notifications" explanation was already shown. */
    suspend fun explanationShown(): Boolean

    suspend fun markExplanationShown()
}
