package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.usecase.CircumferenceProblem
import kotlinx.datetime.LocalDate

/** The rows of one [site], newest first. */
data class SiteHistory(val site: CircumferenceSite, val rows: List<BodyCircumference>)

/**
 * The add/edit sheet. [existingId] is the row being edited (null while adding); the site is locked when editing.
 * [valueProblem] is null while the field is blank or valid; [canSave] also needs a value, a time not in the future,
 * connectivity and no save in flight. [maxMinuteOfDay] bounds the time on today and is null on past days.
 */
data class CircumferenceSheetState(
    val existingId: String?,
    val site: CircumferenceSite,
    val valueText: String,
    val date: LocalDate,
    val hour: Int,
    val minute: Int,
    val maxMinuteOfDay: Int?,
    val canGoNextDay: Boolean,
    val valueProblem: CircumferenceProblem?,
    val canSave: Boolean,
    val canDelete: Boolean,
    val isSaving: Boolean,
    val saveError: WeightSaveError?,
) {
    val isEditing: Boolean get() = existingId != null
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

    /** Ignored when editing: the site is locked. */
    data class SelectSite(val site: CircumferenceSite) : CircumferencesEvent

    data class SetValue(val text: String) : CircumferencesEvent

    /** Dates after today are clamped to today. */
    data class SetDate(val date: LocalDate) : CircumferencesEvent

    /** A time after now on today is clamped to now. */
    data class SetTime(val hour: Int, val minute: Int) : CircumferencesEvent

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
