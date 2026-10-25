package io.github.lopezjavier.appfitness.feature.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.UpdatedFoodEntry
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.usecase.EntryRecalculation
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck
import io.github.lopezjavier.appfitness.domain.usecase.RecalculateEntry
import io.github.lopezjavier.appfitness.feature.food.formatNumber
import io.github.lopezjavier.appfitness.feature.food.parseAmount
import io.github.lopezjavier.appfitness.feature.food.portionLabel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

private const val KEY_AMOUNT = "edit_entry_amount"
private const val KEY_PORTION = "edit_entry_portion"
private const val KEY_SLOT = "edit_entry_slot"
private const val COUNT_SEPARATOR = " × "

/** What the sheet is built from once the entry, its food and the meals are loaded. */
private class EditSetup(val entry: FoodLogEntry, val food: Food, val slots: List<MealSlot>)

/** The user's input: [portionId] is "" for the raw base unit (a saved state cannot hold null reliably). */
private data class EditDraft(val amountText: String, val portionId: String, val slotId: String)

/**
 * Edit-entry sheet (US-19, US-20): change the amount or portion and the meal of a diary entry of [date]. The entry is
 * found by [entryId] after refreshing the day (so the sheet survives process death) and its food by the entry's
 * `foodId`; the draft (amount, portion, meal) lives in [savedState] (ADR-40). The snapshot is recalculated from the
 * food's per-100 values. Saving needs the server, so [EditEntryContent.canSave] is false offline.
 */
