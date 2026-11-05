package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderInputs
import io.github.lopezjavier.appfitness.domain.coach.builder.CardioTargets
import io.github.lopezjavier.appfitness.domain.coach.builder.DaysCheck
import io.github.lopezjavier.appfitness.domain.coach.builder.FolderBlockDraft
import io.github.lopezjavier.appfitness.domain.coach.builder.FolderBlockName
import io.github.lopezjavier.appfitness.domain.coach.builder.FolderBlockOp
import io.github.lopezjavier.appfitness.domain.coach.builder.FolderBlockOps
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerContext
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerEntry
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import io.github.lopezjavier.appfitness.domain.repository.FavoriteExercisesRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildBlockUseCase
import io.github.lopezjavier.appfitness.domain.usecase.FolderBlockSource
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val KEY_FOLDER_OPS = "folder_block_ops"
private const val KEY_PICKER = "folder_block_picker"
private const val KEY_DISCARD = "folder_block_discard"

private const val PICKER_GROUP = "group"
private const val PICKER_ROUTINE = "routine"
private const val PICKER_ADD = "add"
private const val PICKER_SWAP = "swap"
private const val ROUTINE_PARTS = 2
private const val ADD_PARTS = 3

/** What the screen reads once: the folder copy, the catalogue, the history and the cardio target. */
private data class FolderInputs(
    val source: FolderBlockSource,
    val catalogue: List<CatalogExercise>,
    val builder: BuilderInputs,
    val cardioTargetMinWeek: Int,
) {
    val byId: Map<String, CatalogExercise> = catalogue.associateBy { it.id }
}

/**
 * "Desde una carpeta" (US-93): the folder is read once, the user's edits are a list of ops replayed on it
 * ([FolderBlockOps], ADR-40) and the only write is [FolderBlockEvent.Save], which creates a new folder. The source
 * folder is never written.
 */
