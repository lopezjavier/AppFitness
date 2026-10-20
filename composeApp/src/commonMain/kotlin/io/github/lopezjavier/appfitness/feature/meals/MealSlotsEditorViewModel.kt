package io.github.lopezjavier.appfitness.feature.meals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.usecase.ValidateMealSlots
import io.github.lopezjavier.appfitness.domain.usecase.moveExercise
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val KEY_DRAFT = "meals_draft"
private const val KEY_BASELINE = "meals_baseline"
private const val KEY_EDITING = "meals_editing"
private const val NEW_KEY_PREFIX = "new_"

/** One row of the draft; [key] is the slot id, or `new_<n>` for a meal that does not exist yet. */
@Serializable
internal data class DraftMealSlot(val key: String, val id: String?, val code: MealCode?, val label: String)

/** The editable meal structure. Serializable so it survives process death inside the [SavedStateHandle]. */
@Serializable
internal data class MealSlotsDraft(val rows: List<DraftMealSlot> = emptyList(), val nextNewId: Int = 1)

/**
 * Meals editor (US-23, ADR-41): rename, add, delete and reorder meals. Renames, additions and order live in one draft
 * saved atomically; a delete is immediate (the server refuses the last meal and meals with entries) and also leaves
 * the baseline, so it never counts as an unsaved edit. The draft, its baseline and the row being renamed live in
 * [savedState], so edits survive a configuration change and process death. A failed save keeps every edit.
 */
