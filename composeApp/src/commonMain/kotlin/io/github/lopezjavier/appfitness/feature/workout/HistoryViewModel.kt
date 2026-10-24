package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.repository.warmUpIncludedOrDefault
import io.github.lopezjavier.appfitness.domain.usecase.ComputeSessionStats
import io.github.lopezjavier.appfitness.domain.usecase.perHandIdsOrDefault
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

/**
 * The history of completed sessions (US-14), newest first, 20 per page. Pages are fetched by offset; rows are
 * deduplicated by id on append because a session finished meanwhile shifts the window. A refresh reloads from the
 * first page.
 */
class HistoryViewModel(
    private val repository: WorkoutSessionRepository,
    private val preferences: TrainingPreferencesRepository,
    private val clock: Clock,
    private val bodyMeasurements: BodyMeasurementRepository,
    private val exercises: ExerciseRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<HistoryEffect>(Channel.BUFFERED)
    val effects: Flow<HistoryEffect> = effectChannel.receiveAsFlow()

    /** Rows the server returned so far; the next page starts here (deduplicated rows do not count less). */
    private var nextOffset = 0
    private var job: Job? = null

    /** The warm-up setting read with the first page; later pages use it too so the rows stay consistent. */
    private var includeWarmUp = false

    /** Latest body weight read with the first page, for the bodyweight and assisted volume; null when unknown. */
    private var bodyWeightKg: Double? = null

    /** Per-hand exercises (volume counts twice), read with the first page and applied to every page. */
    private var perHandIds: Set<String> = emptySet()

    init {
        loadFirstPage(showLoading = true)
    }

    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the events.
    fun onEvent(event: HistoryEvent) {
        when (event) {
            HistoryEvent.Retry -> if (_uiState.value == HistoryUiState.Error) loadFirstPage(showLoading = true)

            HistoryEvent.Refresh -> if (_uiState.value.isLoaded()) loadFirstPage(showLoading = false)

            HistoryEvent.LoadMore -> (_uiState.value as? HistoryUiState.Content)?.let {
                if (!it.loadingMore && !it.endReached && !it.pageError) loadNextPage(it)
            }

            HistoryEvent.RetryLoadMore -> (_uiState.value as? HistoryUiState.Content)?.let {
                if (!it.loadingMore && it.pageError) loadNextPage(it)
            }

            is HistoryEvent.OpenSession -> effectChannel.trySend(HistoryEffect.OpenDetail(event.id))
        }
    }

    private fun HistoryUiState.isLoaded() = this is HistoryUiState.Content || this == HistoryUiState.Empty

    private fun loadFirstPage(showLoading: Boolean) {
        job?.cancel()
        if (showLoading) _uiState.value = HistoryUiState.Loading
        job = viewModelScope.launch {
            includeWarmUp = preferences.warmUpIncludedOrDefault()
            bodyWeightKg = bodyMeasurements.getLatestWeight().getOrNull()?.weightKg
            perHandIds = exercises.perHandIdsOrDefault()
            repository.listCompleted(0, PAGE_SIZE).fold(
                onSuccess = { page ->
                    nextOffset = page.size
                    _uiState.value = if (page.isEmpty()) {
                        HistoryUiState.Empty
                    } else {
                        HistoryUiState.Content(page.map { it.toRow() }, endReached = page.size < PAGE_SIZE)
                    }
                },
                onFailure = {
                    val current = _uiState.value
                    if (showLoading) {
                        _uiState.value = HistoryUiState.Error
                    } else if (current is HistoryUiState.Content && current.loadingMore) {
                        // The refresh cancelled an in-flight page load: keep the rows, allow loading more again.
                        _uiState.value = current.copy(loadingMore = false)
                    }
                },
            )
        }
    }

    private fun loadNextPage(current: HistoryUiState.Content) {
        job?.cancel()
        _uiState.value = current.copy(loadingMore = true, pageError = false)
        job = viewModelScope.launch {
            repository.listCompleted(nextOffset, PAGE_SIZE).fold(
                onSuccess = { page ->
                    nextOffset += page.size
                    val known = current.rows.map { it.id }.toSet()
                    _uiState.value = current.copy(
                        rows = current.rows + page.filter { it.id !in known }.map { it.toRow() },
                        loadingMore = false,
                        pageError = false,
                        endReached = page.size < PAGE_SIZE,
                    )
                },
                onFailure = { _uiState.value = current.copy(loadingMore = false, pageError = true) },
            )
        }
    }

    private fun WorkoutSession.toRow(): HistoryRow {
        val stats = ComputeSessionStats(this, includeWarmUp, bodyWeightKg, perHandIds)
        return HistoryRow(
            id = id,
            label = sessionDateLabel(sessionLocalDate(startedAt), clock.today()),
            name = name,
            durationSeconds = stats.durationSeconds,
            volumeKg = stats.volumeKg,
            completedSets = stats.completedSets,
        )
    }
}
