package io.github.lopezjavier.appfitness.feature.profile

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MacroTargets
import io.github.lopezjavier.appfitness.domain.model.Macronutrient
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.CalculateMacroTargets
import io.github.lopezjavier.appfitness.feature.food.formatNumber
import io.github.lopezjavier.appfitness.feature.food.parseAmount
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategyEvent
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategyUiState
import io.github.lopezjavier.appfitness.feature.goals.energyStrategyUiState
import kotlinx.datetime.LocalDate

/**
 * Everything Preferencias edits: the name, the biometrics, the current weight, the energy strategy and the macro
 * split. [weightKg] has one decimal (a multiple of 0.1).
 */
data class PreferencesForm(
    val name: String,
    val sex: Sex,
    val birthDate: LocalDate,
    val heightCm: Int,
    val weightKg: Double,
    val activity: ActivityLevel,
    val strategy: GoalStrategy,
    val adjustment: Int,
    val split: MacroSplit,
    /** "Incluir calentamiento": warm-up sets count in volume, 1RM, records and set totals. */
    val warmUpIncluded: Boolean = false,
    /** "Valores anteriores": where the live session takes the previous values from. */
    val previousValuesMode: PreviousValuesMode = PreviousValuesMode.Overall,
    /** "Calculadoras": default bar, available plates and the plate/dumbbell rounding. */
    val calculators: CalculatorSettings = CalculatorSettings(),
    /** "Cocina": the added-fat percentages per cooking method, as typed. */
    val foodPreferences: FoodPreferencesForm = FoodPreferencesForm(),
    /** "Actualizar automáticamente" (ADR-70): a finished routine session updates its routine without asking. */
    val autoUpdateRoutine: Boolean = false,
    /** "Día del check-in": ISO weekday, 1 (Monday) to 7 (Sunday). */
    val checkInWeekday: Int = DEFAULT_CHECK_IN_WEEKDAY,
    /** "Recordatorio de peso": a missing stored row means enabled. */
    val weightReminder: Boolean = true,
    /** "Recordatorio de circunferencias". */
    val circumferenceReminder: Boolean = true,
    /** "Check-in semanal". */
    val checkInReminder: Boolean = true,
) {
    fun toBiometrics(): Biometrics = Biometrics(sex, birthDate, heightCm, activity)

    /** The typed fat percentages, or null while any is blank or outside 0..20. */
    fun toFoodPreferences(): FoodPreferences? = foodPreferences.toFoodPreferences()

    /** The name without surrounding blanks is what gets stored; an empty one is not valid. */
    val isNameValid: Boolean get() = name.isNotBlank()

    companion object {
        /** Sunday. */
        const val DEFAULT_CHECK_IN_WEEKDAY = 7
    }
}

/**
 * The three added-fat percentages of "Cocina" as typed, so a blank or a negative text can exist. Comma and dot are
 * both decimal separators. A field is in error when it is blank, not a number or outside
 * 0..[FoodPreferences.MAX_PCT].
 */
data class FoodPreferencesForm(
    val griddleText: String = formatNumber(FoodPreferences.DEFAULT_GRIDDLE_PCT),
    val airFryerText: String = formatNumber(FoodPreferences.DEFAULT_AIR_FRYER_PCT),
    val friedText: String = formatNumber(FoodPreferences.DEFAULT_FRIED_PCT),
) {
    val griddleError: Boolean get() = parseFatPct(griddleText) == null
    val airFryerError: Boolean get() = parseFatPct(airFryerText) == null
    val friedError: Boolean get() = parseFatPct(friedText) == null

    val isValid: Boolean get() = !griddleError && !airFryerError && !friedError

    fun toFoodPreferences(): FoodPreferences? {
        val griddle = parseFatPct(griddleText)
        val airFryer = parseFatPct(airFryerText)
        val fried = parseFatPct(friedText)
        return if (griddle == null || airFryer == null || fried == null) {
            null
        } else {
            FoodPreferences(griddle, airFryer, fried)
        }
    }

    companion object {
        fun of(prefs: FoodPreferences) = FoodPreferencesForm(
            formatNumber(prefs.fatPctGriddle),
            formatNumber(prefs.fatPctAirFryer),
            formatNumber(prefs.fatPctFried),
        )
    }
}

private fun parseFatPct(text: String): Double? = parseAmount(text)?.takeIf(FoodPreferences::isValidPct)

private const val DAYS_PER_WEEK = 7

/** State of the Preferencias screen. */
sealed interface PreferencesUiState {
    data object Loading : PreferencesUiState

    /** Any read failed, or the profile is incomplete (no weight, missing biometrics). */
    data object LoadFailed : PreferencesUiState

