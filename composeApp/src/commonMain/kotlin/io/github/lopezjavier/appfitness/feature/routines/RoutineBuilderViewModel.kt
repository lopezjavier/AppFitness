package io.github.lopezjavier.appfitness.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.groupWithNext
import io.github.lopezjavier.appfitness.domain.usecase.moveExercise
import io.github.lopezjavier.appfitness.domain.usecase.normalizeGroups
import io.github.lopezjavier.appfitness.domain.usecase.supersetLabels
import io.github.lopezjavier.appfitness.domain.usecase.ungroup
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val KEY_DRAFT = "builder_draft"
private const val KEY_BASELINE = "builder_baseline"

/** One row of the draft; [group] is the superset group value (null = standalone). */
@Serializable
internal data class DraftRow(
    val rowId: Long,
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val group: Int?,
)

/** The editable routine. Serializable so it survives process death inside the [SavedStateHandle]. */
@Serializable
internal data class RoutineDraft(
    val name: String = "",
    val notes: String? = null,
    val rows: List<DraftRow> = emptyList(),
    val nextRowId: Long = 0,
)

/**
 * Routine builder (US-10): name, exercises with sets, reps and rest, reorder (drag or move up/down) and supersets.
 * The whole draft lives in [savedState] (as JSON), so it survives a configuration change and process death. With a
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

            else -> {
                // An exercise created in the picker after this screen opened is not in the loaded catalog yet.
                if (event is RoutineBuilderEvent.AddExercise && event.exerciseId !in catalog) loadNames()
                listEdit(event)?.let { edit(it) }
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

        is RoutineBuilderEvent.GroupWithNext ->
            { draft -> draft.withGroups(groupWithNext(draft.rows.map(DraftRow::group), event.index)) }

        is RoutineBuilderEvent.Ungroup ->
            { draft -> draft.withGroups(ungroup(draft.rows.map(DraftRow::group), event.index)) }

        is RoutineBuilderEvent.Remove ->
            { draft -> draft.copy(rows = draft.rows.filterIndexed { i, _ -> i != event.index }).renormalized() }

        else -> null
    }

    /** The row index and change of a stepper event, or null for any other event. */
    private fun targetEdit(event: RoutineBuilderEvent): Pair<Int, (DraftRow) -> DraftRow>? = when (event) {
        is RoutineBuilderEvent.StepSets ->
            event.index to { row ->
                row.copy(sets = (row.sets + event.delta).coerceIn(RoutineBounds.SETS_MIN, RoutineBounds.SETS_MAX))
            }

        is RoutineBuilderEvent.StepReps ->
            event.index to { row ->
                row.copy(reps = (row.reps + event.delta).coerceIn(RoutineBounds.REPS_MIN, RoutineBounds.REPS_MAX))
            }

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
                        sets = row.sets,
                        reps = row.reps,
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
            )
        }
    }
}

private fun RoutineDraft.withNewRow(exerciseId: String): RoutineDraft = copy(
    rows =
    rows +
        DraftRow(
            rowId = nextRowId,
            exerciseId = exerciseId,
            sets = RoutineBounds.DEFAULT_SETS,
            reps = RoutineBounds.DEFAULT_REPS,
            restSeconds = RoutineBounds.DEFAULT_REST_SECONDS,
            group = null,
        ),
    nextRowId = nextRowId + 1,
)

private fun RoutineDraft.movedRow(from: Int, to: Int): RoutineDraft =
    copy(rows = moveExercise(rows, from, to)).renormalized()

private fun RoutineDraft.withGroups(groups: List<Int?>): RoutineDraft =
    copy(rows = rows.mapIndexed { index, row -> row.copy(group = groups[index]) })

/** Clears and renumbers the superset groups after rows moved or went away. */
private fun RoutineDraft.renormalized(): RoutineDraft = withGroups(normalizeGroups(rows.map(DraftRow::group)))

private fun RoutineDraft.toRoutine(id: String?) = Routine(
    id = id,
    name = name.trim(),
    notes = notes,
    archived = false,
    exercises =
    rows.mapIndexed { index, row ->
        RoutineExercise(row.exerciseId, index, row.group, row.sets, row.reps, row.restSeconds)
    },
)

private fun Routine.toDraft(): RoutineDraft {
    val groups = normalizeGroups(exercises.sortedBy { it.position }.map { it.supersetGroup })
    val rows =
        exercises.sortedBy { it.position }.mapIndexed { index, exercise ->
            DraftRow(
                rowId = index.toLong(),
                exerciseId = exercise.exerciseId,
                sets = exercise.targetSets,
                reps = exercise.targetReps,
                restSeconds = exercise.restSeconds,
                group = groups[index],
            )
        }
    return RoutineDraft(name = name, notes = notes, rows = rows, nextRowId = rows.size.toLong())
}

private fun encode(draft: RoutineDraft): String = Json.encodeToString(RoutineDraft.serializer(), draft)

private fun decode(json: String): RoutineDraft? =
    runCatching { Json.decodeFromString(RoutineDraft.serializer(), json) }.getOrNull()
