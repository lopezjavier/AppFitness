package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.CircumferenceRepository
import io.github.lopezjavier.appfitness.domain.usecase.CircumferenceCheck
import io.github.lopezjavier.appfitness.domain.usecase.CircumferenceProblem
import io.github.lopezjavier.appfitness.domain.usecase.ValidateCircumference
import io.github.lopezjavier.appfitness.domain.usecase.WaistHipRatio
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private const val KEY_OPEN = "circumference_sheet_open"
private const val KEY_EXISTING_ID = "circumference_draft_existing_id"
private const val KEY_SITE = "circumference_draft_site"
private const val KEY_VALUE = "circumference_draft_value"
private const val KEY_DATE = "circumference_draft_date"
private const val KEY_MINUTE = "circumference_draft_minute"
private const val NONE = ""
private const val NO_DRAFT = -1
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

private sealed interface CircumferenceLoad {
    data object Loading : CircumferenceLoad

    data object Failed : CircumferenceLoad

    data class Ready(val rows: List<BodyCircumference>) : CircumferenceLoad
}

/** The draft as typed: the value text is kept raw so a half-typed number survives; the time is minutes of day. */
private data class SheetDraft(
    val existingId: String?,
    val site: CircumferenceSite,
    val valueText: String,
    val date: LocalDate,
    val minuteOfDay: Int,
    val saving: Boolean = false,
    val error: WeightSaveError? = null,
)

/**
 * The circumference section of the weight screen (US-25): the history per site, the waist-to-hip ratio and an
 * add/edit sheet. Deleting is immediate (ADR-45); the site of a row is locked when editing. The open sheet and its
 * draft live in [savedState] as primitives, so they survive recreation; closing the sheet discards the draft. Writes
 * need connectivity; the date and time cannot be in the future.
 */
