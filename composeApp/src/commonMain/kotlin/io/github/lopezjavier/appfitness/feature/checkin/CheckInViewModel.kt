package io.github.lopezjavier.appfitness.feature.checkin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CheckIn
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.WeeklyCheckInRepository
import io.github.lopezjavier.appfitness.domain.usecase.LoadCheckInSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

private const val KEY_ENERGY = "checkin_energy"
private const val KEY_NOTES = "checkin_notes"

/**
 * The weekly check-in (US-59): the closed week in figures and the energy/notes form. The open check-in comes from the
 * [ReminderCoordinator] (no route argument, so opening it on the Monday after still shows the closed week). The summary
 * loads on its own: a failure shows an error block with a retry and never blocks the form. An unsaved energy/notes
 * draft survives process death through [savedState] (ADR-40). Saving upserts the week, refreshes the center and then
 * emits [CheckInEffect.Saved]. [exercises] only names the records of the summary (a failed lookup leaves them unnamed).
 */
class CheckInViewModel(
    private val coordinator: ReminderCoordinator,
    private val checkIns: WeeklyCheckInRepository,
    private val loadSummary: LoadCheckInSummary,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val exercises: ExerciseRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CheckInUiState>(CheckInUiState.Loading)
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<CheckInEffect>(Channel.BUFFERED)
    val effects: Flow<CheckInEffect> = effectChannel.receiveAsFlow()

    private var online = true
    private var summaryJob: Job? = null

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { isOnline ->
                online = isOnline
                updateReady { it.copy(isOffline = !isOnline) }
            }
        }
        load()
    }

    fun onEvent(event: CheckInEvent) {
        when (event) {
            is CheckInEvent.SelectEnergy ->
                if (event.energy in CHECK_IN_ENERGY_RANGE) edit { it.copy(energy = event.energy) }

            is CheckInEvent.SetNotes -> edit { it.copy(notes = event.notes) }

            CheckInEvent.Save -> save()

            CheckInEvent.Retry -> if (_uiState.value == CheckInUiState.LoadFailed) load()

            CheckInEvent.RetrySummary -> (_uiState.value as? CheckInUiState.Ready)?.let { reloadSummary(it.window.day) }
        }
    }

    private fun load() {
        _uiState.value = CheckInUiState.Loading
        viewModelScope.launch {
            val window = coordinator.checkIn.value ?: run {
                coordinator.refresh()
                coordinator.checkIn.value
            }
            if (window == null) {
                _uiState.value = CheckInUiState.LoadFailed
                return@launch
            }
            _uiState.value = CheckInUiState.Ready(
                window = window,
                energy = savedState.get<Int>(KEY_ENERGY) ?: window.answer?.energy,
                notes = savedState.get<String>(KEY_NOTES) ?: window.answer?.notes.orEmpty(),
                summary = CheckInSummaryState.Loading,
                isOffline = !online,
            )
            reloadSummary(window.day)
        }
    }

    private fun reloadSummary(day: LocalDate) {
        summaryJob?.cancel()
        updateReady { it.copy(summary = CheckInSummaryState.Loading) }
        summaryJob = viewModelScope.launch {
            val result = loadSummary(day)
            val state = result.fold(
                onSuccess = { CheckInSummaryState.Loaded(it) },
                onFailure = { CheckInSummaryState.Failed },
            )
            updateReady { it.copy(summary = state) }
            val records = (state as? CheckInSummaryState.Loaded)?.summary?.records.orEmpty()
            if (records.isNotEmpty() && exercises != null) {
                val names = exercises.getAll().getOrNull().orEmpty()
                    .mapNotNull { exercise -> exercise.name?.let { exercise.id to it } }
                    .toMap()
                updateReady { it.copy(exerciseNames = names) }
            }
        }
    }

    private fun edit(transform: (CheckInUiState.Ready) -> CheckInUiState.Ready) {
        _uiState.update { state ->
            if (state is CheckInUiState.Ready && !state.isSaving) transform(state).also(::persistDraft) else state
        }
    }

    private fun updateReady(transform: (CheckInUiState.Ready) -> CheckInUiState.Ready) {
        _uiState.update { if (it is CheckInUiState.Ready) transform(it) else it }
    }

    /** An unsaved draft is kept in the saved state; equal to what is stored, nothing is kept. */
    private fun persistDraft(state: CheckInUiState.Ready) {
        savedState[KEY_ENERGY] = state.energy.takeIf { state.isDirty }
        savedState[KEY_NOTES] = state.notes.takeIf { state.isDirty }
    }

    private fun save() {
        val state = _uiState.value as? CheckInUiState.Ready ?: return
        val energy = state.energy
        if (!state.canSave || energy == null) return
        updateReady { it.copy(isSaving = true) }
        viewModelScope.launch {
            val answer = CheckIn(state.window.weekStart, energy, state.notes)
            val result = checkIns.save(answer)
            if (result.isSuccess) {
                // The center must already show the answered check-in when the editor closes.
                coordinator.refresh()
                savedState[KEY_ENERGY] = null
                savedState[KEY_NOTES] = null
                updateReady { it.copy(window = it.window.copy(answer = answer), isSaving = false) }
                effectChannel.send(CheckInEffect.Saved)
            } else {
                updateReady { it.copy(isSaving = false) }
                effectChannel.send(CheckInEffect.SaveFailed(offline = result.appErrorOrNull() == AppError.Network))
            }
        }
    }
}
