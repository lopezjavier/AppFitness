package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextLine
import io.github.lopezjavier.appfitness.domain.model.FreeTextStatus
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachIntakeRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * "Lo que el entrenador sabe de ti" (US-89): every free-text line the coach reads, grouped by origin. A switch is
 * optimistic and persisted on the server (R-42), so reopening shows it as left; nothing is kept locally.
 */
class FreeTextLinesViewModel(private val repository: CoachIntakeRepository, private val clock: Clock) : ViewModel() {
    private val _uiState = MutableStateFlow<FreeTextLinesUiState>(FreeTextLinesUiState.Loading)
    val uiState: StateFlow<FreeTextLinesUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<FreeTextLinesEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private val pending = mutableSetOf<Pair<FreeTextKind, String>>()

    // The server accepts a date within one day of UTC today, so it is the UTC date.
    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    init {
        load()
    }

    fun onEvent(event: FreeTextLinesEvent) {
        when (event) {
            FreeTextLinesEvent.Retry -> load()
            is FreeTextLinesEvent.Toggle -> toggle(event.kind, event.sourceId)
        }
    }

    private fun load() {
        _uiState.value = FreeTextLinesUiState.Loading
        viewModelScope.launch {
            val result = repository.freeTextLines(today())
            _uiState.value = result.fold(
                onSuccess = { FreeTextLinesUiState.Content(sectionsOf(it)) },
                onFailure = {
                    if (result.appErrorOrNull() == AppError.Network) {
                        FreeTextLinesUiState.Offline
                    } else {
                        FreeTextLinesUiState.Error
                    }
                },
            )
        }
    }

    private fun sectionsOf(lines: List<FreeTextLine>): List<FreeTextSection> = lines.groupBy { it.kind }
        .map { (kind, group) -> FreeTextSection(kind, group.sortedByDescending { it.date }) }
        .sortedBy { it.kind.ordinal }

    private fun toggle(kind: FreeTextKind, sourceId: String) {
        val content = _uiState.value as? FreeTextLinesUiState.Content
        val line = content?.sections?.flatMap { it.lines }?.firstOrNull { it.kind == kind && it.sourceId == sourceId }
        if (line == null || !pending.add(kind to sourceId)) return
        val exclude = line.status != FreeTextStatus.Excluded
        setStatus(kind, sourceId, if (exclude) FreeTextStatus.Excluded else FreeTextStatus.Sent)
        viewModelScope.launch {
            val result = repository.setLineExcluded(kind, sourceId, exclude)
            if (result.isSuccess) {
                refreshStatuses(kind, sourceId)
            } else {
                setStatus(kind, sourceId, line.status)
                effectChannel.send(FreeTextLinesEffect.ToggleFailed(result.appErrorOrNull() == AppError.Network))
            }
            pending.remove(kind to sourceId)
        }
    }

    /** After a successful toggle the server decides between sent and over the limit, so the line is re-read. */
    private suspend fun refreshStatuses(kind: FreeTextKind, sourceId: String) {
        val fresh = repository.freeTextLines(today()).getOrNull()
            ?.firstOrNull { it.kind == kind && it.sourceId == sourceId } ?: return
        setStatus(kind, sourceId, fresh.status)
    }

    private fun setStatus(kind: FreeTextKind, sourceId: String, status: FreeTextStatus) {
        _uiState.update { state ->
            if (state !is FreeTextLinesUiState.Content) return@update state
            state.copy(
                sections = state.sections.map { section ->
                    section.copy(
                        lines = section.lines.map {
                            if (it.kind == kind && it.sourceId == sourceId) it.copy(status = status) else it
                        },
                    )
                },
            )
        }
    }
}