    /**
     * [form] is what the user is editing, [saved] what the server holds. The energy card and the macro targets follow
     * [form] live, including its weight.
     */
    data class Loaded(
        val form: PreferencesForm,
        val saved: PreferencesForm,
        /** The editor this state belongs to: only its fields count for [isDirty] and [canSave]. */
        val section: PreferencesSection,
        val today: LocalDate,
        val isSaving: Boolean = false,
        val isOnline: Boolean = true,
        /** The latest weight comes from Health Connect: the weight is read-only here (ADR-47). */
        val weightFromHealthConnect: Boolean = false,
        /** The user tried to switch off the last plate; cleared by the next accepted plate change. */
        val platesError: Boolean = false,
        /** The section was saved: the editor closes (once). */
        val isSaved: Boolean = false,
    ) : PreferencesUiState {
        /** Only the fields of [section] count. */
        val isDirty: Boolean get() = !form.sectionEquals(section, saved)

        /**
         * "Guardar" needs a change in this section, a filled name (the edited one in "Datos personales", the saved
         * one elsewhere, since every save re-sends it), at least one plate and valid fat percentages where the
         * section owns them, the network and no save running.
         */
        val canSave: Boolean
            get() = isDirty && isOnline && !isSaving && when (section) {
                PreferencesSection.PersonalData -> form.isNameValid

                PreferencesSection.Training -> saved.isNameValid && form.calculators.plateKg.isNotEmpty()

                PreferencesSection.Cooking -> saved.isNameValid && form.foodPreferences.isValid

                PreferencesSection.EnergyGoal, PreferencesSection.Macros -> saved.isNameValid

                // The reminders are written alone, so no other field has to be valid.
                PreferencesSection.Reminders -> form.checkInWeekday in 1..DAYS_PER_WEEK
            }

        val energy: EnergyStrategyUiState
            get() = energyStrategyUiState(
                sex = form.sex,
                birthDate = form.birthDate,
                heightCm = form.heightCm,
                weightKg = form.weightKg,
                activityLevel = form.activity,
                today = today,
                strategy = form.strategy,
                adjustment = form.adjustment,
                enabled = !isSaving,
            )

        val macroTargets: MacroTargets get() = CalculateMacroTargets(energy.targetKcal, form.split)
    }
}

/** Events of the Preferencias screen. */
sealed interface PreferencesEvent {
    data class SetName(val name: String) : PreferencesEvent

    data class SelectSex(val sex: Sex) : PreferencesEvent

    data class SetBirthDate(val date: LocalDate) : PreferencesEvent

    data class SetHeight(val heightCm: Int) : PreferencesEvent

    /** The weight picker applied [tenths] tenths of a kilogram. */
    data class SetWeight(val tenths: Int) : PreferencesEvent

    data class SelectActivity(val level: ActivityLevel) : PreferencesEvent

    data class Energy(val event: EnergyStrategyEvent) : PreferencesEvent

    /** A macro slider moved to [percent]; the others rebalance. */
    data class MoveMacro(val macro: Macronutrient, val percent: Int) : PreferencesEvent

    /** The "Incluir calentamiento" switch; only a draft until [Save]. */
    data class SetWarmUpIncluded(val included: Boolean) : PreferencesEvent

    /** The "Actualizar automáticamente" switch (a draft until "Guardar"). */
    data class SetAutoUpdateRoutine(val enabled: Boolean) : PreferencesEvent

    /** The "Valores anteriores" selector; only a draft until [Save]. */
    data class SetPreviousValuesMode(val mode: PreviousValuesMode) : PreferencesEvent

    /** Default bar of the calculators (20, 15, 10 or 7,5 kg); only a draft until [Save]. */
    data class SetBarWeight(val kg: Double) : PreferencesEvent

    /** Dumbbell handle weight in kg (clamped to 0,5..10, steps of 0,5); only a draft until [Save]. */
    data class SetDumbbellHandle(val kg: Double) : PreferencesEvent

    /** Switches one plate of the catalogue on or off; the last plate cannot be switched off (sets `platesError`). */
    data class SetPlateEnabled(val kg: Double, val enabled: Boolean) : PreferencesEvent

    /** "Grasa añadida a la plancha (%)" as typed; only a draft until [Save]. */
    data class SetFatPctGriddle(val text: String) : PreferencesEvent

    /** "Grasa añadida en air fryer (%)" as typed; only a draft until [Save]. */
    data class SetFatPctAirFryer(val text: String) : PreferencesEvent

    /** "Grasa añadida al freír (%)" as typed; only a draft until [Save]. */
    data class SetFatPctFried(val text: String) : PreferencesEvent

    /** "Día del check-in" picked (ISO weekday 1..7); only a draft until [Save]. */
    data class SetCheckInWeekday(val weekday: Int) : PreferencesEvent

    /** A reminder switch of "Avisos"; only a draft until [Save]. */
    data class SetReminderEnabled(val kind: NotificationKind, val enabled: Boolean) : PreferencesEvent

    data object Save : PreferencesEvent

    data object Retry : PreferencesEvent

    /** Reloads only the weight and its Health Connect flag (e.g. on resume); never saves, keeps other fields. */
    data object Refresh : PreferencesEvent
}

/** The section that owns an event; null for the section-independent ones (save, retry). */
internal val PreferencesEvent.owner: PreferencesSection?
    get() = when (this) {
        is PreferencesEvent.SetName, is PreferencesEvent.SelectSex, is PreferencesEvent.SetBirthDate,
        is PreferencesEvent.SetHeight, is PreferencesEvent.SetWeight, is PreferencesEvent.SelectActivity,
        PreferencesEvent.Refresh,
        -> PreferencesSection.PersonalData

        is PreferencesEvent.Energy -> PreferencesSection.EnergyGoal

        is PreferencesEvent.MoveMacro -> PreferencesSection.Macros

        is PreferencesEvent.SetFatPctGriddle, is PreferencesEvent.SetFatPctAirFryer,
        is PreferencesEvent.SetFatPctFried,
        -> PreferencesSection.Cooking

        is PreferencesEvent.SetWarmUpIncluded, is PreferencesEvent.SetAutoUpdateRoutine,
        is PreferencesEvent.SetPreviousValuesMode, is PreferencesEvent.SetBarWeight,
        is PreferencesEvent.SetDumbbellHandle, is PreferencesEvent.SetPlateEnabled,
        -> PreferencesSection.Training

        is PreferencesEvent.SetCheckInWeekday, is PreferencesEvent.SetReminderEnabled -> PreferencesSection.Reminders

        PreferencesEvent.Save, PreferencesEvent.Retry -> null
    }

/** One-off results of a save; success is the `isSaved` flag of the state, not an effect. */
sealed interface PreferencesEffect {
    /** [offline] is true when the failure was the network. */
    data class SaveFailed(val offline: Boolean) : PreferencesEffect
}
