package io.github.lopezjavier.appfitness.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.groupWithNext
import io.github.lopezjavier.appfitness.domain.usecase.supersetLabels
import io.github.lopezjavier.appfitness.domain.usecase.ungroup
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val KEY_DRAFT = "builder_draft"
private const val KEY_BASELINE = "builder_baseline"
private const val KEY_PICKER = "builder_type_picker"

/**
 * Routine builder (US-10, US-29): name, exercises with per-set weight, reps and type, rest, reorder (drag or move
 * up/down) and supersets. Supersets keep an equal set count (ADR-38): adding or removing a set applies to every
 * member, and grouping makes the following members adopt the count of the row grouped. The whole draft lives in
 * [savedState] (as JSON), so it survives a configuration change and process death. With a
 * [routineId] it loads that routine unless a draft was already restored. Saving is one atomic call and a failure
 * keeps every edit.
 */
@Suppress("TooManyFunctions") // One small function per builder event.
class RoutineBuilderViewModel(
    private val routineId: String?,
    private val savedState: SavedStateHandle,
    private val repository: RoutineRepository,
    private val loadCatalog: LoadExerciseCatalog,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val restored = savedState.get<String>(KEY_DRAFT)?.let { decode(it) }
    private var draft: RoutineDraft = restored ?: RoutineDraft()
    private var baseline: RoutineDraft =
        savedState.get<String>(KEY_BASELINE)?.let { decode(it) }
            ?: (if (routineId == null || restored == null) RoutineDraft() else restored)
    private var phase: Phase = if (routineId != null && restored == null) Phase.Loading else Phase.Editing
    private var catalog: Map<String, CatalogExercise> = emptyMap()
    private var online = true
    private var saving = false
    private var discardAsked = false

    /** The set whose type picker is open as (rowId, setId); restored with the draft, never part of it. */
    private var picker: Pair<Long, Long>? =
        savedState.get<String>(KEY_PICKER)?.split(":")?.takeIf { it.size == 2 }?.let {
            val rowId = it[0].toLongOrNull()
            val setId = it[1].toLongOrNull()
            if (rowId != null && setId != null) rowId to setId else null
        }

    init {
        // A new routine is compared against the empty draft; without a stored baseline a restored draft would
        // become its own baseline and stop counting as unsaved.
        if (routineId == null && savedState.get<String>(KEY_BASELINE) == null) {
            savedState[KEY_BASELINE] = encode(RoutineDraft())
        }
    }

    private val _uiState = MutableStateFlow<RoutineBuilderUiState>(render())
    val uiState: StateFlow<RoutineBuilderUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<RoutineBuilderEffect>(Channel.BUFFERED)
    val effects: Flow<RoutineBuilderEffect> = effectChannel.receiveAsFlow()

    private enum class Phase { Loading, LoadError, Editing }

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect {
                online = it
                publish()
            }
        }
        if (phase == Phase.Loading) loadRoutine() else loadNames()
    }

    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the events.
    fun onEvent(event: RoutineBuilderEvent) {
        when (event) {
            RoutineBuilderEvent.Save -> save()

            RoutineBuilderEvent.Retry -> if (phase == Phase.LoadError) loadRoutine()

            RoutineBuilderEvent.Back -> back()

            RoutineBuilderEvent.ConfirmDiscard -> effectChannel.trySend(RoutineBuilderEffect.Close)

            RoutineBuilderEvent.DismissDiscard -> {
                discardAsked = false
                publish()
            }

            is RoutineBuilderEvent.OpenTypePicker -> openPicker(event.index, event.setIndex)

            RoutineBuilderEvent.CloseTypePicker -> setPicker(null)

            else -> {
                // An exercise created in the picker after this screen opened is not in the loaded catalog yet.
                if (event is RoutineBuilderEvent.AddExercise && event.exerciseId !in catalog) loadNames()
                listEdit(event)?.let { edit(it) }
                setsEdit(event)?.let { edit(it) }
                targetEdit(event)?.let { (index, change) -> editRow(index, change) }
            }
        }
    }

    /** The change an event makes to the list (name, rows, order, supersets), or null for any other event. */
    private fun listEdit(event: RoutineBuilderEvent): ((RoutineDraft) -> RoutineDraft)? = when (event) {
        is RoutineBuilderEvent.SetName -> { draft -> draft.copy(name = event.name.take(RoutineBounds.NAME_MAX_LENGTH)) }

        is RoutineBuilderEvent.AddExercise -> { draft -> draft.withNewRow(event.exerciseId) }

        is RoutineBuilderEvent.MoveTo -> { draft -> draft.movedRow(event.from, event.to) }

        is RoutineBuilderEvent.MoveUp -> { draft -> draft.movedRow(event.index, event.index - 1) }

        is RoutineBuilderEvent.MoveDown -> { draft -> draft.movedRow(event.index, event.index + 1) }

        is RoutineBuilderEvent.GroupWithNext -> { draft ->
            draft.withGroups(groupWithNext(draft.rows.map(DraftRow::group), event.index)).leveledTo(event.index)
        }

        is RoutineBuilderEvent.Ungroup ->
            { draft -> draft.withGroups(ungroup(draft.rows.map(DraftRow::group), event.index)) }

        is RoutineBuilderEvent.Remove ->
            { draft -> draft.copy(rows = draft.rows.filterIndexed { i, _ -> i != event.index }).renormalized() }

        else -> null
    }

    /** The change an event makes to the planned sets of a row, or null for any other event. */
    private fun setsEdit(event: RoutineBuilderEvent): ((RoutineDraft) -> RoutineDraft)? = when (event) {
        is RoutineBuilderEvent.AddSet -> { draft -> draft.withAddedSet(event.index) }

        is RoutineBuilderEvent.RemoveSet -> { draft -> draft.withoutSet(event.index, event.setIndex) }

        is RoutineBuilderEvent.SetWeight -> { draft ->
            draft.withSetEdit(event.index, event.setIndex) { it.copy(weight = event.text.take(WEIGHT_MAX_CHARS)) }
        }

        is RoutineBuilderEvent.SetReps -> { draft ->
            draft.withSetEdit(event.index, event.setIndex) { it.copy(reps = event.text.take(REPS_MAX_CHARS)) }
        }

        is RoutineBuilderEvent.ChangeSetType -> { draft ->
            setPicker(null)
            draft.withSetEdit(event.index, event.setIndex) { it.copy(type = event.type) }
        }

        else -> null
    }

    /** The row index and change of the rest stepper event, or null for any other event. */
    private fun targetEdit(event: RoutineBuilderEvent): Pair<Int, (DraftRow) -> DraftRow>? = when (event) {
        is RoutineBuilderEvent.StepRest ->
            event.index to { row ->
                val rest = row.restSeconds + event.delta * RoutineBounds.REST_STEP_SECONDS
                row.copy(restSeconds = rest.coerceIn(RoutineBounds.REST_MIN_SECONDS, RoutineBounds.REST_MAX_SECONDS))
            }

        else -> null
    }

    private fun loadRoutine() {
        val id = routineId ?: return
        phase = Phase.Loading
        publish()
        viewModelScope.launch {
            val routine = repository.get(id).getOrNull()
            val names = loadCatalog().getOrNull()
            if (routine == null || names == null) {
                phase = Phase.LoadError
            } else {
                catalog = names.associateBy { it.id }
                draft = routine.toDraft()
                baseline = draft
                savedState[KEY_BASELINE] = encode(baseline)
                persist()
                phase = Phase.Editing
            }
            publish()
        }
    }

    /** Fills in the exercise names; a failure just leaves the rows unnamed. */
    private fun loadNames() {
        viewModelScope.launch {
            loadCatalog().onSuccess { list ->
                catalog = list.associateBy { it.id }
                publish()
            }
        }
    }

    private fun back() {
        if (saving) return
        if (draft != baseline) {
            discardAsked = true
            publish()
        } else {
            effectChannel.trySend(RoutineBuilderEffect.Close)
        }
    }

    private fun save() {
        val state = _uiState.value as? RoutineBuilderUiState.Editing
        if (state == null || !state.canSave) return
        saving = true
        publish()
        val routine = draft.toRoutine(routineId)
        viewModelScope.launch {
            repository.save(routine).fold(
                onSuccess = {
                    saving = false
                    baseline = draft
                    publish()
                    effectChannel.trySend(RoutineBuilderEffect.Close)
                },
                onFailure = {
                    saving = false
                    publish()
                    effectChannel.trySend(
                        RoutineBuilderEffect.SaveFailed(
                            offline =
                            (it as? AppException)?.error == AppError.Network,
                        ),
                    )
                },
            )
        }
    }

    private fun edit(transform: (RoutineDraft) -> RoutineDraft) {
        if (phase != Phase.Editing || saving) return
        draft = transform(draft)
        persist()
        publish()
    }

    private fun editRow(index: Int, transform: (DraftRow) -> DraftRow) = edit { current ->
        current.copy(rows = current.rows.mapIndexed { i, row -> if (i == index) transform(row) else row })
    }

    private fun persist() {
        savedState[KEY_DRAFT] = encode(draft)
    }

    private fun openPicker(rowIndex: Int, setIndex: Int) {
        val row = draft.rows.getOrNull(rowIndex)
        val set = row?.sets?.getOrNull(setIndex)
        if (phase == Phase.Editing && row != null && set != null) setPicker(row.rowId to set.setId)
    }

    private fun setPicker(target: Pair<Long, Long>?) {
        picker = target
        savedState[KEY_PICKER] = target?.let { "${it.first}:${it.second}" }
        publish()
    }

    private fun renderPicker(): TypePickerUi? {
        val (rowId, setId) = picker ?: return null
        val rowIndex = draft.rows.indexOfFirst { it.rowId == rowId }
        val set = draft.rows.getOrNull(rowIndex)?.sets?.withIndex()?.firstOrNull { it.value.setId == setId }
        return set?.let { TypePickerUi(rowIndex, it.index, it.value.type) }
    }

    private fun publish() {
        _uiState.value = render()
    }

    private fun render(): RoutineBuilderUiState = when (phase) {
        Phase.Loading -> RoutineBuilderUiState.Loading

        Phase.LoadError -> RoutineBuilderUiState.LoadError

        Phase.Editing -> {
            val labels = supersetLabels(draft.rows.map(DraftRow::group))
            val lastIndex = draft.rows.lastIndex
            RoutineBuilderUiState.Editing(
                isEditMode = routineId != null,
                name = draft.name,
                rows =
                draft.rows.mapIndexed { index, row ->
                    val exercise = catalog[row.exerciseId]
                    BuilderRowUi(
                        rowId = row.rowId,
                        exerciseId = row.exerciseId,
                        name = exercise?.name,
                        imageKey = exercise?.takeIf { it.hasMedia }?.exercise?.key,
                        plannedSets = row.sets.map { it.toUi() },
                        restSeconds = row.restSeconds,
                        supersetLabel = labels[index],
                        canMoveUp = index > 0,
                        canMoveDown = index < lastIndex,
                        canGroupWithNext = index < lastIndex,
                    )
                },
                isOnline = online,
                isSaving = saving,
                isDirty = draft != baseline,
                showDiscardConfirm = discardAsked,
                typePicker = renderPicker(),
            )
        }
    }
}