// One dependency per port; one small handler per event; guard clauses return early.
@Suppress("LongParameterList", "TooManyFunctions", "ReturnCount", "CyclomaticComplexMethod")
class CircumferencesViewModel(
    private val repository: CircumferenceRepository,
    private val validate: ValidateCircumference,
    private val waistHipRatio: WaistHipRatio,
    connectivity: ConnectivityObserver,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val load = MutableStateFlow<CircumferenceLoad>(CircumferenceLoad.Loading)
    private val sheet = MutableStateFlow(restoreSheet())
    private val online = MutableStateFlow(true)

    private val effectChannel = Channel<CircumferencesEffect>(Channel.BUFFERED)
    val effects: Flow<CircumferencesEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<CircumferencesUiState> = combine(load, sheet, online, ::buildState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, CircumferencesUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        reload()
    }

    fun onEvent(event: CircumferencesEvent) {
        when (event) {
            CircumferencesEvent.OpenAdd -> setSheet(
                SheetDraft(null, CircumferenceSite.Waist, NONE, clock.today(), nowMinuteOfDay()),
            )

            is CircumferencesEvent.OpenEdit -> openEdit(event.id)

            is CircumferencesEvent.SelectSite -> edit { if (it.existingId == null) it.copy(site = event.site) else it }

            is CircumferencesEvent.SetValue -> edit { it.copy(valueText = event.text) }

            is CircumferencesEvent.SetDate -> setDate(event.date)

            is CircumferencesEvent.SetTime -> setTime(event.hour, event.minute)

            CircumferencesEvent.Save -> save()

            is CircumferencesEvent.Delete -> delete(event.id)

            CircumferencesEvent.CloseSheet -> setSheet(null)

            CircumferencesEvent.Retry -> if (load.value == CircumferenceLoad.Failed) reload()

            CircumferencesEvent.Refresh -> if (load.value is CircumferenceLoad.Ready) reload(silent = true)
        }
    }

    // Loading

    private fun reload(silent: Boolean = false) {
        if (!silent) load.value = CircumferenceLoad.Loading
        viewModelScope.launch { fetch(silent) }
    }

    /** True when the rows were loaded. A silent failure keeps the rows already shown. */
    private suspend fun fetch(silent: Boolean = true): Boolean {
        val result = repository.getAll()
        when {
            result.isSuccess -> load.value = CircumferenceLoad.Ready(result.getOrThrow())
            !silent -> load.value = CircumferenceLoad.Failed
        }
        return result.isSuccess
    }

    // Sheet

    private fun openEdit(id: String) {
        val row = (load.value as? CircumferenceLoad.Ready)?.rows?.firstOrNull { it.id == id } ?: return
        val local = row.measuredAt.toLocalDateTime(timeZone)
        setSheet(
            SheetDraft(
                existingId = row.id,
                site = row.site,
                valueText = decimalText(row.valueCm),
                date = local.date,
                minuteOfDay = local.hour * MINUTES_PER_HOUR + local.minute,
            ),
        )
    }

    private fun nowMinuteOfDay(): Int {
        val local = clock.now().toLocalDateTime(timeZone)
        return local.hour * MINUTES_PER_HOUR + local.minute
    }

    private fun maxMinuteOf(day: LocalDate): Int? = if (day >= clock.today()) nowMinuteOfDay() else null

    private fun edit(change: (SheetDraft) -> SheetDraft) {
        val draft = sheet.value ?: return
        if (draft.saving) return
        setSheet(change(draft).copy(error = null))
    }

    private fun setDate(date: LocalDate) = edit { draft ->
        val day = minOf(date, clock.today())
        draft.copy(date = day, minuteOfDay = draft.minuteOfDay.coerceAtMost(maxMinuteOf(day) ?: Int.MAX_VALUE))
    }

    private fun setTime(hour: Int, minute: Int) = edit { draft ->
        val max = maxMinuteOf(draft.date) ?: (MINUTES_PER_DAY - 1)
        val requested = (hour * MINUTES_PER_HOUR + minute).coerceIn(0, MINUTES_PER_DAY - 1)
        draft.copy(minuteOfDay = requested.coerceAtMost(max))
    }

    private fun setSheet(draft: SheetDraft?) {
        sheet.value = draft
        savedState[KEY_OPEN] = draft != null
        savedState[KEY_EXISTING_ID] = draft?.existingId ?: NONE
        savedState[KEY_SITE] = draft?.site?.name ?: NONE
        savedState[KEY_VALUE] = draft?.valueText ?: NONE
        savedState[KEY_DATE] = draft?.date?.toString() ?: NONE
        savedState[KEY_MINUTE] = draft?.minuteOfDay ?: NO_DRAFT
    }

    private fun restoreSheet(): SheetDraft? {
        if (savedState.get<Boolean>(KEY_OPEN) != true) return null
        val site = savedState.get<String>(KEY_SITE)?.takeIf { it != NONE }?.let(CircumferenceSite::valueOf)
        val date = savedState.get<String>(KEY_DATE)?.takeIf { it != NONE }?.let(LocalDate::parse)
        val minute = savedState.get<Int>(KEY_MINUTE) ?: NO_DRAFT
        if (site == null || date == null || minute == NO_DRAFT) return null
        return SheetDraft(
            existingId = savedState.get<String>(KEY_EXISTING_ID)?.takeIf { it != NONE },
            site = site,
            valueText = savedState.get<String>(KEY_VALUE).orEmpty(),
            date = date,
            minuteOfDay = minute,
        )
    }

    // Writes

    private fun save() {
        val draft = sheet.value ?: return
        val value = parseDecimal(draft.valueText) ?: return
        if (!online.value || draft.saving || valueProblemOf(draft) != null) return
        if (timeProblemOf(draft)) return
        val measuredAt = LocalDateTime(draft.date, draft.time()).toInstant(timeZone)
        setSheet(draft.copy(saving = true, error = null))
        viewModelScope.launch {
            val result = draft.existingId
                ?.let { repository.update(it, value, measuredAt) }
                ?: repository.add(draft.site, value, measuredAt)
            if (result.isSuccess) {
                fetch()
                setSheet(null)
            } else {
                val error = if (result.appErrorOrNull() == AppError.Network) {
                    WeightSaveError.Offline
                } else {
                    WeightSaveError.Failed
                }
                sheet.value?.let { setSheet(it.copy(saving = false, error = error)) }
            }
        }
    }

    private fun delete(id: String) {
        if (!online.value) return
        viewModelScope.launch {
            val result = repository.delete(id)
            if (result.isSuccess) {
                fetch()
                if (sheet.value?.existingId == id) setSheet(null)
            } else {
                effectChannel.send(CircumferencesEffect.DeleteFailed(result.appErrorOrNull() == AppError.Network))
            }
        }
    }

    // State

    private fun SheetDraft.time() = LocalTime(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)

    private fun valueProblemOf(draft: SheetDraft): CircumferenceProblem? {
        if (draft.valueText.isBlank()) return null
        val value = parseDecimal(draft.valueText) ?: return CircumferenceProblem.NotANumber
        return (validate(value) as? CircumferenceCheck.Invalid)?.problem
    }

    private fun timeProblemOf(draft: SheetDraft): Boolean {
        val max = maxMinuteOf(draft.date)
        return draft.date > clock.today() || (max != null && draft.minuteOfDay > max)
    }

    private fun buildState(load: CircumferenceLoad, draft: SheetDraft?, online: Boolean): CircumferencesUiState =
        when (load) {
            CircumferenceLoad.Loading -> CircumferencesUiState.Loading

            CircumferenceLoad.Failed -> CircumferencesUiState.Error

            is CircumferenceLoad.Ready -> {
                val rows = load.rows.sortedWith(
                    compareByDescending<BodyCircumference> {
                        it.measuredAt
                    }.thenBy { it.id },
                )
                CircumferencesUiState.Content(
                    CircumferencesContent(
                        groups = CircumferenceSite.entries.mapNotNull { site ->
                            rows.filter { it.site == site }.takeIf { it.isNotEmpty() }?.let { SiteHistory(site, it) }
                        },
                        ratio = waistHipRatio(rows),
                        isOnline = online,
                        sheet = draft?.let { sheetOf(it, online) },
                    ),
                )
            }
        }

    private fun sheetOf(draft: SheetDraft, online: Boolean): CircumferenceSheetState {
        val value = parseDecimal(draft.valueText)
        val problem = valueProblemOf(draft)
        return CircumferenceSheetState(
            existingId = draft.existingId,
            site = draft.site,
            valueText = draft.valueText,
            date = draft.date,
            hour = draft.minuteOfDay / MINUTES_PER_HOUR,
            minute = draft.minuteOfDay % MINUTES_PER_HOUR,
            maxMinuteOfDay = maxMinuteOf(draft.date),
            canGoNextDay = draft.date < clock.today(),
            valueProblem = problem,
            canSave = value != null && problem == null && !timeProblemOf(draft) && online && !draft.saving,
            canDelete = draft.existingId != null && online && !draft.saving,
            isSaving = draft.saving,
            saveError = draft.error,
        )
    }
}
