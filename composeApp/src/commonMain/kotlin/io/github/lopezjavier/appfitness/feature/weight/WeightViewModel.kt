package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.DayMeasurements
import io.github.lopezjavier.appfitness.domain.model.LatestComposition
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.usecase.HealthWriteBack
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.WeightMonth
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

private const val KEY_MONTH = "weight_month"
private const val KEY_SHEET_DAY = "weight_sheet_day"
private const val KEY_EXISTING_ID = "weight_draft_existing_id"
private const val KEY_WEIGHT = "weight_draft_weight"
private const val KEY_FAT = "weight_draft_fat"
private const val KEY_HAS_FORM = "weight_draft_open"
private const val KEY_COMPOSITION_OPEN = "weight_composition_open"
private const val NONE = ""

/** What one month load fetched: the panel's latest row and the month's rows, also grouped by day. */
private class WeightData(
    val month: YearMonth,
    val current: BodyMeasurement?,
    val rows: List<BodyMeasurement>,
    val days: Map<LocalDate, DayMeasurements>,
    val composition: LatestComposition?,
)

private sealed interface WeightLoad {
    data object Loading : WeightLoad

    data object Failed : WeightLoad

    data class Ready(val data: WeightData) : WeightLoad
}

/** The open day sheet before it is joined with the data; [draft] is null for a Health Connect only day. */
private data class SheetState(
    val day: LocalDate,
    val draft: MeasurementDraft?,
    val saving: Boolean = false,
    val error: WeightSaveError? = null,
)

/**
 * The weight screen (US-25): the current weight, a month calendar of the days with measurements and a day sheet that
 * creates, edits (weight, fat; the date is locked, no hour is asked) or deletes the day's manual weight, and lists its
 * Health Connect rows (delete only). Deleting is immediate (ADR-45). The visible month, the open day and the draft
 * live in [savedState] as primitives, so they survive recreation; closing the sheet discards the draft. Writes need
 * connectivity. Prefill rule for a new weight: today uses the latest weight; a past day uses the latest weight at or
 * before it in the visible month, else the latest overall; none leaves the field blank.
 */
