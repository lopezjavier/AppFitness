package io.github.lopezjavier.appfitness.feature.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.PersonalRecordCatalogue
import io.github.lopezjavier.appfitness.domain.usecase.SetEntryText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Instant

private const val PAGE_SIZE = 20
private const val KEY_TAB = "tab"
private const val KEY_EDIT_OPEN = "detail_edit_open"
private const val KEY_EDIT_NAME = "detail_edit_name"
private const val KEY_EDIT_MUSCLE = "detail_edit_muscle"
private const val KEY_EDIT_EQUIPMENT = "detail_edit_equipment"
private const val KEY_EDIT_PER_HAND = "detail_edit_per_hand"

/** Longest exercise name, matching the DB check. */
private const val NAME_MAX_LENGTH = 100

/**
 * The edit sheet of a manual exercise (same fields as the create sheet, minus the metric type, which cannot change).
 * Nothing is applied until "Guardar" ([ExerciseDetailViewModel.submitEdit]).
 */
data class EditExerciseForm(
    val name: String,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val perHand: Boolean,
    val isSubmitting: Boolean = false,
    val hasError: Boolean = false,
) {
    val canSubmit: Boolean get() = name.isNotBlank() && !isSubmitting
}

/** One-off results of the detail screen. */
sealed interface ExerciseDetailEffect {
    /** The manual exercise was deleted: leave the screen and show "Ejercicio eliminado". */
    data object Deleted : ExerciseDetailEffect

    /** The delete was refused because the exercise is used by [sets] sets and [routines] routines. */
    data class InUse(val sets: Int, val routines: Int) : ExerciseDetailEffect

    /** The delete failed for another reason (network, server). */
    data object DeleteFailed : ExerciseDetailEffect

    /** The edit was applied and the form closed. */
    data object Saved : ExerciseDetailEffect

    /** The edit failed; the form stays open. */
    data object SaveFailed : ExerciseDetailEffect
}

/** The tabs of the exercise detail; the selected one is kept in the saved state by name. */
enum class ExerciseDetailTab { Summary, History, HowTo }

/** Personal records of the exercise (T-094), already filtered to the families of its metric type. */
sealed interface RecordsState {
    data object Loading : RecordsState

    /** No record logged yet. */
    data object Empty : RecordsState

    data class Content(val records: List<PersonalRecord>) : RecordsState

    data object Error : RecordsState
}

/** One completed set of a history row; [text] is the formatted metric values. */
data class HistorySetUi(val setIndex: Int, val setType: SetType, val text: String, val rpe: Double?)

/** One completed session containing the exercise; [records] are the families it beat. */
data class ExerciseHistoryRow(
    val sessionId: String,
    val startedAt: Instant,
    val name: String,
    val sets: List<HistorySetUi>,
    val records: Set<PersonalRecordType>,
)

/** Completed sessions containing the exercise, paged by offset (20 per page). */
sealed interface HistoryState {
    /** First page not loaded yet or in flight. */
    data object Loading : HistoryState

    data object Empty : HistoryState

    data class Content(
        val rows: List<ExerciseHistoryRow>,
        val loadingMore: Boolean = false,
        val endReached: Boolean = false,
        val pageError: Boolean = false,
    ) : HistoryState

    data object Error : HistoryState
}

sealed interface ExerciseDetailUiState {
    data object Loading : ExerciseDetailUiState

    data object Error : ExerciseDetailUiState

    data class Loaded(
        val detail: ExerciseDetail,
        val tab: ExerciseDetailTab = ExerciseDetailTab.Summary,
        val records: RecordsState = RecordsState.Loading,
        val history: HistoryState = HistoryState.Loading,
        /** The edit sheet (manual exercises only); null when closed. Survives process death (ADR-40). */
        val editForm: EditExerciseForm? = null,
        /** False while offline: edit and delete are disabled with a hint. */
        val isOnline: Boolean = true,
        /** A delete is in flight (double taps are ignored). */
        val isDeleting: Boolean = false,
    ) : ExerciseDetailUiState {
        /** Only the user's own manual exercises can be edited or deleted. */
        val canManage: Boolean get() = detail.exercise.isCustom
    }
}

/**
 * Loads one exercise for the detail screen (US-28): the detail, its personal records, and the paged history of the
 * sessions that contain it (loaded when the History tab is first shown). The selected tab survives recreation.
 */
