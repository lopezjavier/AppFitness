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
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.usecase.ValidateMealSlots
import io.github.lopezjavier.appfitness.domain.usecase.moveExercise
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val KEY_DRAFT = "meals_draft"
private const val KEY_BASELINE = "meals_baseline"
private const val NEW_KEY_PREFIX = "new_"

/** One row of the draft; [key] is the slot id, or `new_<n>` for a meal that does not exist yet. */
@Serializable
internal data class DraftMealSlot(
    val key: String,
    val id: String?,
    val code: MealCode?,
    val label: String,
    val enabled: Boolean,
)

/** The editable meal structure. Serializable so it survives process death inside the [SavedStateHandle]. */
@Serializable
internal data class MealSlotsDraft(val rows: List<DraftMealSlot> = emptyList(), val nextNewId: Int = 1)

/**
 * Meals editor (US-23): rename, add, enable/disable and reorder in one draft saved atomically. [viewedDay] is the
 * Diary day the user came from: a meal with entries on it cannot be disabled (the server enforces it too); when those
 * entries cannot be read, disabling existing meals is locked conservatively. The draft and its baseline live in
 * [savedState] (as JSON), so edits survive a configuration change and process death. A failed save keeps every edit.
 */
@Suppress("LongParameterList", "TooManyFunctions") // One dependency per port; one small function per concern.
class MealSlotsEditorViewModel(
    private val viewedDay: LocalDate,
    private val savedState: SavedStateHandle,
    private val mealSlots: MealSlotRepository,
    private val foodDiary: FoodDiaryRepository,
    private val validate: ValidateMealSlots,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val restored = savedState.get<String>(KEY_DRAFT)?.let { decode(it) }
    private var draft: MealSlotsDraft = restored ?: MealSlotsDraft()
    private var baseline: MealSlotsDraft = savedState.get<String>(KEY_BASELINE)?.let { decode(it) } ?: draft
    private var phase: Phase = if (restored == null) Phase.Loading else Phase.Editing

    /** Ids of the meals with entries on [viewedDay]; null when they could not be read. */
    private var idsWithEntries: Set<String>? = null
    private var online = true
    private var saving = false
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

            else -> listEdit(event)?.let { edit(it) }
        }
    }

    /** The change an editing event makes to the draft, or null for any other event. */
    private fun listEdit(event: MealSlotsEvent): ((MealSlotsDraft) -> MealSlotsDraft)? = when (event) {
        is MealSlotsEvent.SetLabel -> rowEdit(event.key) { it.copy(label = event.label) }

        is MealSlotsEvent.SetEnabled -> rowEdit(event.key) { row ->
            if (!event.enabled && !canDisable(row)) row else row.copy(enabled = event.enabled)
        }

        is MealSlotsEvent.Add -> { draft -> draft.withNewRow(event.afterIndex) }

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
            loadEntries()
        }
    }

    /** Reads the entries of the viewed day; a failure leaves [idsWithEntries] null (disabling stays locked). */
    private suspend fun loadEntries() {
        idsWithEntries =
            foodDiary.refresh(viewedDay).getOrNull()?.let {
                foodDiary.entries(viewedDay).first().map { entry -> entry.mealSlotId }.toSet()
            }
        publish()
    }

    /** A meal can be disabled unless it exists and has (or may have) entries on the viewed day. */
    private fun canDisable(row: DraftMealSlot): Boolean {
        val id = row.id ?: return true
        return idsWithEntries?.let { id !in it } ?: false
    }

    private fun back() {
        if (saving) return
        if (draft != baseline) {
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
        saving = true
        saveError = null
        publish()
        viewModelScope.launch {
            mealSlots.save(drafts, viewedDay).fold(
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
        if (phase != Phase.Editing || saving) return
        val next = transform(draft)
        if (next == draft) return
        draft = next
        saveError = null
        persist()
        publish()
    }

    private fun rowEdit(key: String, transform: (DraftMealSlot) -> DraftMealSlot): (MealSlotsDraft) -> MealSlotsDraft =
        { current -> current.copy(rows = current.rows.map { if (it.key == key) transform(it) else it }) }

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
            val dirty = draft != baseline
            MealSlotsUiState.Editing(
                rows =
                draft.rows.mapIndexed { index, row ->
                    MealSlotRowUi(
                        key = row.key,
                        label = row.label,
                        enabled = row.enabled,
                        error = validation.errors[index],
                        canMoveUp = index > 0,
                        canMoveDown = index < lastIndex,
                        canToggle = !row.enabled || canDisable(row),
                        hasEntriesInViewedDay = row.id != null && idsWithEntries?.contains(row.id) == true,
                        isNew = row.id == null,
                    )
                },
                noneEnabled = validation.noneEnabled,
                isOnline = online,
                isSaving = saving,
                isDirty = dirty,
                showDiscardConfirm = discardAsked,
                saveError = saveError,
                canSave = validation.isValid && dirty && online && !saving,
            )
        }
    }
}

private fun MealSlotsDraft.withNewRow(afterIndex: Int?): MealSlotsDraft {
    val row = DraftMealSlot(NEW_KEY_PREFIX + nextNewId, id = null, code = null, label = "", enabled = true)
    val at = if (afterIndex == null) rows.size else (afterIndex + 1).coerceIn(0, rows.size)
    return copy(rows = rows.toMutableList().apply { add(at, row) }, nextNewId = nextNewId + 1)
}

private fun MealSlotsDraft.toDrafts(): List<MealSlotDraft> =
    rows.map { MealSlotDraft(it.id, it.code, it.label, it.enabled) }

private fun List<MealSlot>.toDraft() = MealSlotsDraft(
    map {
        DraftMealSlot(it.id, it.id, it.code, it.label, it.enabled)
    },
)

private fun encode(draft: MealSlotsDraft): String = Json.encodeToString(MealSlotsDraft.serializer(), draft)

private fun decode(json: String): MealSlotsDraft? =
    runCatching { Json.decodeFromString(MealSlotsDraft.serializer(), json) }.getOrNull()
