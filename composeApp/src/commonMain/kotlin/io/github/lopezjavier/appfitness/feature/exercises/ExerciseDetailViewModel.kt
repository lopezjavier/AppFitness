package io.github.lopezjavier.appfitness.feature.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.PersonalRecordCatalogue
import io.github.lopezjavier.appfitness.domain.usecase.SetEntryText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Instant

private const val PAGE_SIZE = 20
private const val KEY_TAB = "tab"

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
    ) : ExerciseDetailUiState
}

/**
 * Loads one exercise for the detail screen (US-28): the detail, its personal records, and the paged history of the
 * sessions that contain it (loaded when the History tab is first shown). The selected tab survives recreation.
 */
@Suppress("TooManyFunctions") // One function per event of the three tabs.
class ExerciseDetailViewModel(
    private val exerciseId: String,
    private val loadCatalog: LoadExerciseCatalog,
    private val workoutSessions: WorkoutSessionRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<ExerciseDetailUiState>(ExerciseDetailUiState.Loading)
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    /** Sessions the server returned so far; the next page starts here (deduplicated rows still count). */
    private var nextOffset = 0
    private var historyRequested = false
    private var historyJob: Job? = null

    /** Per-session aggregates used for the marks; null when unreadable (rows show no marks). */
    private var sessionRecords: List<ExerciseSessionRecords>? = null

    init {
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
                    _uiState.value = ExerciseDetailUiState.Loaded(detail, tab = storedTab())
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
