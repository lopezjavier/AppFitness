package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MyTraining
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPlace
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

/** Wire values of the `training_intention` enum. */
@Serializable
internal enum class TrainingIntentionDto(val domain: TrainingIntention) {
    @SerialName("strength")
    Strength(TrainingIntention.Strength),

    @SerialName("muscle")
    Muscle(TrainingIntention.Muscle),

    @SerialName("fat_loss")
    FatLoss(TrainingIntention.FatLoss),

    @SerialName("recomposition")
    Recomposition(TrainingIntention.Recomposition),

    @SerialName("maintenance")
    Maintenance(TrainingIntention.Maintenance),
    ;

    companion object {
        fun of(intention: TrainingIntention): TrainingIntentionDto = entries.first { it.domain == intention }
    }
}

/** Wire values of the `training_place` enum. */
@Serializable
internal enum class TrainingPlaceDto(val domain: TrainingPlace) {
    @SerialName("home")
    Home(TrainingPlace.Home),

    @SerialName("gym")
    Gym(TrainingPlace.Gym),

    @SerialName("other")
    Other(TrainingPlace.Other),
    ;

    companion object {
        fun of(place: TrainingPlace): TrainingPlaceDto = entries.first { it.domain == place }
    }
}

/**
 * Upsert body of "Mi entrenamiento": only these five columns plus the key. No defaults on purpose, so a null is
 * sent explicitly (that is how an optional field is cleared).
 */
@Serializable
internal data class MyTrainingUpsertDto(
    @SerialName("user_id") val userId: String,
    @SerialName("intention") val intention: TrainingIntentionDto?,
    @SerialName("place") val place: TrainingPlaceDto?,
    @SerialName("equipment") val equipment: List<Equipment>,
    @SerialName("days_per_week") val daysPerWeek: Int?,
    @SerialName("session_minutes") val sessionMinutes: Int?,
) {
    companion object {
        fun of(userId: String, training: MyTraining) = MyTrainingUpsertDto(
            userId = userId,
            intention = training.intention?.let(TrainingIntentionDto::of),
            place = training.place?.let(TrainingPlaceDto::of),
            equipment = training.equipment.sorted(),
            daysPerWeek = training.daysPerWeek,
            sessionMinutes = training.sessionMinutes,
        )
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
    @SerialName("intention") val intention: TrainingIntentionDto? = null,
    @SerialName("place") val place: TrainingPlaceDto? = null,
    @SerialName("equipment") val equipment: List<Equipment> = emptyList(),
    @SerialName("days_per_week") val daysPerWeek: Int? = null,
    @SerialName("session_minutes") val sessionMinutes: Int? = null,
) {
    fun toDomain(): TrainingPreferences = TrainingPreferences(
        warmUpIncluded = warmUpIncluded,
        previousValuesMode = previousValuesMode.domain,
        barWeightKg = barWeightKg,
        plateKg = plateKg,
        dumbbellHandleKg = dumbbellHandleKg,
        autoUpdateRoutine = autoUpdateRoutine,
        intention = intention?.domain,
        place = place?.domain,
        equipment = equipment.toSet(),
        daysPerWeek = daysPerWeek,
        sessionMinutes = sessionMinutes,
    )

    companion object {
        const val COLUMNS =
            "warm_up_included,previous_values_mode,bar_weight_kg,plate_kg,dumbbell_handle_kg,auto_update_routine," +
                "intention,place,equipment,days_per_week,session_minutes"
    }
}
