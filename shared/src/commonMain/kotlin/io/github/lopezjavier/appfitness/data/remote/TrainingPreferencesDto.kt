package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of `public.training_preferences` as read. */
@Serializable
internal data class TrainingPreferencesDto(@SerialName("warm_up_included") val warmUpIncluded: Boolean = false) {
    fun toDomain(): TrainingPreferences = TrainingPreferences(warmUpIncluded)

    companion object {
        const val COLUMNS = "warm_up_included"
    }
}