@Suppress("LongParameterList", "TooManyFunctions") // One dependency per port; one small function per concern.
class MealSlotsEditorViewModel(
    private val savedState: SavedStateHandle,
    private val mealSlots: MealSlotRepository,
    private val validate: ValidateMealSlots,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val restored = savedState.get<String>(KEY_DRAFT)?.let { decode(it) }
    private var draft: MealSlotsDraft = restored ?: MealSlotsDraft()
    private var baseline: MealSlotsDraft = savedState.get<String>(KEY_BASELINE)?.let { decode(it) } ?: draft
    private var phase: Phase = if (restored == null) Phase.Loading else Phase.Editing
    private var editingKey: String? = savedState[KEY_EDITING]

    /** Keys of the rows whose delete the server refused; cleared when the row is edited. */
    private var blockedKeys: Set<String> = emptySet()
    private var online = true
    private var saving = false
    private var deleting = false
    private var discardAsked = false
    private var saveError: MealSlotsSaveError? = null

    private val _uiState = MutableStateFlow<MealSlotsUiState>(render())
    val uiState: StateFlow<MealSlotsUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<MealSlotsEffect>(Channel.BUFFERED)
    val effects: Flow<MealSlotsEffect> = effectChannel.receiveAsFlow()

    private enum class Phase { Loading, LoadError, Editing }

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect {
                online = it
                publish()
            }
        }
        load()
    }

    fun onEvent(event: MealSlotsEvent) {
        when (event) {
            MealSlotsEvent.Save -> save()

            MealSlotsEvent.Retry -> if (phase == Phase.LoadError) load()

            MealSlotsEvent.Back -> back()

            MealSlotsEvent.ConfirmDiscard -> effectChannel.trySend(MealSlotsEffect.Close)

            MealSlotsEvent.DismissDiscard -> {
                discardAsked = false
                publish()
            }

            MealSlotsEvent.StopEdit -> setEditing(null)

            is MealSlotsEvent.Edit -> startEdit(event.key)

            is MealSlotsEvent.Delete -> delete(event.key)

            else -> listEdit(event)?.let { edit(it) }
        }
    }

    /** The change an editing event makes to the draft, or null for any other event. */
    private fun listEdit(event: MealSlotsEvent): ((MealSlotsDraft) -> MealSlotsDraft)? = when (event) {
        is MealSlotsEvent.SetLabel -> rowEdit(event.key) { it.copy(label = event.label) }

        MealSlotsEvent.Add -> { draft -> draft.withNewRow() }

        is MealSlotsEvent.MoveTo -> { draft -> draft.copy(rows = moveExercise(draft.rows, event.from, event.to)) }

        is MealSlotsEvent.MoveUp ->
            { draft -> draft.copy(rows = moveExercise(draft.rows, event.index, event.index - 1)) }

        is MealSlotsEvent.MoveDown ->
            { draft -> draft.copy(rows = moveExercise(draft.rows, event.index, event.index + 1)) }

        else -> null
    }

    private fun load() {
        phase = if (restored == null) Phase.Loading else Phase.Editing
        publish()
        viewModelScope.launch {
            if (restored == null) {
                val slots = mealSlots.listAll().getOrNull()
                if (slots == null) {
                    phase = Phase.LoadError
                    publish()
                    return@launch
                }
                draft = slots.sortedBy { it.position }.toDraft()
                baseline = draft
                savedState[KEY_BASELINE] = encode(baseline)
                persist()
                phase = Phase.Editing
                publish()
            }
        }
    }

    private fun setEditing(key: String?) {
        editingKey = key
        savedState[KEY_EDITING] = key
        publish()
    }

    private fun startEdit(key: String) {
        if (!isIdle() || draft.rows.none { it.key == key }) return
        blockedKeys = blockedKeys - key
        setEditing(key)
    }

    private fun isIdle(): Boolean = phase == Phase.Editing && !saving && !deleting

    private fun canDelete(): Boolean = isIdle() && online && draft.rows.size > 1

    private fun delete(key: String) {
        val row = draft.rows.firstOrNull { it.key == key }
        if (row == null || !canDelete()) return
        val id = row.id
        if (id == null) {
            removeRow(key, fromBaseline = false)
            return
        }
        deleting = true
        saveError = null
        publish()
        viewModelScope.launch {
            mealSlots.delete(id).fold(
                onSuccess = {
                    deleting = false
                    removeRow(key, fromBaseline = true)
                },
                onFailure = {
                    deleting = false
                    when ((it as? AppException)?.error) {
                        AppError.Validation -> blockedKeys = blockedKeys + key
                        AppError.Network -> saveError = MealSlotsSaveError.Offline
                        else -> saveError = MealSlotsSaveError.Failed
                    }
                    publish()
                },
            )
        }
    }

    /** Drops the row from the draft (and the baseline when it was deleted on the server) in one step. */
    private fun removeRow(key: String, fromBaseline: Boolean) {
        draft = draft.copy(rows = draft.rows.filter { it.key != key })
        if (fromBaseline) {
            baseline = baseline.copy(rows = baseline.rows.filter { it.key != key })
            savedState[KEY_BASELINE] = encode(baseline)
        }
        blockedKeys = blockedKeys - key
        if (editingKey == key) editingKey = null
        savedState[KEY_EDITING] = editingKey
        persist()
        publish()
    }

    private fun back() {
        if (saving) return
        if (draft.rows != baseline.rows) {
            discardAsked = true
            publish()
        } else {
            effectChannel.trySend(MealSlotsEffect.Close)
        }
    }

    private fun save() {
        val state = _uiState.value as? MealSlotsUiState.Editing
        if (state == null || !state.canSave) return
        val drafts = validate(draft.toDrafts()).slots
        editingKey = null
        savedState[KEY_EDITING] = null
        saving = true
        saveError = null
        publish()
        viewModelScope.launch {
            mealSlots.save(drafts).fold(
                onSuccess = {
                    saving = false
                    baseline = draft
                    savedState[KEY_BASELINE] = encode(baseline)
                    publish()
                    effectChannel.trySend(MealSlotsEffect.Close)
                },
                onFailure = {
                    saving = false
                    saveError =
                        when ((it as? AppException)?.error) {
                            AppError.Network -> MealSlotsSaveError.Offline
                            AppError.Validation -> MealSlotsSaveError.Rejected
                            else -> MealSlotsSaveError.Failed
                        }
                    publish()
                },
            )
        }
    }

    private fun edit(transform: (MealSlotsDraft) -> MealSlotsDraft) {
        if (phase != Phase.Editing || saving || deleting) return
        val next = transform(draft)
        if (next == draft) return
        val added = next.rows.firstOrNull { row -> draft.rows.none { it.key == row.key } }
        draft = next
        if (added != null) {
            editingKey = added.key
            savedState[KEY_EDITING] = added.key
        }
        saveError = null
        persist()
        publish()
    }

    private fun rowEdit(key: String, transform: (DraftMealSlot) -> DraftMealSlot): (MealSlotsDraft) -> MealSlotsDraft =
        { current ->
            blockedKeys = blockedKeys - key
            current.copy(rows = current.rows.map { if (it.key == key) transform(it) else it })
        }

    private fun persist() {
        savedState[KEY_DRAFT] = encode(draft)
    }

    private fun publish() {
        _uiState.value = render()
    }

    private fun render(): MealSlotsUiState = when (phase) {
        Phase.Loading -> MealSlotsUiState.Loading

        Phase.LoadError -> MealSlotsUiState.LoadError

        Phase.Editing -> {
            val validation = validate(draft.toDrafts())
            val lastIndex = draft.rows.lastIndex
            val dirty = draft.rows != baseline.rows
            MealSlotsUiState.Editing(
                rows =
                draft.rows.mapIndexed { index, row ->
                    MealSlotRowUi(
                        key = row.key,
                        label = row.label,
                        error = validation.errors[index],
                        canMoveUp = index > 0,
                        canMoveDown = index < lastIndex,
                        isNew = row.id == null,
                        isEditing = row.key == editingKey,
                        deleteBlocked = row.key in blockedKeys,
                        canDelete = canDelete(),
                    )
                },
                onlyOneMeal = draft.rows.size == 1,
                isOnline = online,
                isSaving = saving,
                isDirty = dirty,
                showDiscardConfirm = discardAsked,
                saveError = saveError,
                canSave = validation.isValid && dirty && online && !saving && !deleting,
            )
        }
    }
}

private fun MealSlotsDraft.withNewRow(): MealSlotsDraft {
    val row = DraftMealSlot(NEW_KEY_PREFIX + nextNewId, id = null, code = null, label = "")
    return copy(rows = rows + row, nextNewId = nextNewId + 1)
}

private fun MealSlotsDraft.toDrafts(): List<MealSlotDraft> = rows.map { MealSlotDraft(it.id, it.code, it.label) }

private fun List<MealSlot>.toDraft() = MealSlotsDraft(
    map {
        DraftMealSlot(it.id, it.id, it.code, it.label)
    },
)

/** Ignores keys of older drafts (such as the removed `enabled`). */
private val LenientJson = Json { ignoreUnknownKeys = true }

private fun encode(draft: MealSlotsDraft): String = Json.encodeToString(MealSlotsDraft.serializer(), draft)

private fun decode(json: String): MealSlotsDraft? =
    runCatching { LenientJson.decodeFromString(MealSlotsDraft.serializer(), json) }.getOrNull()
