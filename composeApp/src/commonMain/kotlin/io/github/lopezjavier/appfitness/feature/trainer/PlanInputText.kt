@file:Suppress("TooManyFunctions") // One small formatter per key group.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.activity_high_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_light_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_moderate_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_sedentary_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_very_high_title
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_line
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_line_height_weight
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_line_sex_age
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_unset
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_unset_optional
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_value_date
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_days_count
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_intention_fat_loss
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_intention_maintenance
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_intention_muscle
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_intention_recomposition
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_intention_strength
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_place_gym
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_place_home
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_place_other
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_sex_female
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_sex_male
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_age
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_cardio_none
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_height
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_label_age
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_label_cardio
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_label_days
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_label_diary
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_label_equipment
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_label_experience
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_label_height
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_label_intention
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_label_place
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_label_session
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_label_sex
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_label_unknown
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_label_weight
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_no
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_trend
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_weight
import io.github.lopezjavier.appfitness.composeapp.resources.plan_input_yes
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_experience_advanced
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_experience_beginner
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_experience_intermediate
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_kcal_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_row_activity_level
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_row_total_expenditure
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_row_weight_trend
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_section_estimate
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_section_my_training
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_section_profile
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_section_weighing
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_source_other
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.durationText
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputSource
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.feature.exercises.label
import io.github.lopezjavier.appfitness.feature.weight.decimalText
import io.github.lopezjavier.appfitness.feature.weight.signedTenths
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val SECONDS_PER_MINUTE = 60

/** Spanish label of an input ("Objetivo", "Nivel de actividad"). */
@Suppress("CyclomaticComplexMethod") // One branch per key.
internal fun PlanInputKey.labelResource(): StringResource = when (this) {
    PlanInputKey.Intention -> Res.string.plan_input_label_intention

    PlanInputKey.Sex -> Res.string.plan_input_label_sex

    PlanInputKey.Age -> Res.string.plan_input_label_age

    PlanInputKey.Height -> Res.string.plan_input_label_height

    PlanInputKey.Weight -> Res.string.plan_input_label_weight

    PlanInputKey.ActivityLevel -> Res.string.trainer_row_activity_level

    PlanInputKey.TotalExpenditure -> Res.string.trainer_row_total_expenditure

    PlanInputKey.WeightTrend -> Res.string.trainer_row_weight_trend

    PlanInputKey.Diary -> Res.string.plan_input_label_diary

    PlanInputKey.Place -> Res.string.plan_input_label_place

    PlanInputKey.Equipment -> Res.string.plan_input_label_equipment

    PlanInputKey.Cardio -> Res.string.plan_input_label_cardio

    PlanInputKey.DaysPerWeek -> Res.string.plan_input_label_days

    PlanInputKey.SessionMinutes -> Res.string.plan_input_label_session

    PlanInputKey.Experience -> Res.string.plan_input_label_experience

    PlanInputKey.TrainingYears, PlanInputKey.KnownMaintenance, PlanInputKey.FreeText,
    PlanInputKey.Unknown,
    -> Res.string.plan_input_label_unknown
}

internal fun PlanInputSource.originLabel(): StringResource = when (this) {
    PlanInputSource.Profile -> Res.string.your_data_section_profile
    PlanInputSource.MyTraining -> Res.string.your_data_section_my_training
    PlanInputSource.WeighIn -> Res.string.your_data_section_weighing
    PlanInputSource.CoachEstimate -> Res.string.your_data_section_estimate
    PlanInputSource.Intake, PlanInputSource.Unknown -> Res.string.your_data_source_other
}

internal fun PlanInputOrigin.title(): StringResource = when (this) {
    PlanInputOrigin.Profile -> Res.string.your_data_section_profile
    PlanInputOrigin.MyTraining -> Res.string.your_data_section_my_training
    PlanInputOrigin.Weighing -> Res.string.your_data_section_weighing
    PlanInputOrigin.Estimate -> Res.string.your_data_section_estimate
}

private fun normalized(text: String): String = text.filter { it.isLetter() }.lowercase()

private fun intentionResource(text: String): StringResource? = when (normalized(text)) {
    "strength" -> Res.string.my_training_intention_strength
    "muscle" -> Res.string.my_training_intention_muscle
    "fatloss" -> Res.string.my_training_intention_fat_loss
    "recomposition" -> Res.string.my_training_intention_recomposition
    "maintenance" -> Res.string.my_training_intention_maintenance
    else -> null
}

private fun placeResource(text: String): StringResource? = when (normalized(text)) {
    "home" -> Res.string.my_training_place_home
    "gym" -> Res.string.my_training_place_gym
    "other" -> Res.string.my_training_place_other
    else -> null
}

private fun activityResource(text: String): StringResource? = when (normalized(text)) {
    "sedentary" -> Res.string.activity_sedentary_title
    "light" -> Res.string.activity_light_title
    "moderate" -> Res.string.activity_moderate_title
    "high" -> Res.string.activity_high_title
    "veryhigh" -> Res.string.activity_very_high_title
    else -> null
}

