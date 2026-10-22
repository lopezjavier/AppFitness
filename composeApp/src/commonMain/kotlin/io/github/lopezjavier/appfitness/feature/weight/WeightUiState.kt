package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.DayMeasurements
import io.github.lopezjavier.appfitness.domain.model.LatestComposition
import io.github.lopezjavier.appfitness.domain.usecase.BodyMeasurementProblem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** Why a save did not go through. */
enum class WeightSaveError { Offline, Failed }

/**
 * The manual weight form of the day sheet. [existingId] is the manual row being edited (null while creating); the date
 * is never part of the form, so it cannot be changed when editing. The problems are null while the field is blank or
 * valid; [canSave] also needs a weight, connectivity and no save in flight. There is no hour: a new weight is stored
 * at the save instant on today and at 12:00 local on a past day; an edit keeps its stored instant.
 */
data class MeasurementForm(
    val existingId: String?,
    val weightText: String,
    val fatText: String,
    val weightProblem: BodyMeasurementProblem?,
    val fatProblem: BodyMeasurementProblem?,
    val canSave: Boolean,
    val canDelete: Boolean,
    val isSaving: Boolean,
    val saveError: WeightSaveError?,
) {
    val isEditing: Boolean get() = existingId != null
}

/**
 * The open day sheet. [healthConnect] are that day's Health Connect rows (delete only). A day with a Health Connect
 * weight is [manualLocked] (ADR-47): [form] stays null and the manual entry cannot be created or edited.
 * [dayComposition] holds that day's body-composition values (null when the day has none).
 */
data class WeightSheet(
    val day: LocalDate,
    val isToday: Boolean,
    val healthConnect: List<BodyMeasurement>,
    val form: MeasurementForm?,
    val manualLocked: Boolean = false,
    val dayComposition: LatestComposition? = null,
)

/** Everything the weight screen shows once loaded. */
data class WeightContent(
    val month: YearMonth,
    val today: LocalDate,
    val canGoNext: Boolean,
    /** The latest measurement of any source, whatever the visible month; null when nothing was ever logged. */
    val current: BodyMeasurement?,
    /** The visible month's measurements by local day, newest day first. */
    val days: Map<LocalDate, DayMeasurements>,
    val isOnline: Boolean,
    val sheet: WeightSheet?,
    /** The newest value of each composition metric; null when it could not be read. */
    val composition: LatestComposition? = null,
    /** The "Composición corporal" sheet is open. */
    val compositionOpen: Boolean = false,
) {
    /** Days of the visible month with at least one measurement. */
    val markedDays: Set<LocalDate> get() = days.keys

    /** True when no measurement exists at all: the screen shows the empty state with the log button. */
    val isEmpty: Boolean get() = current == null
}

sealed interface WeightUiState {
    data object Loading : WeightUiState

    data object Error : WeightUiState

    data class Content(val content: WeightContent) : WeightUiState
}

sealed interface WeightEvent {
    data object PreviousMonth : WeightEvent

    data object NextMonth : WeightEvent

    /** "Registrar peso de hoy": today's sheet, in edit mode when today already has a manual weight. */
    data object LogToday : WeightEvent

    /** A calendar day was tapped; days after today are ignored. */
    data class OpenDay(val date: LocalDate) : WeightEvent

    /** Close the sheet discarding the draft (X, back, swipe, scrim). */
    data object CloseSheet : WeightEvent

    /** Ignored: a day with a Health Connect weight offers no manual entry (ADR-47). */
    data object StartManual : WeightEvent

    data object OpenComposition : WeightEvent

    data object CloseComposition : WeightEvent

    data class SetWeight(val text: String) : WeightEvent

    data class SetBodyFat(val text: String) : WeightEvent

    data object Save : WeightEvent

    /** Delete the manual row being edited at once (ADR-45): no confirmation, no undo. */
    data object DeleteManual : WeightEvent

    data class DeleteHealthConnect(val id: String) : WeightEvent

    data object Retry : WeightEvent

    /** Silent reload, e.g. on resume after a change in Preferencias. */
    data object Refresh : WeightEvent
}

sealed interface WeightEffect {
    /** A delete was rejected; [offline] when the cause was connectivity. */
    data class DeleteFailed(val offline: Boolean) : WeightEffect
}
