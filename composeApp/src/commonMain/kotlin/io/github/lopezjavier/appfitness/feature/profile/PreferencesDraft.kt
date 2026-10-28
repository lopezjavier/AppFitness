package io.github.lopezjavier.appfitness.feature.profile

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The unsaved [PreferencesForm] in a form that fits the saved-state handle (JSON), so an edit in progress survives
 * process death. Enums travel by name and the date as ISO text; a draft that no longer decodes is dropped.
 */
@Serializable
internal data class PreferencesDraft(
    val name: String,
    val sex: String,
    val birthDate: String,
    val heightCm: Int,
    val weightKg: Double,
    val activity: String,
    val strategy: String,
    val adjustment: Int,
    val proteinPct: Int,
    val carbsPct: Int,
    val fatPct: Int,
    /** Absent in drafts saved by older versions. */
    val warmUpIncluded: Boolean = false,
    /** [PreviousValuesMode] by name; absent in drafts saved by older versions. */
    val previousValuesMode: String = PreviousValuesMode.Overall.name,
    /** Calculator settings; absent in drafts saved by older versions (the defaults then). */
    val barWeightKg: Double = CalculatorSettings.DEFAULT_BAR_KG,
    val plateKg: List<Double> = CalculatorSettings.PLATE_CATALOGUE,
    val dumbbellHandleKg: Double = CalculatorSettings.DEFAULT_DUMBBELL_HANDLE_KG,
    /** "Cocina" fat percentages as typed; absent in drafts saved by older versions (the defaults then). */
    val fatPctGriddleText: String = FoodPreferencesForm().griddleText,
    val fatPctAirFryerText: String = FoodPreferencesForm().airFryerText,
    val fatPctFriedText: String = FoodPreferencesForm().friedText,
    /** Absent in drafts saved by older versions. */
    val autoUpdateRoutine: Boolean = false,
    /** ISO weekday of the check-in; absent in drafts saved by older versions (Sunday then). */
    val checkInWeekday: Int = PreferencesForm.DEFAULT_CHECK_IN_WEEKDAY,
    /** Reminder switches; null in drafts saved by older versions (the saved values then). */
    val weightReminder: Boolean? = null,
    val circumferenceReminder: Boolean? = null,
    val checkInReminder: Boolean? = null,
)

internal fun PreferencesForm.encodeDraft(): String = Json.encodeToString(
    PreferencesDraft.serializer(),
    PreferencesDraft(
        name = name,
        sex = sex.name,
        birthDate = birthDate.toString(),
        heightCm = heightCm,
        weightKg = weightKg,
        activity = activity.name,
        strategy = strategy.name,
        adjustment = adjustment,
        proteinPct = split.proteinPct,
        carbsPct = split.carbsPct,
        fatPct = split.fatPct,
        warmUpIncluded = warmUpIncluded,
        previousValuesMode = previousValuesMode.name,
        barWeightKg = calculators.barWeightKg,
        plateKg = calculators.plateKg,
        dumbbellHandleKg = calculators.dumbbellHandleKg,
        fatPctGriddleText = foodPreferences.griddleText,
        fatPctAirFryerText = foodPreferences.airFryerText,
        fatPctFriedText = foodPreferences.friedText,
        autoUpdateRoutine = autoUpdateRoutine,
        checkInWeekday = checkInWeekday,
        weightReminder = weightReminder,
        circumferenceReminder = circumferenceReminder,
        checkInReminder = checkInReminder,
    ),
)

/** [saved] supplies the values a draft from an older version does not carry (the reminder switches). */
internal fun decodeDraft(json: String, saved: PreferencesForm? = null): PreferencesForm? = runCatching {
    // Drafts saved before ADR-56 still carry the two rounding fields.
    val draft = Json { ignoreUnknownKeys = true }.decodeFromString(PreferencesDraft.serializer(), json)
    PreferencesForm(
        name = draft.name,
        sex = Sex.valueOf(draft.sex),
        birthDate = LocalDate.parse(draft.birthDate),
        heightCm = draft.heightCm,
        weightKg = draft.weightKg,
        activity = ActivityLevel.valueOf(draft.activity),
        strategy = GoalStrategy.valueOf(draft.strategy),
        adjustment = draft.adjustment,
        split = MacroSplit(draft.proteinPct, draft.carbsPct, draft.fatPct),
        warmUpIncluded = draft.warmUpIncluded,
        previousValuesMode = PreviousValuesMode.valueOf(draft.previousValuesMode),
        calculators = CalculatorSettings(
            barWeightKg = draft.barWeightKg,
            plateKg = draft.plateKg,
            dumbbellHandleKg = draft.dumbbellHandleKg,
        ),
        foodPreferences = FoodPreferencesForm(draft.fatPctGriddleText, draft.fatPctAirFryerText, draft.fatPctFriedText),
        autoUpdateRoutine = draft.autoUpdateRoutine,
        checkInWeekday = draft.checkInWeekday,
        weightReminder = draft.weightReminder ?: saved?.weightReminder ?: true,
        circumferenceReminder = draft.circumferenceReminder ?: saved?.circumferenceReminder ?: true,
        checkInReminder = draft.checkInReminder ?: saved?.checkInReminder ?: true,
    )
}.getOrNull()
