package io.github.lopezjavier.appfitness.feature.profile

/**
 * One editor of the Preferencias hub. Each section owns a set of [PreferencesForm] fields: only those are compared,
 * edited and saved by its editor (US-56); every other field keeps the saved value.
 */
enum class PreferencesSection(val key: String) {
    /** Name, sex, birth date, height, activity level and current weight. */
    PersonalData("personal"),

    /** Energy strategy and calorie adjustment. */
    EnergyGoal("energy"),

    /** Macro split. */
    Macros("macros"),

    /** Added-fat percentages per cooking method. */
    Cooking("cooking"),

    /** Warm-up, previous values, calculators and the automatic routine update. */
    Training("training"),
    ;

    companion object {
        /** The section of a route argument; an unknown or missing key falls back to [PersonalData]. */
        fun fromKey(key: String?): PreferencesSection = entries.firstOrNull { it.key == key } ?: PersonalData
    }
}

/** True when [other] holds the same values as this form in the fields [section] owns. */
fun PreferencesForm.sectionEquals(section: PreferencesSection, other: PreferencesForm): Boolean =
    ownedFields(section) == other.ownedFields(section)

/** The values of the fields [section] owns, in a fixed order. */
private fun PreferencesForm.ownedFields(section: PreferencesSection): List<Any> = when (section) {
    PreferencesSection.PersonalData -> listOf(name, sex, birthDate, heightCm, weightKg, activity)
    PreferencesSection.EnergyGoal -> listOf(strategy, adjustment)
    PreferencesSection.Macros -> listOf(split)
    PreferencesSection.Cooking -> listOf(foodPreferences)
    PreferencesSection.Training -> listOf(warmUpIncluded, previousValuesMode, calculators, autoUpdateRoutine)
}

/** This form with the fields [section] owns taken from [other]; every other field is kept. */
fun PreferencesForm.withSectionFrom(section: PreferencesSection, other: PreferencesForm): PreferencesForm =
    when (section) {
        PreferencesSection.PersonalData -> copy(
            name = other.name,
            sex = other.sex,
            birthDate = other.birthDate,
            heightCm = other.heightCm,
            weightKg = other.weightKg,
            activity = other.activity,
        )

        PreferencesSection.EnergyGoal -> copy(strategy = other.strategy, adjustment = other.adjustment)

        PreferencesSection.Macros -> copy(split = other.split)

        PreferencesSection.Cooking -> copy(foodPreferences = other.foodPreferences)

        PreferencesSection.Training -> copy(
            warmUpIncluded = other.warmUpIncluded,
            previousValuesMode = other.previousValuesMode,
            calculators = other.calculators,
            autoUpdateRoutine = other.autoUpdateRoutine,
        )
    }
