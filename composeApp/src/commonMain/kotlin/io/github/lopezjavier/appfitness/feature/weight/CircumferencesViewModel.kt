package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceEntry
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
import kotlin.time.Instant

private const val KEY_OPEN = "circumference_sheet_open"
private const val KEY_EXISTING_ID = "circumference_draft_existing_id"
private const val KEY_VALUES = "circumference_draft_values"
private const val KEY_DATE = "circumference_draft_date"
private const val KEY_HELP = "circumference_draft_help"
private const val NONE = ""
private const val ENTRY_SEPARATOR = ";"
private const val PAIR_SEPARATOR = "="
private const val NOON_HOUR = 12

private sealed interface CircumferenceLoad {
    data object Loading : CircumferenceLoad

    data object Failed : CircumferenceLoad

    data class Ready(val rows: List<BodyCircumference>) : CircumferenceLoad
}

/**
 * The draft as typed: the texts are kept raw so a half-typed number survives. Adding has an entry per site, editing
 * only the row's own site.
 */
private data class SheetDraft(
    val existingId: String?,
    val values: Map<CircumferenceSite, String>,
    val date: LocalDate,
    val helpSite: CircumferenceSite? = null,
    val saving: Boolean = false,
    val error: WeightSaveError? = null,
)

/**
 * The circumference section of the weight screen (US-25): the history per site, the waist-to-hip ratio and an
 * add/edit sheet. Adding saves every filled site at once, for a day (no hour); editing changes one row, whose site is
 * locked. Deleting is immediate (ADR-45). The open sheet and its draft live in [savedState] as primitives, so they
 * survive recreation; closing the sheet discards the draft. Writes need connectivity; the day cannot be in the future.
 * A new measurement is stored at the save instant on today and at 12:00 local on a past day.
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
                SheetDraft(null, CircumferenceSite.entries.associateWith { NONE }, clock.today()),
            )

            is CircumferencesEvent.OpenEdit -> openEdit(event.id)

            is CircumferencesEvent.SetValue -> edit { draft ->
                if (event.site in
                    draft.values
                ) {
                    draft.copy(values = draft.values + (event.site to event.text))
                } else {
                    draft
                }
            }

            is CircumferencesEvent.SetDate -> edit { it.copy(date = minOf(event.date, clock.today())) }

            is CircumferencesEvent.ToggleHelp -> edit {
                it.copy(helpSite = if (it.helpSite == event.site) null else event.site)
            }

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
        setSheet(
            SheetDraft(
                existingId = row.id,
                values = mapOf(row.site to decimalText(row.valueCm)),
                date = row.measuredAt.toLocalDateTime(timeZone).date,
            ),
        )
    }

    private fun edit(change: (SheetDraft) -> SheetDraft) {
        val draft = sheet.value ?: return
        if (draft.saving) return
        setSheet(change(draft).copy(error = null))
    }

    private fun setSheet(draft: SheetDraft?) {
        sheet.value = draft
        savedState[KEY_OPEN] = draft != null
        savedState[KEY_EXISTING_ID] = draft?.existingId ?: NONE
        savedState[KEY_VALUES] = draft?.values?.entries
            ?.joinToString(ENTRY_SEPARATOR) { "${it.key.name}$PAIR_SEPARATOR${it.value}" } ?: NONE
        savedState[KEY_DATE] = draft?.date?.toString() ?: NONE
        savedState[KEY_HELP] = draft?.helpSite?.name ?: NONE
    }

    private fun restoreSheet(): SheetDraft? {
        if (savedState.get<Boolean>(KEY_OPEN) != true) return null
        val date = savedState.get<String>(KEY_DATE)?.takeIf { it != NONE }?.let(LocalDate::parse) ?: return null
        val stored = savedState.get<String>(KEY_VALUES).orEmpty().split(ENTRY_SEPARATOR).mapNotNull { entry ->
            val (name, text) = entry.split(PAIR_SEPARATOR, limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            CircumferenceSite.entries.firstOrNull { it.name == name }?.let { it to text }
        }.toMap()
        if (stored.isEmpty()) return null
        val existingId = savedState.get<String>(KEY_EXISTING_ID)?.takeIf { it != NONE }
        return SheetDraft(
            existingId = existingId,
            values = if (existingId == null) CircumferenceSite.entries.associateWith { stored[it] ?: NONE } else stored,
            date = date,
            helpSite = CircumferenceSite.entries.firstOrNull { it.name == savedState.get<String>(KEY_HELP) },
        )
    }

    // Writes

    private fun save() {
        val draft = sheet.value ?: return
        val entries = entriesOf(draft)
        if (entries.isEmpty() || !online.value || draft.saving) return
        if (problemsOf(draft).isNotEmpty() || draft.date > clock.today()) return
        val measuredAt = instantFor(draft)
        setSheet(draft.copy(saving = true, error = null))
        viewModelScope.launch {
            val result = draft.existingId
                ?.let { repository.update(it, entries.single().valueCm, measuredAt) }
                ?: repository.addAll(entries, measuredAt)
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

    /** Today: the save instant; a past day: 12:00 local; an edited row on its own day keeps its instant. */
    private fun instantFor(draft: SheetDraft): Instant {
        val original = draft.existingId?.let { id ->
            (load.value as? CircumferenceLoad.Ready)?.rows?.firstOrNull { it.id == id }?.measuredAt
        }
        return when {
            original != null && original.toLocalDateTime(timeZone).date == draft.date -> original
            draft.date == clock.today() -> clock.now()
            else -> LocalDateTime(draft.date, LocalTime(NOON_HOUR, 0)).toInstant(timeZone)
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

    private fun entriesOf(draft: SheetDraft): List<CircumferenceEntry> = draft.values.mapNotNull { (site, text) ->
        if (text.isBlank()) null else parseDecimal(text)?.let { CircumferenceEntry(site, it) }
    }

    private fun problemsOf(draft: SheetDraft): Map<CircumferenceSite, CircumferenceProblem> =
        draft.values.mapNotNull { (site, text) ->
            if (text.isBlank()) return@mapNotNull null
            val value = parseDecimal(text) ?: return@mapNotNull site to CircumferenceProblem.NotANumber
            (validate(value) as? CircumferenceCheck.Invalid)?.problem?.let { site to it }
        }.toMap()

    private fun buildState(load: CircumferenceLoad, draft: SheetDraft?, online: Boolean): CircumferencesUiState =
        when (load) {
            CircumferenceLoad.Loading -> CircumferencesUiState.Loading

            CircumferenceLoad.Failed -> CircumferencesUiState.Error

            is CircumferenceLoad.Ready -> {
                val rows = load.rows.sortedWith(
                    compareByDescending<BodyCircumference> { it.measuredAt }
                        .thenByDescending { it.createdAt }
                        .thenBy { it.id },
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
        val problems = problemsOf(draft)
        return CircumferenceSheetState(
            existingId = draft.existingId,
            values = draft.values,
            problems = problems,
            date = draft.date,
            canGoNextDay = draft.date < clock.today(),
            helpSite = draft.helpSite,
            canSave = entriesOf(draft).isNotEmpty() && problems.isEmpty() && draft.date <= clock.today() &&
                online && !draft.saving,
            canDelete = draft.existingId != null && online && !draft.saving,
            isSaving = draft.saving,
            saveError = draft.error,
        )
    }
}