@Suppress("LongParameterList") // One dependency per port.
class EditEntryViewModel(
    private val entryId: String,
    private val date: LocalDate,
    private val foodRepository: FoodRepository,
    private val foodDiary: FoodDiaryRepository,
    private val mealSlots: MealSlotRepository,
    private val recalculateEntry: RecalculateEntry,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private sealed interface Loaded {
        data object Loading : Loaded

        data object Failed : Loaded

        data class Ready(val setup: EditSetup) : Loaded
    }

    private val loaded = MutableStateFlow<Loaded>(Loaded.Loading)
    private val draft = MutableStateFlow<EditDraft?>(null)
    private val online = MutableStateFlow(true)
    private val saving = MutableStateFlow(false)

    private val effectChannel = Channel<EditEntryEffect>(Channel.BUFFERED)
    val effects: Flow<EditEntryEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<EditEntryUiState> =
        combine(loaded, draft, online, saving, ::buildState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, EditEntryUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        load()
    }

    fun onEvent(event: EditEntryEvent) {
        when (event) {
            is EditEntryEvent.AmountChanged -> updateDraft { it.copy(amountText = event.text) }
            is EditEntryEvent.PortionSelected -> selectPortion(event.portionId)
            is EditEntryEvent.SlotSelected -> updateDraft { it.copy(slotId = event.slotId) }
            EditEntryEvent.Save -> save()
            EditEntryEvent.Delete -> if (online.value) effectChannel.trySend(EditEntryEffect.DeleteRequested(entryId))
            EditEntryEvent.Retry -> load()
        }
    }

    private fun load() {
        loaded.value = Loaded.Loading
        viewModelScope.launch {
            val entry = loadEntry()
            val (food, slots) = coroutineScope {
                val food = async { entry?.let { foodRepository.getById(it.foodId) } }
                val slots = async { mealSlots.listEnabled() }
                food.await() to slots.await()
            }
            val loadedFood = food?.getOrNull()
            val loadedSlots = slots.getOrNull()
            if (entry == null || loadedFood == null || loadedSlots == null) {
                loaded.value = Loaded.Failed
                return@launch
            }
            if (draft.value == null) draft.value = initialDraft(entry, loadedFood)
            loaded.value = Loaded.Ready(EditSetup(entry, loadedFood, loadedSlots))
        }
    }

    /** The entry from a fresh read of the day, or null when the day cannot be read or the entry is gone. */
    private suspend fun loadEntry(): FoodLogEntry? {
        if (foodDiary.refresh(date).isFailure) return null
        return foodDiary.entries(date).first().firstOrNull { it.id == entryId }
    }

    private fun initialDraft(entry: FoodLogEntry, food: Food): EditDraft {
        val slotId = savedState.get<String>(KEY_SLOT) ?: entry.mealSlotId
        val savedAmount = savedState.get<String>(KEY_AMOUNT)
        val savedPortion = savedState.get<String>(KEY_PORTION)
            ?.takeIf { id -> id.isEmpty() || food.portions.any { it.id == id } }
        if (savedAmount != null && savedPortion != null) return EditDraft(savedAmount, savedPortion, slotId)
        val (portionId, count) = loggedPortion(entry, food)
        return EditDraft(formatNumber(count ?: entry.amount), portionId, slotId)
    }

    private fun selectPortion(portionId: String?) {
        val setup = (loaded.value as? Loaded.Ready)?.setup ?: return
        val id = portionId.orEmpty()
        if (id.isNotEmpty() && setup.food.portions.none { it.id == id }) return
        val amount = if (id.isEmpty()) setup.entry.amount else 1.0
        updateDraft { it.copy(portionId = id, amountText = formatNumber(amount)) }
    }

    private fun updateDraft(change: (EditDraft) -> EditDraft) {
        val updated = change(draft.value ?: return)
        draft.value = updated
        savedState[KEY_AMOUNT] = updated.amountText
        savedState[KEY_PORTION] = updated.portionId
        savedState[KEY_SLOT] = updated.slotId
    }

    private fun save() {
        val update = pendingUpdate() ?: return
        saving.value = true
        viewModelScope.launch {
            val result = foodDiary.updateEntry(entryId, update)
            saving.value = false
            effectChannel.send(
                if (result.isSuccess) EditEntryEffect.Saved else EditEntryEffect.SaveFailed(result.appErrorOrNull()),
            )
        }
    }

    /** The update to send for the current draft, or null when the sheet cannot be saved now. */
    private fun pendingUpdate(): UpdatedFoodEntry? {
        val setup = (loaded.value as? Loaded.Ready)?.setup
        val current = draft.value
        val content = if (setup != null && current != null) {
            buildContent(setup, current, online.value, saving.value).takeIf { it.canSave }
        } else {
            null
        }
        val amount = content?.resolvedAmount
        val preview = content?.preview
        val portion = setup?.food?.portions?.firstOrNull { it.id == current?.portionId }
        return if (current == null || amount == null || preview == null) {
            null
        } else {
            UpdatedFoodEntry(
                mealSlotId = current.slotId,
                amount = amount,
                portionLabel = portion?.let { portionLabel(it, parseAmount(current.amountText)) },
                snapshot = preview,
            )
        }
    }

    private fun buildState(loaded: Loaded, draft: EditDraft?, online: Boolean, saving: Boolean): EditEntryUiState =
        when {
            loaded == Loaded.Failed -> EditEntryUiState.Error
            loaded !is Loaded.Ready || draft == null -> EditEntryUiState.Loading
            else -> EditEntryUiState.Content(buildContent(loaded.setup, draft, online, saving))
        }

    private fun buildContent(setup: EditSetup, draft: EditDraft, online: Boolean, saving: Boolean): EditEntryContent {
        val portion = setup.food.portions.firstOrNull { it.id == draft.portionId }
        val typed = parseAmount(draft.amountText)
        val result = typed?.let { recalculateEntry(setup.food, it, portion, setup.entry.cookingOilRetainedG) }
        val valid = result as? EntryRecalculation.Valid
        return EditEntryContent(
            entry = setup.entry,
            food = setup.food,
            amountText = draft.amountText,
            selectedPortionId = portion?.id,
            slots = setup.slots,
            selectedSlotId = draft.slotId,
            resolvedAmount = valid?.amount,
            preview = valid?.snapshot,
            quantityError = when (result) {
                null -> QuantityCheck.TooLow
                is EntryRecalculation.Invalid -> result.check
                is EntryRecalculation.Valid -> null
            },
            isOffline = !online,
            saving = saving,
        )
    }
}

/** The portion and count an entry was logged with, read back from its label; none for the raw unit. */
private fun loggedPortion(entry: FoodLogEntry, food: Food): Pair<String, Double?> {
    val label = entry.portionLabel
    val match = label?.let { text ->
        food.portions.firstNotNullOfOrNull { portion -> countOf(text, portion)?.let { portion.id to it } }
    }
    return match ?: ("" to null)
}

/** 1 for a label that is just the portion, the count for "N × portion", null when the label is another portion. */
private fun countOf(label: String, portion: FoodPortion): Double? = when {
    label == portion.label -> 1.0

    label.endsWith("$COUNT_SEPARATOR${portion.label}") ->
        parseAmount(label.removeSuffix("$COUNT_SEPARATOR${portion.label}"))

    else -> null
}