// One dependency per port; one small handler per event; guard clauses return early.
@Suppress("LongParameterList", "TooManyFunctions", "ReturnCount", "CyclomaticComplexMethod")
class WeightViewModel(
    private val repository: BodyMeasurementRepository,
    private val measurementsByDay: MeasurementsByDay,
    private val saveBodyMeasurement: SaveBodyMeasurement,
    private val validate: ValidateBodyMeasurement,
    connectivity: ConnectivityObserver,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val healthWriteBack: HealthWriteBack? = null,
) : ViewModel() {
    private val month = MutableStateFlow(
        savedState.get<String>(KEY_MONTH)?.let(YearMonth::parse) ?: clock.today().yearMonth,
    )
    private val load = MutableStateFlow<WeightLoad>(WeightLoad.Loading)
    private val sheet = MutableStateFlow(restoreSheet())
    private val online = MutableStateFlow(true)
    private val compositionOpen = MutableStateFlow(savedState.get<Boolean>(KEY_COMPOSITION_OPEN) == true)
    private var loadJob: Job? = null
    private var openJob: Job? = null

    private val effectChannel = Channel<WeightEffect>(Channel.BUFFERED)
    val effects: Flow<WeightEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<WeightUiState> = combine(load, sheet, online, compositionOpen, ::buildState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeightUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        reload()
    }

    fun onEvent(event: WeightEvent) {
        when (event) {
            WeightEvent.PreviousMonth -> showMonth(month.value.minus(1, DateTimeUnit.MONTH))

            WeightEvent.NextMonth -> if (month.value < clock.today().yearMonth) {
                showMonth(month.value.plus(1, DateTimeUnit.MONTH))
            }

            WeightEvent.LogToday -> open(clock.today())

            is WeightEvent.OpenDay -> open(event.date)

            WeightEvent.CloseSheet -> setSheet(null)

            // A day with a Health Connect weight offers no manual entry (ADR-47); any other day already has a form.

            WeightEvent.OpenComposition -> setCompositionOpen(true)

            WeightEvent.CloseComposition -> setCompositionOpen(false)

            is WeightEvent.SetWeight -> edit { it.copy(weightText = event.text) }

            is WeightEvent.SetBodyFat -> edit { it.copy(fatText = event.text) }

            WeightEvent.Save -> save()

            WeightEvent.DeleteManual -> deleteManual()

            is WeightEvent.DeleteHealthConnect -> deleteHealthConnect(event.id)

            WeightEvent.Retry -> if (load.value == WeightLoad.Failed) reload()

            WeightEvent.Refresh -> if (load.value is WeightLoad.Ready) reload(silent = true)
        }
    }

    // Loading

    private fun showMonth(target: YearMonth) {
        month.value = target
        savedState[KEY_MONTH] = target.toString()
        reload()
    }

    private fun reload(silent: Boolean = false) {
        loadJob?.cancel()
        if (!silent) load.value = WeightLoad.Loading
        loadJob = viewModelScope.launch {
            val data = fetch(month.value)
            when {
                data != null -> load.value = WeightLoad.Ready(data)
                !silent -> load.value = WeightLoad.Failed
            }
        }
    }

    /** Null when either read fails. */
    private suspend fun fetch(target: YearMonth): WeightData? {
        val (from, toExclusive) = WeightMonth.bounds(target, timeZone)
        val (latest, rows, composition) = coroutineScope {
            val latest = async { repository.getLatestWeight() }
            val rows = async { repository.getMeasurements(from, toExclusive) }
            val composition = async { repository.getLatestComposition() }
            Triple(latest.await(), rows.await(), composition.await())
        }
        if (latest.isFailure || rows.isFailure) return null
        val monthRows = rows.getOrThrow()
        return WeightData(
            target,
            latest.getOrNull(),
            monthRows,
            measurementsByDay(monthRows, timeZone),
            composition.getOrNull(),
        )
    }

    private fun setCompositionOpen(open: Boolean) {
        compositionOpen.value = open
        savedState[KEY_COMPOSITION_OPEN] = open
    }

    private suspend fun refreshData(): WeightData? = fetch(month.value)?.also { load.value = WeightLoad.Ready(it) }

    // Sheet

    private fun open(day: LocalDate) {
        if (day > clock.today()) return
        openJob?.cancel()
        openJob = viewModelScope.launch {
            if (month.value != day.yearMonth) {
                showMonth(day.yearMonth)
                loadJob?.join()
            }
            val data = (load.value as? WeightLoad.Ready)?.data ?: return@launch
            val dayRows = data.days[day]
            val manual = dayRows?.manual
            val draft = when {
                dayRows?.hasHealthConnectWeight == true -> null
                manual != null -> editDraft(manual)
                else -> newDraft(day, data)
            }
            setSheet(SheetState(day, draft))
        }
    }

    private suspend fun newDraft(day: LocalDate, data: WeightData): MeasurementDraft {
        val prefill = if (day == clock.today()) {
            repository.getLatestWeight().getOrNull()
        } else {
            WeightMonth.prefill(data.rows, day, timeZone) ?: repository.getLatestWeight().getOrNull()
        }
        return MeasurementDraft(
            existingId = null,
            weightText = prefill?.let { decimalText(it.weightKg) }.orEmpty(),
            fatText = NONE,
        )
    }

    private fun edit(change: (MeasurementDraft) -> MeasurementDraft) {
        val state = sheet.value ?: return
        val draft = state.draft ?: return
        if (state.saving) return
        setSheet(state.copy(draft = change(draft), error = null))
    }

    private fun setSheet(state: SheetState?) {
        sheet.value = state
        savedState[KEY_SHEET_DAY] = state?.day?.toString() ?: NONE
        val draft = state?.draft
        savedState[KEY_EXISTING_ID] = draft?.existingId ?: NONE
        savedState[KEY_WEIGHT] = draft?.weightText ?: NONE
        savedState[KEY_FAT] = draft?.fatText ?: NONE
        savedState[KEY_HAS_FORM] = draft != null
    }

    private fun restoreSheet(): SheetState? {
        val day = savedState.get<String>(KEY_SHEET_DAY)?.takeIf { it != NONE }?.let(LocalDate::parse) ?: return null
        val draft = if (savedState.get<Boolean>(KEY_HAS_FORM) != true) {
            null
        } else {
            MeasurementDraft(
                existingId = savedState.get<String>(KEY_EXISTING_ID)?.takeIf { it != NONE },
                weightText = savedState.get<String>(KEY_WEIGHT).orEmpty(),
                fatText = savedState.get<String>(KEY_FAT).orEmpty(),
            )
        }
        return SheetState(day, draft)
    }

    // Writes

    private fun save() {
        val state = sheet.value ?: return
        val draft = state.draft ?: return
        val data = (load.value as? WeightLoad.Ready)?.data ?: return
        val weight = draft.weightKg ?: return
        if (data.days[state.day]?.hasHealthConnectWeight == true) return
        if (!online.value || state.saving) return
        val existing = draft.existingId?.let { id -> data.days[state.day]?.manual?.takeIf { it.id == id } }
        setSheet(state.copy(saving = true, error = null))
        viewModelScope.launch {
            val result = saveBodyMeasurement(weight, draft.bodyFatPct, state.day, existing)
            if (result.isSuccess) {
                refreshData()
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

    private fun deleteManual() {
        val state = sheet.value ?: return
        val id = state.draft?.existingId ?: return
        if (state.saving) return
        val dayRows = (load.value as? WeightLoad.Ready)?.data?.days?.get(state.day)
        if (dayRows?.hasHealthConnectWeight == true) return
        val row = dayRows?.manual?.takeIf { it.id == id }
        deleteRow(id, onDeleted = { row?.let { healthWriteBack?.onDeleted(it) } }) { setSheet(null) }
    }

    private fun deleteHealthConnect(id: String) {
        val state = sheet.value ?: return
        deleteRow(id) { data ->
            val left = data?.days?.get(state.day)?.healthConnect.orEmpty()
            if (left.isEmpty() && sheet.value?.draft == null) setSheet(null)
        }
    }

    private fun deleteRow(id: String, onDeleted: suspend () -> Unit = {}, afterReload: (WeightData?) -> Unit) {
        if (!online.value) return
        viewModelScope.launch {
            val result = repository.deleteMeasurement(id)
            if (result.isSuccess) {
                onDeleted()
                afterReload(refreshData())
            } else {
                effectChannel.send(WeightEffect.DeleteFailed(offline = result.appErrorOrNull() == AppError.Network))
            }
        }
    }

    // State

    private fun buildState(
        load: WeightLoad,
        sheet: SheetState?,
        online: Boolean,
        compositionOpen: Boolean,
    ): WeightUiState = when (load) {
        WeightLoad.Loading -> WeightUiState.Loading

        WeightLoad.Failed -> WeightUiState.Error

        is WeightLoad.Ready -> {
            val today = clock.today()
            WeightUiState.Content(
                WeightContent(
                    month = load.data.month,
                    today = today,
                    canGoNext = load.data.month < today.yearMonth,
                    current = load.data.current,
                    days = load.data.days,
                    isOnline = online,
                    sheet = sheet?.let { sheetOf(it, load.data, online) },
                    composition = load.data.composition,
                    compositionOpen = compositionOpen,
                ),
            )
        }
    }

    private fun sheetOf(state: SheetState, data: WeightData, online: Boolean): WeightSheet {
        val day = data.days[state.day]
        val locked = day?.hasHealthConnectWeight == true
        val composition = day?.let { LatestComposition.from(it.healthConnect + listOfNotNull(it.manual)) }
        return WeightSheet(
            day = state.day,
            isToday = state.day == clock.today(),
            healthConnect = day?.healthConnect.orEmpty(),
            form = state.draft?.takeUnless { locked }?.let { formOf(it, validate, online, state.saving, state.error) },
            manualLocked = locked,
            dayComposition = composition?.takeIf { it != LatestComposition() },
        )
    }
}