@Suppress("TooManyFunctions", "LongParameterList") // One small handler per event; the reads the screen needs.
class FolderBlockViewModel(
    private val planId: String,
    private val folderId: String,
    private val buildBlock: BuildBlockUseCase,
    private val catalog: LoadExerciseCatalog,
    private val favourites: FavoriteExercisesRepository,
    private val plans: CoachPlanRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<FolderBlockUiState>(FolderBlockUiState.Loading)
    val uiState: StateFlow<FolderBlockUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<FolderBlockEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var inputs: FolderInputs? = null
    private var ops: List<FolderBlockOp> = emptyList()
    private var target: FolderPickerTarget? = null
    private var discardDialog = false
    private var saving = false
    private var saveError: BuilderSaveError? = null

    // The server accepts a date within one day of UTC today, so it is the UTC date.
    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    init {
        load()
    }

    @Suppress("CyclomaticComplexMethod") // One branch per event.
    fun onEvent(event: FolderBlockEvent) {
        when (event) {
            FolderBlockEvent.OpenAdd -> openAdd()
            is FolderBlockEvent.ChooseGroup -> chooseGroup(event.group)
            is FolderBlockEvent.ChooseRoutine -> chooseRoutine(event.routineIndex)
            is FolderBlockEvent.OpenSwap -> openSwap(event.routineIndex, event.exerciseIndex)
            FolderBlockEvent.ClosePicker -> setTarget(null)
            is FolderBlockEvent.PickExercise -> pickExercise(event.exerciseId)
            is FolderBlockEvent.RemoveAdded -> edit(FolderBlockOp.Remove(event.routineIndex, event.exerciseIndex))
            is FolderBlockEvent.ApplyRepFix -> applyRepFix(event.routineIndex, event.exerciseIndex)
            FolderBlockEvent.Save -> save()
            FolderBlockEvent.Back -> back()
            FolderBlockEvent.ConfirmDiscard -> effectChannel.trySend(FolderBlockEffect.Close)
            FolderBlockEvent.CancelDiscard -> update { discardDialog = false }
            FolderBlockEvent.Retry -> retry()
        }
    }

    // region loading and restoring

    private fun retry() {
        val state = _uiState.value
        if (state is FolderBlockUiState.Failed || state is FolderBlockUiState.Refused) {
            ops = emptyList()
            load()
        }
    }

    private fun load() {
        _uiState.value = FolderBlockUiState.Loading
        viewModelScope.launch {
            readInputs().onSuccess {
                inputs = it
                restore(it)
            }.onFailure {
                _uiState.value = FolderBlockUiState.Failed(offline = it.isOfflineFailure())
            }
        }
    }

    @Suppress("ReturnCount") // One early exit per failed read.
    private suspend fun readInputs(): Result<FolderInputs> {
        val source = buildBlock.loadFolder(folderId).getOrElse { return Result.failure(it) }
        val exercises = catalog().getOrElse { return Result.failure(it) }
        favourites.refresh()
        val built = buildBlock.loadInputs(today()).getOrElse { return Result.failure(it) }
        val plan = plans.currentPlan().getOrElse { return Result.failure(it) }
        return Result.success(FolderInputs(source, exercises, built, CardioTargets.forPlan(plan)))
    }

    private fun restore(data: FolderInputs) {
        val base = data.source.draft
        when {
            base.routines.isEmpty() -> {
                _uiState.value = FolderBlockUiState.Empty(data.source.folderName)
                clearSaved()
            }

            base.status().days != DaysCheck.Ok -> {
                _uiState.value =
                    FolderBlockUiState.Refused(data.source.folderName, base.status().days, base.daysPerWeek)
                clearSaved()
            }

            else -> {
                ops = savedState.get<String>(KEY_FOLDER_OPS)?.let(FolderBlockOps::decode).orEmpty()
                target = decodeTarget(savedState.get<String>(KEY_PICKER))?.takeIf { validTarget(it, draft()) }
                discardDialog = savedState.get<Boolean>(KEY_DISCARD) == true
                render()
            }
        }
    }

    private fun clearSaved() {
        savedState.remove<String>(KEY_FOLDER_OPS)
        savedState.remove<String>(KEY_PICKER)
        savedState.remove<Boolean>(KEY_DISCARD)
    }

    // endregion

    // region state

    private fun draft(): FolderBlockDraft {
        val data = checkNotNull(inputs)
        return FolderBlockOps.replay(data.source.draft, ops, data.catalogue, data.builder.history)
    }

    private fun content(): FolderBlockUiState.Content? = _uiState.value as? FolderBlockUiState.Content

    private fun render() {
        val data = checkNotNull(inputs)
        val draft = draft()
        val current = target?.takeIf { validTarget(it, draft) }
        _uiState.value = FolderBlockUiState.Content(
            folderName = data.source.folderName,
            name = FolderBlockName.unique(data.source.folderName, data.source.existingFolderNames),
            draft = draft,
            status = draft.status(),
            pickerTarget = current,
            pickerEntries = current?.let { entriesFor(it, draft, data) }.orEmpty(),
            exercises = data.byId,
            favouriteIds = favourites.favoriteIds.value,
            saving = saving,
            saveError = saveError,
            discardDialog = discardDialog,
            edited = ops.isNotEmpty(),
        )
        persist(current)
    }

    private fun persist(current: FolderPickerTarget?) {
        savedState[KEY_FOLDER_OPS] = FolderBlockOps.encode(ops)
        if (current == null) savedState.remove<String>(KEY_PICKER) else savedState[KEY_PICKER] = encodeTarget(current)
        if (discardDialog) savedState[KEY_DISCARD] = true else savedState.remove<Boolean>(KEY_DISCARD)
    }

    private fun update(change: () -> Unit) {
        if (content() == null) return
        change()
        render()
    }

    private fun setTarget(next: FolderPickerTarget?) = update {
        if (!saving) target = next
    }

    // endregion

    // region picker

    private fun validTarget(t: FolderPickerTarget, draft: FolderBlockDraft): Boolean = when (t) {
        FolderPickerTarget.ChooseGroup -> draft.addableGroups().isNotEmpty()

        is FolderPickerTarget.ChooseRoutine -> draft.addableGroups().any { it.group == t.group }

        is FolderPickerTarget.Add -> draft.addableGroups().any { it.group == t.group } &&
            draft.routines.getOrNull(t.routineIndex)?.lifting == true

        is FolderPickerTarget.Swap ->
            draft.routines.getOrNull(t.routineIndex)?.exercises?.getOrNull(t.exerciseIndex) != null
    }

    private fun entriesFor(t: FolderPickerTarget, draft: FolderBlockDraft, data: FolderInputs): List<PickerEntry> {
        val slot = when (t) {
            is FolderPickerTarget.Add -> draft.pickerSlot(t.group, t.routineIndex)
            is FolderPickerTarget.Swap -> draft.swapSlot(t.routineIndex, t.exerciseIndex)
            else -> null
        }
        val ctx = PickerContext(draft.equipment, data.builder.history, favourites.favoriteIds.value)
        return slot?.let { buildBlock.eligible(it, data.catalogue, ctx, usedIn(t, draft)) }.orEmpty()
    }

    /** The ids already in the target routine (a swap does not count the row being swapped). */
    private fun usedIn(t: FolderPickerTarget, draft: FolderBlockDraft): Set<String> = when (t) {
        is FolderPickerTarget.Add -> draft.routines[t.routineIndex].exercises.map { it.id }.toSet()

        is FolderPickerTarget.Swap -> draft.routines[t.routineIndex].exercises
            .filterIndexed { i, _ -> i != t.exerciseIndex }.map { it.id }.toSet()

        else -> emptySet()
    }

    private fun openAdd() {
        val state = content() ?: return
        if (state.saving) return
        val groups = state.addableGroups
        when {
            groups.isEmpty() -> Unit
            groups.size == 1 -> chooseGroup(groups.single().group)
            else -> setTarget(FolderPickerTarget.ChooseGroup)
        }
    }

    private fun chooseGroup(group: MuscleGroup) {
        val state = content() ?: return
        if (state.saving || state.addableGroups.none { it.group == group }) return
        val routines = state.liftingRoutines
        when {
            routines.isEmpty() -> Unit
            routines.size == 1 -> setTarget(FolderPickerTarget.Add(group, routines.single().first))
            else -> setTarget(FolderPickerTarget.ChooseRoutine(group))
        }
    }

    private fun chooseRoutine(routineIndex: Int) {
        val state = content()
        val chosen = state?.pickerTarget as? FolderPickerTarget.ChooseRoutine
        if (chosen != null && state.liftingRoutines.any { it.first == routineIndex }) {
            setTarget(FolderPickerTarget.Add(chosen.group, routineIndex))
        }
    }

    private fun openSwap(routineIndex: Int, exerciseIndex: Int) {
        if (content() == null) return
        setTarget(FolderPickerTarget.Swap(routineIndex, exerciseIndex))
    }

    @Suppress("ReturnCount") // Guards for a closed picker, a save in flight or a disabled row.
    private fun pickExercise(exerciseId: String) {
        val state = content() ?: return
        if (state.saving) return
        val entry = state.pickerEntries.firstOrNull { it.exercise.id == exerciseId } ?: return
        if (entry.disabled) return
        val op = when (val t = state.pickerTarget) {
            is FolderPickerTarget.Add -> FolderBlockOp.Add(t.routineIndex, exerciseId)
            is FolderPickerTarget.Swap -> FolderBlockOp.Swap(t.routineIndex, t.exerciseIndex, exerciseId)
            else -> return
        }
        target = null
        edit(op)
    }

    // endregion

    // region edits and save

    /** Appends [op] when it changes the copy; the picker follows and a change clears a failed save. */
    private fun edit(op: FolderBlockOp) {
        val state = content() ?: return
        if (state.saving) return
        val data = checkNotNull(inputs)
        val before = draft()
        val after = FolderBlockOps.apply(before, op, data.byId, data.builder.history)
        if (after != before) {
            ops = ops + op
            saveError = null
            effectChannel.trySend(FolderBlockEffect.Confirm)
        }
        render()
    }

    private fun applyRepFix(routineIndex: Int, exerciseIndex: Int) =
        edit(FolderBlockOp.RepFix(routineIndex, exerciseIndex))

    @Suppress("ReturnCount") // Guards for a state that cannot save.
    private fun save() {
        val state = content() ?: return
        if (!state.canSave) return
        val data = checkNotNull(inputs)
        saving = true
        saveError = null
        render()
        viewModelScope.launch {
            val result = buildBlock.saveFolder(
                planId = planId,
                draft = state.draft,
                sessionMinutes = data.source.sessionMinutes,
                cardioTargetMinWeek = data.cardioTargetMinWeek,
                inputs = data.builder,
                folderName = state.name,
            )
            if (result == null) {
                saving = false
                render()
                return@launch
            }
            // `saving` stays on after a success: the screen closes on `Saved`, and a second Save must not repeat it.
            result.onSuccess {
                effectChannel.send(FolderBlockEffect.Confirm)
                effectChannel.send(FolderBlockEffect.Saved)
            }.onFailure { failure ->
                saving = false
                saveError = failure.toBuilderSaveError()
                render()
                effectChannel.send(FolderBlockEffect.Reject)
            }
        }
    }

    private fun back() {
        val state = content()
        when {
            state == null -> effectChannel.trySend(FolderBlockEffect.Close)
            state.discardDialog -> update { discardDialog = false }
            state.pickerTarget != null -> update { target = null }
            state.edited -> update { discardDialog = true }
            else -> effectChannel.trySend(FolderBlockEffect.Close)
        }
    }

    // endregion

    // region picker codec

    private fun encodeTarget(t: FolderPickerTarget): String = when (t) {
        FolderPickerTarget.ChooseGroup -> PICKER_GROUP
        is FolderPickerTarget.ChooseRoutine -> "$PICKER_ROUTINE:${t.group.name}"
        is FolderPickerTarget.Add -> "$PICKER_ADD:${t.group.name}:${t.routineIndex}"
        is FolderPickerTarget.Swap -> "$PICKER_SWAP:${t.routineIndex}:${t.exerciseIndex}"
    }

    @Suppress("CyclomaticComplexMethod") // One branch per target kind.
    private fun decodeTarget(raw: String?): FolderPickerTarget? {
        val p = raw?.split(":") ?: return null
        return when (p[0]) {
            PICKER_GROUP -> FolderPickerTarget.ChooseGroup

            PICKER_ROUTINE -> groupOf(p.getOrNull(1))?.takeIf { p.size == ROUTINE_PARTS }
                ?.let { FolderPickerTarget.ChooseRoutine(it) }

            PICKER_ADD -> {
                val ri = p.getOrNull(2)?.toIntOrNull()
                val group = groupOf(p.getOrNull(1))
                if (p.size == ADD_PARTS && ri != null && group != null) FolderPickerTarget.Add(group, ri) else null
            }

            PICKER_SWAP -> {
                val ri = p.getOrNull(1)?.toIntOrNull()
                val ei = p.getOrNull(2)?.toIntOrNull()
                if (p.size == ADD_PARTS && ri != null && ei != null) FolderPickerTarget.Swap(ri, ei) else null
            }

            else -> null
        }
    }

    private fun groupOf(name: String?): MuscleGroup? = MuscleGroup.entries.firstOrNull { it.name == name }

    // endregion
}
