package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values of the `previous_values_mode` enum. */
@Serializable
internal enum class PreviousValuesModeDto(val domain: PreviousValuesMode) {
    @SerialName("overall")
    Overall(PreviousValuesMode.Overall),

    @SerialName("routine")
    Routine(PreviousValuesMode.Routine),
    ;

    companion object {
        fun of(mode: PreviousValuesMode): PreviousValuesModeDto = entries.first { it.domain == mode }
    }
}

/** Row of `public.training_preferences` as read. */
@Serializable
internal data class TrainingPreferencesDto(
    @SerialName("warm_up_included") val warmUpIncluded: Boolean = false,
    @SerialName("previous_values_mode") val previousValuesMode: PreviousValuesModeDto = PreviousValuesModeDto.Overall,
) {
    fun toDomain(): TrainingPreferences = TrainingPreferences(warmUpIncluded, previousValuesMode.domain)

    companion object {
        const val COLUMNS = "warm_up_included,previous_values_mode"
    }
}
