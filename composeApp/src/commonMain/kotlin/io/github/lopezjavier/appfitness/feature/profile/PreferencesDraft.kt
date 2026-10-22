package io.github.lopezjavier.appfitness.feature.profile

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
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
    ),
)

internal fun decodeDraft(json: String): PreferencesForm? = runCatching {
    val draft = Json.decodeFromString(PreferencesDraft.serializer(), json)
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
    )
}.getOrNull()
