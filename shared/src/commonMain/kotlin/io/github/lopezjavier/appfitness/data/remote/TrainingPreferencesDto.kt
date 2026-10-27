package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
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
    @SerialName("bar_weight_kg") val barWeightKg: Double = CalculatorSettings.DEFAULT_BAR_KG,
    @SerialName("plate_kg") val plateKg: List<Double> = CalculatorSettings.PLATE_CATALOGUE,
    @SerialName("dumbbell_handle_kg") val dumbbellHandleKg: Double = CalculatorSettings.DEFAULT_DUMBBELL_HANDLE_KG,
    @SerialName("auto_update_routine") val autoUpdateRoutine: Boolean = false,
) {
    fun toDomain(): TrainingPreferences = TrainingPreferences(
        warmUpIncluded = warmUpIncluded,
        previousValuesMode = previousValuesMode.domain,
        barWeightKg = barWeightKg,
        plateKg = plateKg,
        dumbbellHandleKg = dumbbellHandleKg,
        autoUpdateRoutine = autoUpdateRoutine,
    )

    companion object {
        const val COLUMNS =
            "warm_up_included,previous_values_mode,bar_weight_kg,plate_kg,dumbbell_handle_kg,auto_update_routine"
    }
}
