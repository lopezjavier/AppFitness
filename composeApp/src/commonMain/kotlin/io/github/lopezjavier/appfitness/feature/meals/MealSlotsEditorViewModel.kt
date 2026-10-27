package io.github.lopezjavier.appfitness.feature.meals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealSlotBounds
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.usecase.ValidateMealSlots
import io.github.lopezjavier.appfitness.domain.usecase.moveExercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private const val KEY_DRAFT = "meals_draft"
private const val KEY_BASELINE = "meals_baseline"
private const val KEY_REMOVED = "meals_removed"
private const val KEY_SHEET = "meals_sheet"
private const val SHEET_CREATE = "create"
private const val SHEET_RENAME_PREFIX = "rename:"
private const val NEW_KEY_PREFIX = "new_"

/** One row of the draft; [key] is the slot id, or `new_<n>` for a meal that does not exist yet. */
@Serializable
internal data class DraftMealSlot(val key: String, val id: String?, val code: MealCode?, val label: String)

/** The editable meal structure. Serializable so it survives process death inside the [SavedStateHandle]. */
@Serializable
internal data class MealSlotsDraft(val rows: List<DraftMealSlot> = emptyList(), val nextNewId: Int = 1)

/**
 * Meals editor (US-23, US-56): rename, add, remove and reorder meals. Nothing reaches the server until [save]: renames,
 * additions, order and removals (staged in [removedIds]) are sent together in one atomic call. Create and rename go
 * through the Name sheet. The draft, its baseline, the staged removals and the open sheet live in [savedState], so
 * edits survive a configuration change and process death. A failed save keeps every edit.
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
    private var removedIds: List<String> = savedState.get<String>(KEY_REMOVED)?.let { decodeIds(it) } ?: emptyList()
    private var sheet: String? = savedState[KEY_SHEET]
    private var phase: Phase = if (restored == null) Phase.Loading else Phase.Editing

    /** Ids of the meals with logged entries; a failed load is not fatal (the hints just do not show). */
    private var inUseIds: Set<String> = emptySet()
    private var online = true
    private var saving = false
    private var saved = false
    private var saveError: MealSlotsSaveError? = null

    private val _uiState = MutableStateFlow<MealSlotsUiState>(render())
    val uiState: StateFlow<MealSlotsUiState> = _uiState.asStateFlow()

    private enum class Phase { Loading, LoadError, Editing }

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect {
                online = it
                publish()
            }
        }
        load()
        loadInUse()
    }

    fun onEvent(event: MealSlotsEvent) {
        when (event) {
            MealSlotsEvent.Save -> save()

            MealSlotsEvent.Retry -> if (phase == Phase.LoadError) load()

            MealSlotsEvent.Add -> openSheet(SHEET_CREATE)

            is MealSlotsEvent.Rename -> if (draft.rows.any { it.key == event.key }) {
                openSheet(SHEET_RENAME_PREFIX + event.key)
            }

            is MealSlotsEvent.SubmitName -> submitName(event.name)

            MealSlotsEvent.DismissSheet -> setSheet(null)

            is MealSlotsEvent.Delete -> delete(event.key)

            is MealSlotsEvent.MoveTo -> edit { it.copy(rows = moveExercise(it.rows, event.from, event.to)) }

            is MealSlotsEvent.MoveUp -> edit { it.copy(rows = moveExercise(it.rows, event.index, event.index - 1)) }

            is MealSlotsEvent.MoveDown -> edit { it.copy(rows = moveExercise(it.rows, event.index, event.index + 1)) }
        }
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

    private fun loadInUse() {
        viewModelScope.launch {
            mealSlots.listInUse().onSuccess {
                inUseIds = it
                publish()
            }
        }
    }

    private fun isIdle(): Boolean = phase == Phase.Editing && !saving

    private fun openSheet(value: String) {
        if (isIdle()) setSheet(value)
    }

    private fun setSheet(value: String?) {
        sheet = value
        savedState[KEY_SHEET] = value
        publish()
    }

    private fun submitName(raw: String) {
        val target = sheet
        val name = raw.trim()
        if (!isIdle() || target == null) return
        val renameKey = target.removePrefix(SHEET_RENAME_PREFIX).takeIf { target.startsWith(SHEET_RENAME_PREFIX) }
        val others = draft.rows.filter { it.key != renameKey }.map { it.label }
        val valid = name.isNotEmpty() &&
            name.length <= MealSlotBounds.LABEL_MAX_LENGTH &&
            !ValidateMealSlots.isDuplicate(name, others)
        if (!valid) return
        draft = if (renameKey == null) {
            draft.withNewRow(name)
        } else {
            draft.copy(rows = draft.rows.map { if (it.key == renameKey) it.copy(label = name) else it })
        }
        saveError = null
        persist()
        setSheet(null)
    }

    private fun isProtected(row: DraftMealSlot): Boolean = row.id in inUseIds || draft.rows.size <= 1

    private fun delete(key: String) {
        val row = draft.rows.firstOrNull { it.key == key }
        if (row == null || !isIdle() || isProtected(row)) return
        draft = draft.copy(rows = draft.rows.filter { it.key != key })
        row.id?.let {
            removedIds = removedIds + it
            savedState[KEY_REMOVED] = encodeIds(removedIds)
        }
        saveError = null
        persist()
        publish()
    }

    private fun edit(transform: (MealSlotsDraft) -> MealSlotsDraft) {
        if (!isIdle()) return
        val next = transform(draft)
        if (next == draft) return
        draft = next
        saveError = null
        persist()
        publish()
    }

    private fun save() {
        val state = _uiState.value as? MealSlotsUiState.Editing
        if (state == null || !state.canSave) return
        val drafts = validate(draft.toDrafts()).slots
        val removed = removedIds
        saving = true
        saveError = null
        publish()
        viewModelScope.launch {
            mealSlots.saveStructure(drafts, removed).fold(
                onSuccess = {
                    saving = false
                    saved = true
                    baseline = draft
                    removedIds = emptyList()
                    savedState[KEY_BASELINE] = encode(baseline)
                    savedState[KEY_REMOVED] = encodeIds(removedIds)
                    publish()
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
                    if (saveError == MealSlotsSaveError.Rejected) loadInUse()
                },
            )
        }
    }

    private fun persist() {
        savedState[KEY_DRAFT] = encode(draft)
    }

    private fun publish() {
        _uiState.value = render()
    }

    private fun isDirty(): Boolean = draft.rows != baseline.rows || removedIds.isNotEmpty()

    private fun renderSheet(): MealNameSheet? = when (val value = sheet) {
        null -> null

        SHEET_CREATE -> MealNameSheet.Create

        else -> draft.rows.firstOrNull { it.key == value.removePrefix(SHEET_RENAME_PREFIX) }
            ?.let { MealNameSheet.Rename(it.key, it.label) }
    }

    private fun render(): MealSlotsUiState = when (phase) {
        Phase.Loading -> MealSlotsUiState.Loading

        Phase.LoadError -> MealSlotsUiState.LoadError

        Phase.Editing -> {
            val validation = validate(draft.toDrafts())
            val lastIndex = draft.rows.lastIndex
            val dirty = isDirty()
            MealSlotsUiState.Editing(
                rows =
                draft.rows.mapIndexed { index, row ->
                    val inUse = row.id != null && row.id in inUseIds
                    val isLast = draft.rows.size == 1
                    MealSlotRowUi(
                        key = row.key,
                        label = row.label,
                        canMoveUp = index > 0,
                        canMoveDown = index < lastIndex,
                        isNew = row.id == null,
                        inUse = inUse,
                        isLastMeal = isLast,
                        canDelete = isIdle() && !inUse && !isLast,
                    )
                },
                sheet = renderSheet(),
                isOnline = online,
                isSaving = saving,
                isDirty = dirty,
                saveError = saveError,
                canSave = validation.isValid && dirty && online && !saving,
                saved = saved,
                canNavigateToDefaults = !dirty,
            )
        }
    }
}

private fun MealSlotsDraft.withNewRow(label: String): MealSlotsDraft {
    val row = DraftMealSlot(NEW_KEY_PREFIX + nextNewId, id = null, code = null, label = label)
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

private fun encodeIds(ids: List<String>): String = Json.encodeToString(ListSerializer(String.serializer()), ids)

private fun decodeIds(json: String): List<String>? =
    runCatching { Json.decodeFromString(ListSerializer(String.serializer()), json) }.getOrNull()