private fun experienceResource(text: String): StringResource? = when (normalized(text)) {
    "beginner" -> Res.string.trainer_experience_beginner
    "intermediate" -> Res.string.trainer_experience_intermediate
    "advanced" -> Res.string.trainer_experience_advanced
    else -> null
}

private fun sexResource(text: String): StringResource? = when (normalized(text)) {
    "male" -> Res.string.onboarding_sex_male
    "female" -> Res.string.onboarding_sex_female
    else -> null
}

private fun equipmentResource(text: String): StringResource? =
    Equipment.entries.firstOrNull { normalized(it.name) == normalized(text) }?.label

@Composable
private fun known(text: String, mapper: (String) -> StringResource?): String =
    mapper(text)?.let { stringResource(it) } ?: text

/** The label of the row ("Falta: Objetivo" is built by the screen from this). */
@Composable
internal fun PlanInputRow.labelText(): String = stringResource(key.labelResource())

/** The value text of the row, without the "Sin definir" fallback (null when there is nothing to show). */
@Composable
private fun PlanInputRow.rawValueText(): String? {
    val v = value
    return when {
        // No cardio machine is a valid answer: the coach proposes walking (US-80).
        isEmpty && key == PlanInputKey.Cardio -> stringResource(Res.string.plan_input_cardio_none)

        isEmpty -> null

        v is PlanInputValue.Text -> textValue(v.text)

        v is PlanInputValue.Number -> numberValue(v.number)

        v is PlanInputValue.Date -> AppDateFormat.title(v.date)

        v is PlanInputValue.Flag -> stringResource(if (v.flag) Res.string.plan_input_yes else Res.string.plan_input_no)

        v is PlanInputValue.Items -> v.items.map { known(it, ::equipmentResource) }.joinToString(", ")

        else -> null
    }
}

@Composable
private fun PlanInputRow.textValue(text: String): String = when (key) {
    PlanInputKey.Intention -> known(text, ::intentionResource)
    PlanInputKey.Sex -> known(text, ::sexResource)
    PlanInputKey.ActivityLevel -> known(text, ::activityResource)
    PlanInputKey.Place -> known(text, ::placeResource)
    PlanInputKey.Experience -> known(text, ::experienceResource)
    else -> text
}

@Composable
@Suppress("CyclomaticComplexMethod") // One branch per numeric key.
private fun PlanInputRow.numberValue(number: Double): String = when (key) {
    PlanInputKey.Age -> stringResource(Res.string.plan_input_age, number.toInt())

    PlanInputKey.Height -> stringResource(Res.string.plan_input_height, AppNumberFormat.integer(number.toInt()))

    PlanInputKey.Weight -> stringResource(Res.string.plan_input_weight, decimalText(number))

    PlanInputKey.TotalExpenditure -> stringResource(
        Res.string.trainer_kcal_value,
        AppNumberFormat.integer(number.toInt()),
    )

    PlanInputKey.WeightTrend -> stringResource(Res.string.plan_input_trend, signedTenths(number))

    PlanInputKey.DaysPerWeek -> pluralStringResource(Res.plurals.my_training_days_count, number.toInt(), number.toInt())

    PlanInputKey.SessionMinutes -> durationText(number.toLong() * SECONDS_PER_MINUTE)

    else -> decimalText(number)
}

/**
 * The value as the summary and "Tus datos" show it: the formatted value, "Sin definir" when empty ("Sin definir · Lo
 * elige el entrenador" when the coach picks it); an old weigh-in appends its date.
 */
@Composable
internal fun PlanInputRow.valueText(): String {
    val raw = rawValueText()
    return when {
        raw == null && marker == PlanInputMarker.Optional -> stringResource(Res.string.before_start_unset_optional)

        raw == null -> stringResource(Res.string.before_start_unset)

        marker == PlanInputMarker.Stale && date != null ->
            stringResource(Res.string.before_start_value_date, raw, AppDateFormat.title(date))

        else -> raw
    }
}

/** One summary line "Etiqueta · valor"; several keys of one line share the label ("Sexo y edad") and join values. */
@Composable
internal fun summaryLineText(rows: List<PlanInputRow>): String {
    val label = when {
        rows.size == 1 -> rows.single().labelText()

        rows.map {
            it.key
        } == listOf(PlanInputKey.Sex, PlanInputKey.Age) -> stringResource(Res.string.before_start_line_sex_age)

        rows.map { it.key } == listOf(PlanInputKey.Height, PlanInputKey.Weight) ->
            stringResource(Res.string.before_start_line_height_weight)

        else -> rows.map { it.labelText() }.joinToString(" y ")
    }
    val values = rows.map { it.valueText() }
    val value = values.joinToString(" · ")
    return stringResource(Res.string.before_start_line, label, value)
}
