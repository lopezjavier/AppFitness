package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.SplitOption
import io.github.lopezjavier.appfitness.domain.model.SplitSession
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val KEY_OPTIONS = "splits_options"
private const val KEY_SELECTED = "splits_selected"
private const val KEY_BUILDING = "splits_building"

/**
 * "Tipo de rutina" (US-93): asks the coach for the split options, then builds the block on the picked one. This model
 * owns both paid calls. Nothing is saved: the block is only stored for the preview and "Guardar bloque" saves it.
 * [savedState] keeps the options (re-asking would bill again), the pick and whether a build was in flight; a recreated
 * model with a build in flight looks for the stored block and else shows [PlanFailureKind.Interrupted], and never
 * asks by itself.
 */
@Suppress("TooManyFunctions") // One small handler per event.
class BlockSplitsViewModel(
    private val planId: String,
    private val blocks: CoachBlockRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<BlockSplitsUiState>(BlockSplitsUiState.Idle)
    val uiState: StateFlow<BlockSplitsUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<BlockSplitsEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var options: List<SplitOption> = emptyList()
    private var selectedId: String? = null

    // The server accepts a date within one day of UTC today, so it is the UTC date.
    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    init {
        options = savedState.get<String>(KEY_OPTIONS)?.let(::decode).orEmpty()
        selectedId = savedState.get<String>(KEY_SELECTED)?.takeIf { id -> options.any { it.id == id } }
            ?: options.pickDefault()?.id
        when {
            savedState.get<Boolean>(KEY_BUILDING) == true -> viewModelScope.launch { resumeBuild() }
            options.isNotEmpty() -> showOptions()
        }
    }

    fun onEvent(event: BlockSplitsEvent) {
        when (event) {
            BlockSplitsEvent.Request -> if (_uiState.value == BlockSplitsUiState.Idle) requestOptions()
            is BlockSplitsEvent.Pick -> pick(event.id)
            BlockSplitsEvent.Create -> if (_uiState.value.isIdleOptions()) build()
            BlockSplitsEvent.Retry -> retry()
        }
    }

    private fun BlockSplitsUiState.isIdleOptions() = this is BlockSplitsUiState.Options && !building

    private fun List<SplitOption>.pickDefault() = firstOrNull { it.recommended } ?: firstOrNull()

    private fun showOptions(building: Boolean = false) {
        val id = selectedId ?: return
        _uiState.value = BlockSplitsUiState.Options(options, id, building)
    }

    private fun requestOptions() {
        _uiState.value = BlockSplitsUiState.Requesting
        viewModelScope.launch {
            blocks.requestSplitOptions(today()).fold(
                onSuccess = { list ->
                    options = list
                    selectedId = list.pickDefault()?.id
                    if (selectedId == null) {
                        _uiState.value = BlockSplitsUiState.Failed(PlanFailureKind.Other, BlockSplitsStage.Options)
                    } else {
                        savedState[KEY_OPTIONS] = encode(list)
                        savedState[KEY_SELECTED] = selectedId
                        showOptions()
                    }
                },
                onFailure = {
                    _uiState.value = BlockSplitsUiState.Failed(planFailureKindOf(it), BlockSplitsStage.Options)
                },
            )
        }
    }

    private fun pick(id: String) {
        val state = _uiState.value as? BlockSplitsUiState.Options ?: return
        if (state.building || state.options.none { it.id == id }) return
        selectedId = id
        savedState[KEY_SELECTED] = id
        _uiState.value = state.copy(selectedId = id)
    }

    private fun build() {
        val split = options.firstOrNull { it.id == selectedId } ?: return
        savedState[KEY_BUILDING] = true
        showOptions(building = true)
        viewModelScope.launch {
            blocks.requestBlock(planId, today(), split).fold(
                onSuccess = {
                    savedState.remove<Boolean>(KEY_BUILDING)
                    effectChannel.send(BlockSplitsEffect.Built)
                },
                onFailure = {
                    savedState.remove<Boolean>(KEY_BUILDING)
                    _uiState.value = BlockSplitsUiState.Failed(planFailureKindOf(it), BlockSplitsStage.Build)
                },
            )
        }
    }

    /** A build was in flight when the model was recreated: the block may have been stored meanwhile. */
    private suspend fun resumeBuild() {
        _uiState.value = BlockSplitsUiState.Requesting
        savedState.remove<Boolean>(KEY_BUILDING)
        val stored = blocks.currentBlock(planId)
        _uiState.value = when {
            stored.getOrNull() != null -> {
                effectChannel.send(BlockSplitsEffect.Built)
                return
            }

            stored.isFailure ->
                BlockSplitsUiState.Failed(planFailureKindOf(stored.exceptionOrNull()), BlockSplitsStage.Build)

            else -> BlockSplitsUiState.Failed(PlanFailureKind.Interrupted, BlockSplitsStage.Build)
        }
    }

    private fun retry() {
        val state = _uiState.value as? BlockSplitsUiState.Failed ?: return
        // Without kept options a build cannot repeat; the options are asked again instead.
        if (state.stage == BlockSplitsStage.Build && selectedId != null) build() else requestOptions()
    }

    private fun encode(list: List<SplitOption>): String = Json.encodeToString(
        list.map {
            SavedOption(it.id, it.name, it.recommended, it.reason, it.sessions.map(::SavedSession))
        },
    )

    private fun decode(raw: String): List<SplitOption>? = runCatching {
        Json.decodeFromString<List<SavedOption>>(raw).map { o ->
            SplitOption(o.id, o.name, o.recommended, o.reason, o.sessions.map { SplitSession(it.name, it.focus) })
        }
    }.getOrNull()
}

@Serializable
private class SavedOption(
    val id: String,
    val name: String,
    val recommended: Boolean,
    val reason: String,
    val sessions: List<SavedSession>,
)

@Serializable
private class SavedSession(val name: String, val focus: List<String>) {
    constructor(session: SplitSession) : this(session.name, session.focus)
}