@Suppress("TooManyFunctions") // One function per event of the three tabs and the edit/delete actions.
class ExerciseDetailViewModel(
    private val exerciseId: String,
    private val loadCatalog: LoadExerciseCatalog,
    private val repository: ExerciseRepository,
    private val workoutSessions: WorkoutSessionRepository,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<ExerciseDetailUiState>(ExerciseDetailUiState.Loading)
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<ExerciseDetailEffect>(Channel.BUFFERED)
    val effects: Flow<ExerciseDetailEffect> = effectChannel.receiveAsFlow()

    /** Sessions the server returned so far; the next page starts here (deduplicated rows still count). */
    private var nextOffset = 0
    private var historyRequested = false
    private var historyJob: Job? = null

    /** Per-session aggregates used for the marks; null when unreadable (rows show no marks). */
    private var sessionRecords: List<ExerciseSessionRecords>? = null

    private var online = true

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { isOnline ->
                online = isOnline
                updateLoaded { it.copy(isOnline = isOnline) }
            }
        }
        load()
    }

    fun retry() {
        if (_uiState.value == ExerciseDetailUiState.Error) load()
    }

    fun selectTab(tab: ExerciseDetailTab) {
        savedState[KEY_TAB] = tab.name
        updateLoaded { it.copy(tab = tab) }
        if (tab == ExerciseDetailTab.History) loadHistoryIfNeeded()
    }

    fun retryRecords() {
        if (loaded()?.records != RecordsState.Error) return
        updateLoaded { it.copy(records = RecordsState.Loading) }
        viewModelScope.launch { loadRecords() }
    }

    fun retryHistory() {
        if (loaded()?.history != HistoryState.Error) return
        historyRequested = false
        loadHistoryIfNeeded()
    }

    fun loadMoreHistory() {
        val content = loaded()?.history as? HistoryState.Content ?: return
        if (!content.loadingMore && !content.endReached && !content.pageError) loadNextPage(content)
    }

    fun retryHistoryPage() {
        val content = loaded()?.history as? HistoryState.Content ?: return
        if (!content.loadingMore && content.pageError) loadNextPage(content)
    }

    fun openEdit() {
        val exercise = loaded()?.detail?.exercise?.takeIf { it.isCustom } ?: return
        setEditForm(
            EditExerciseForm(exercise.name.orEmpty(), exercise.muscleGroup, exercise.equipment, exercise.perHand),
        )
    }

    fun dismissEdit() = setEditForm(null)

    fun setEditName(name: String) = editForm { it.copy(name = name.take(NAME_MAX_LENGTH), hasError = false) }

    fun selectEditMuscleGroup(group: MuscleGroup) = editForm { it.copy(muscleGroup = group) }

    fun selectEditEquipment(equipment: Equipment) = editForm { it.copy(equipment = equipment) }

    fun setEditPerHand(perHand: Boolean) = editForm { it.copy(perHand = perHand) }

    /** "Guardar": applies the edit; on success the form closes and the detail shows the new values. */
    fun submitEdit() {
        val form = loaded()?.editForm?.takeIf { it.canSubmit && online } ?: return
        setEditForm(form.copy(isSubmitting = true, hasError = false))
        viewModelScope.launch {
            repository.updateCustom(exerciseId, form.name.trim(), form.muscleGroup, form.equipment, form.perHand).fold(
                onSuccess = {
                    loadCatalog.detail(exerciseId).onSuccess { detail -> updateLoaded { it.copy(detail = detail) } }
                    setEditForm(null)
                    effectChannel.trySend(ExerciseDetailEffect.Saved)
                },
                onFailure = {
                    setEditForm(form.copy(isSubmitting = false, hasError = true))
                    effectChannel.trySend(ExerciseDetailEffect.SaveFailed)
                },
            )
        }
    }

    /**
     * Deletes the manual exercise at once (no dialog). Refused offline and while a delete is running; an exercise in
     * use is refused by the server and nothing is deleted.
     */
    fun delete() {
        if (loaded()?.let { it.canManage && online && !it.isDeleting } != true) return
        updateLoaded { it.copy(isDeleting = true) }
        viewModelScope.launch {
            val result = repository.deleteCustom(exerciseId)
            val error = result.appErrorOrNull()
            updateLoaded { it.copy(isDeleting = false) }
            effectChannel.trySend(
                when {
                    result.isSuccess -> ExerciseDetailEffect.Deleted
                    error is AppError.InUse -> ExerciseDetailEffect.InUse(error.sets, error.routines)
                    else -> ExerciseDetailEffect.DeleteFailed
                },
            )
        }
    }

    private fun editForm(change: (EditExerciseForm) -> EditExerciseForm) {
        loaded()?.editForm?.let { setEditForm(change(it)) }
    }

    private fun setEditForm(form: EditExerciseForm?) {
        updateLoaded { it.copy(editForm = form) }
        savedState[KEY_EDIT_OPEN] = form != null
        savedState[KEY_EDIT_NAME] = form?.name
        savedState[KEY_EDIT_MUSCLE] = form?.muscleGroup?.name
        savedState[KEY_EDIT_EQUIPMENT] = form?.equipment?.name
        savedState[KEY_EDIT_PER_HAND] = form?.perHand
    }

    private fun restoredEditForm(detail: ExerciseDetail): EditExerciseForm? {
        if (!detail.exercise.isCustom || savedState.get<Boolean>(KEY_EDIT_OPEN) != true) return null
        val exercise = detail.exercise
        return EditExerciseForm(
            name = savedState.get<String>(KEY_EDIT_NAME) ?: exercise.name.orEmpty(),
            muscleGroup = MuscleGroup.entries.firstOrNull { it.name == savedState.get<String>(KEY_EDIT_MUSCLE) }
                ?: exercise.muscleGroup,
            equipment = Equipment.entries.firstOrNull { it.name == savedState.get<String>(KEY_EDIT_EQUIPMENT) }
                ?: exercise.equipment,
            perHand = savedState.get<Boolean>(KEY_EDIT_PER_HAND) ?: exercise.perHand,
        )
    }

    private fun loaded() = _uiState.value as? ExerciseDetailUiState.Loaded

    private fun updateLoaded(change: (ExerciseDetailUiState.Loaded) -> ExerciseDetailUiState.Loaded) {
        loaded()?.let { _uiState.value = change(it) }
    }

    private fun load() {
        _uiState.value = ExerciseDetailUiState.Loading
        historyJob?.cancel()
        historyRequested = false
        nextOffset = 0
        viewModelScope.launch {
            loadCatalog.detail(exerciseId).fold(
                onSuccess = { detail ->
                    _uiState.value = ExerciseDetailUiState.Loaded(
                        detail,
                        tab = storedTab(),
                        editForm = restoredEditForm(detail),
                        isOnline = online,
                    )
                    loadRecords()
                    if (storedTab() == ExerciseDetailTab.History) loadHistoryIfNeeded()
                },
                onFailure = { _uiState.value = ExerciseDetailUiState.Error },
            )
        }
    }

    private fun storedTab(): ExerciseDetailTab =
        ExerciseDetailTab.entries.firstOrNull { it.name == savedState.get<String>(KEY_TAB) }
            ?: ExerciseDetailTab.Summary

    private suspend fun loadRecords() {
        val metricType = loaded()?.detail?.exercise?.metricType ?: return
        val state = workoutSessions.personalRecords(exerciseId).fold(
            onSuccess = { all ->
                val shown = recordFamilies(metricType)
                val records = all.filter { it.type in shown }.sortedBy { shown.indexOf(it.type) }
                if (records.isEmpty()) RecordsState.Empty else RecordsState.Content(records)
            },
            onFailure = { RecordsState.Error },
        )
        updateLoaded { it.copy(records = state) }
    }

    /** The catalogue families, except total reps which only rep-only exercises show. */
    private fun recordFamilies(metricType: ExerciseMetricType): List<PersonalRecordType> =
        PersonalRecordCatalogue.families(metricType)
            .filter { it != PersonalRecordType.TotalReps || metricType == ExerciseMetricType.RepsOnly }

    private fun loadHistoryIfNeeded() {
        if (historyRequested || loaded() == null) return
        historyRequested = true
        updateLoaded { it.copy(history = HistoryState.Loading) }
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            sessionRecords = workoutSessions.sessionRecords(exerciseId).getOrNull()
            workoutSessions.exerciseSessions(exerciseId, 0, PAGE_SIZE).fold(
                onSuccess = { page ->
                    nextOffset = page.size
                    updateLoaded {
                        it.copy(
                            history = if (page.isEmpty()) {
                                HistoryState.Empty
                            } else {
                                HistoryState.Content(page.map { s -> s.toRow() }, endReached = page.size < PAGE_SIZE)
                            },
                        )
                    }
                },
                onFailure = { updateLoaded { it.copy(history = HistoryState.Error) } },
            )
        }
    }

    private fun loadNextPage(current: HistoryState.Content) {
        updateLoaded { it.copy(history = current.copy(loadingMore = true, pageError = false)) }
        historyJob = viewModelScope.launch {
            workoutSessions.exerciseSessions(exerciseId, nextOffset, PAGE_SIZE).fold(
                onSuccess = { page ->
                    nextOffset += page.size
                    val known = current.rows.map { it.sessionId }.toSet()
                    updateLoaded {
                        it.copy(
                            history = current.copy(
                                rows = current.rows + page.filter { s -> s.id !in known }.map { s -> s.toRow() },
                                loadingMore = false,
                                pageError = false,
                                endReached = page.size < PAGE_SIZE,
                            ),
                        )
                    }
                },
                onFailure = { updateLoaded { it.copy(history = current.copy(loadingMore = false, pageError = true)) } },
            )
        }
    }

    private fun WorkoutSession.toRow(): ExerciseHistoryRow {
        val all = sessionRecords
        val mine = all?.firstOrNull { it.sessionId == id }
        return ExerciseHistoryRow(
            sessionId = id,
            startedAt = startedAt,
            name = name,
            sets = exercises.flatMap { it.sets }.filter { it.isCompleted }.sortedBy { it.setIndex }.map {
                HistorySetUi(it.setIndex, it.setType, SetEntryText.format(it.entry), it.rpe)
            },
            records = if (all != null && mine != null) {
                PersonalRecordCatalogue.beatenBy(mine, all)
            } else {
                emptySet()
            },
        )
    }
}
