package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** DB values of `public.sex_type`. */
@Serializable
internal enum class SexDto(val domain: Sex) {
    @SerialName("male")
    Male(Sex.Male),

    @SerialName("female")
    Female(Sex.Female),
}

/** DB values of `public.activity_level`. */
@Serializable
internal enum class ActivityLevelDto(val domain: ActivityLevel) {
    @SerialName("sedentary")
    Sedentary(ActivityLevel.Sedentary),

    @SerialName("light")
    Light(ActivityLevel.Light),

    @SerialName("moderate")
    Moderate(ActivityLevel.Moderate),

    @SerialName("high")
    High(ActivityLevel.High),

    @SerialName("very_high")
    VeryHigh(ActivityLevel.VeryHigh),
}

private fun Sex.toDto(): SexDto = SexDto.entries.first { it.domain == this }

private fun ActivityLevel.toDto(): ActivityLevelDto = ActivityLevelDto.entries.first { it.domain == this }

/** Biometric columns of `public.profiles`, read for the onboarding prefill. `numeric` height decodes as Double. */
@Serializable
internal data class BiometricsDto(
    val sex: SexDto? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("activity_level") val activityLevel: ActivityLevelDto? = null,
) {
    fun toDomain(): SavedBiometrics = SavedBiometrics(
        sex = sex?.domain,
        birthDate = birthDate?.let(LocalDate::parse),
        heightCm = heightCm?.roundToInt(),
        activityLevel = activityLevel?.domain,
    )

    companion object {
        const val COLUMNS = "sex,birth_date,height_cm,activity_level"
    }
}

/** Body of the `PATCH /profiles` that saves the four biometric columns (the ones the column grant allows). */
@Serializable
internal data class BiometricsUpdateDto(
    val sex: SexDto,
    @SerialName("birth_date") val birthDate: String,
    @SerialName("height_cm") val heightCm: Int,
    @SerialName("activity_level") val activityLevel: ActivityLevelDto,
) {
    companion object {
        fun from(biometrics: Biometrics) = BiometricsUpdateDto(
            sex = biometrics.sex.toDto(),
            birthDate = biometrics.birthDate.toString(),
            heightCm = biometrics.heightCm,
            activityLevel = biometrics.activityLevel.toDto(),
        )
    }
}
