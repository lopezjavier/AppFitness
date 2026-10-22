package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.usecase.CircumferenceProblem
import kotlinx.datetime.LocalDate

/** The rows of one [site], newest first. */
data class SiteHistory(val site: CircumferenceSite, val rows: List<BodyCircumference>)

/**
 * The add/edit sheet. [existingId] is the row being edited (null while adding). Adding lists every site in [values]
 * (raw text, blank = not measured); editing has only that row's site. [problems] has an entry only for non-blank
 * invalid fields. [helpSite] is the site whose measuring instructions are open. [canSave] needs at least one value,
 * no problem, a day not in the future, connectivity and no save in flight.
 */
data class CircumferenceSheetState(
    val existingId: String?,
    val values: Map<CircumferenceSite, String>,
    val problems: Map<CircumferenceSite, CircumferenceProblem>,
    val date: LocalDate,
    val canGoNextDay: Boolean,
    val helpSite: CircumferenceSite?,
    val canSave: Boolean,
    val canDelete: Boolean,
    val isSaving: Boolean,
    val saveError: WeightSaveError?,
) {
    val isEditing: Boolean get() = existingId != null

    /** The locked site of the row being edited. */
    val editSite: CircumferenceSite? get() = if (isEditing) values.keys.firstOrNull() else null
}

/** What the circumference section shows once loaded; [groups] only has sites with rows, in site order. */
data class CircumferencesContent(
    val groups: List<SiteHistory>,
    /** Waist-to-hip ratio, null without a waist and a hip row. */
    val ratio: Double?,
    val isOnline: Boolean,
    val sheet: CircumferenceSheetState?,
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

sealed interface CircumferencesUiState {
    data object Loading : CircumferencesUiState

    data object Error : CircumferencesUiState

    data class Content(val content: CircumferencesContent) : CircumferencesUiState
}

sealed interface CircumferencesEvent {
    data object OpenAdd : CircumferencesEvent

    data class OpenEdit(val id: String) : CircumferencesEvent

    /** Ignored for a site not in the sheet (the site of an edited row is locked). */
    data class SetValue(val site: CircumferenceSite, val text: String) : CircumferencesEvent

    /** Dates after today are clamped to today. */
    data class SetDate(val date: LocalDate) : CircumferencesEvent

    /** Opens the instructions of [site], closing any other; closes them when already open. */
    data class ToggleHelp(val site: CircumferenceSite) : CircumferencesEvent

    data object Save : CircumferencesEvent

    /** Delete a row at once (ADR-45): no confirmation, no undo. */
    data class Delete(val id: String) : CircumferencesEvent

    /** Close the sheet discarding the draft. */
    data object CloseSheet : CircumferencesEvent

    data object Retry : CircumferencesEvent

    /** Silent reload, e.g. on resume. */
    data object Refresh : CircumferencesEvent
}

sealed interface CircumferencesEffect {
    /** A delete was rejected; [offline] when the cause was connectivity. */
    data class DeleteFailed(val offline: Boolean) : CircumferencesEffect
}
