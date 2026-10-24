package io.github.lopezjavier.appfitness.feature.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.TemplateNameCheck
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.food.formatNumber
import io.github.lopezjavier.appfitness.feature.food.parseAmount
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_AMOUNT = "template_details_amount_"
private const val KEY_REMOVED = "template_details_removed_"
private const val KEY_NAME = "template_details_draft_name"
private const val KEY_TAKEN = "template_details_taken_name"
private const val KEY_ADDED_FOODS = "template_details_added_foods"
private const val KEY_ADDED_AMOUNTS = "template_details_added_amounts"
private const val KEY_ADDED_LABELS = "template_details_added_labels"
private const val KEY_OPEN_FOOD = "template_details_open_food"
private const val KEY_OPEN_FOOD_AMOUNT = "template_details_open_food_amount"
private const val NO_FOOD = -1
private const val NO_LABEL = ""

/** The comida as read, and the (normalised) names of the other comidas, for the duplicate check. */
private sealed interface DetailsLoaded {
    data object Loading : DetailsLoaded

    data object Failed : DetailsLoaded

    data class Ready(val template: MealTemplate, val otherNames: Set<String>) : DetailsLoaded
}

/**
 * A food added in the editor. Its row index is the saved item count plus its position in the added list (the list is
 * append-only, removed additions stay in it and in `removed`, so an index never changes).
 */
private data class AddedItem(val foodId: String, val amount: Double, val portionLabel: String?, val food: Food)

/**
 * The user's draft, by row index: the amounts that were typed (an absent index keeps the original amount), the removed
 * rows, the new name (null: unchanged), the name the server refused, the added foods, and the open food sheet.
 */
private data class DetailsDraft(
    val amounts: Map<Int, String>,
    val removed: Set<Int>,
    val name: String?,
    val takenName: String?,
    val added: List<AddedItem>,
    val openFood: Int?,
    val openFoodAmount: String?,
)

/** A food of the comida as drafted (saved or added), before the draft's amount is applied. */
private class Entry(val index: Int, val food: Food, val amount: Double, val portionLabel: String?, val added: Boolean)

private fun amountKey(index: Int) = "$KEY_AMOUNT$index"

private fun removedKey(index: Int) = "$KEY_REMOVED$index"

private fun nameKey(name: String) = name.trim().lowercase()

/** Every food of the draft, saved ones first, then the added ones in the order they arrived. */
private fun entries(template: MealTemplate, draft: DetailsDraft): List<Entry> {
    val saved = template.items.mapIndexed { index, item ->
        Entry(index, item.food, item.amount, item.portionLabel, added = false)
    }
    val added = draft.added.mapIndexed { position, item ->
        Entry(template.items.size + position, item.food, item.amount, item.portionLabel, added = true)
    }
    return saved + added
}

/** The foods left, with the index that identifies each (a food may repeat). */
private fun remainingEntries(template: MealTemplate, draft: DetailsDraft): List<Entry> =
    entries(template, draft).filter { it.index !in draft.removed }

private fun amountText(draft: DetailsDraft, entry: Entry) = draft.amounts[entry.index] ?: formatNumber(entry.amount)

/**
 * Editor of a saved comida (US-21, US-47, ADR-45). Every change (name, amounts, removed and added foods) goes into a
 * draft that survives process death ([savedState], ADR-40) and is stored only by "Guardar", in a single request;
 * totals and rows follow the draft. Changing an amount clears that food's portion label. Saving and deleting need the
 * server, so they do nothing offline; renaming and the other draft changes work offline.
 */
@Suppress("LongParameterList", "TooManyFunctions") // One dependency per port; one function per user action.
class TemplateDetailsViewModel(
    private val templateId: String,
    private val mealTemplates: MealTemplateRepository,
    private val scaleNutrition: ScaleNutrition,
    private val validateQuantity: ValidateQuantity,
    private val validateTemplateName: ValidateTemplateName,
    private val foodRepository: FoodRepository,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val loaded = MutableStateFlow<DetailsLoaded>(DetailsLoaded.Loading)
    private val draft = MutableStateFlow<DetailsDraft?>(null)
    private val micronutrients = MutableStateFlow<Map<String, Micronutrients>>(emptyMap())
    private val online = MutableStateFlow(true)
    private val busy = MutableStateFlow(false)

    private val effectChannel = Channel<TemplateDetailsEffect>(Channel.BUFFERED)
    val effects: Flow<TemplateDetailsEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<TemplateDetailsUiState> =
        combine(loaded, draft, micronutrients, online, busy, ::buildState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, TemplateDetailsUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        load(showLoading = true)
    }

    fun onEvent(event: TemplateDetailsEvent) {
        when (event) {
            is TemplateDetailsEvent.OpenFood -> openFood(event.index)

            TemplateDetailsEvent.DismissFood -> updateDraft { it.copy(openFood = null, openFoodAmount = null) }

            is TemplateDetailsEvent.FoodAmountChanged ->
                updateDraft { if (it.openFood == null) it else it.copy(openFoodAmount = event.text) }

            TemplateDetailsEvent.SaveFood -> saveFood()

            is TemplateDetailsEvent.AmountChanged ->
                updateDraft { it.copy(amounts = it.amounts + (event.index to event.text)) }

            is TemplateDetailsEvent.RemoveFood -> removeFood(event.index)

            else -> onNameAddOrSaveEvent(event)
        }
    }

    private fun onNameAddOrSaveEvent(event: TemplateDetailsEvent) {
        when (event) {
            is TemplateDetailsEvent.NameChanged -> updateDraft { it.copy(name = event.text) }
            TemplateDetailsEvent.AddFoodClicked -> effectChannel.trySend(TemplateDetailsEffect.OpenFoodPicker)
            is TemplateDetailsEvent.FoodPicked -> addFood(event)
            TemplateDetailsEvent.SaveClicked -> saveDraft()
            TemplateDetailsEvent.Delete -> delete()
            TemplateDetailsEvent.Retry -> load(showLoading = true)
            TemplateDetailsEvent.Refresh -> load(showLoading = false)
            else -> Unit
        }
    }

    private fun load(showLoading: Boolean) {
        if (showLoading) loaded.value = DetailsLoaded.Loading
        viewModelScope.launch {
            val all = mealTemplates.list().getOrNull()
            val template = all?.firstOrNull { it.id == templateId }
            if (all == null || template == null) {
                loaded.value = DetailsLoaded.Failed
                return@launch
            }
            // The draft is read once: a retry or a refresh keeps what the user has changed, unless the saved items
            // changed on the server (the draft is keyed by index, so it would no longer match).
            val previous = readyTemplate()
            if (previous != null && previous.items != template.items) {
                clearSavedDraft(previous)
                draft.value = null
            }
            if (draft.value == null) {
                val restored = initialDraft(template)
                if (restored == null) {
                    loaded.value = DetailsLoaded.Failed
                    return@launch
                }
                draft.value = restored
            }
            val others = all.filter { it.id != templateId }.map { nameKey(it.name) }.toSet()
            loaded.value = DetailsLoaded.Ready(template, others)
            draft.value?.openFood?.let { index -> openFoodOf(template, index)?.let(::loadMicronutrients) }
        }
    }

    private fun clearSavedDraft(template: MealTemplate) {
        val indices = template.items.indices + (template.items.size until template.items.size + addedCount())
        indices.forEach {
            savedState.remove<String>(amountKey(it))
            savedState.remove<Boolean>(removedKey(it))
        }
        listOf(KEY_NAME, KEY_TAKEN, KEY_OPEN_FOOD_AMOUNT).forEach { savedState.remove<String>(it) }
        listOf(KEY_ADDED_FOODS, KEY_ADDED_AMOUNTS, KEY_ADDED_LABELS).forEach {
            savedState.remove<ArrayList<String>>(it)
        }
        savedState.remove<Int>(KEY_OPEN_FOOD)
    }

    private fun addedCount() = savedState.get<ArrayList<String>>(KEY_ADDED_FOODS)?.size ?: 0

    /** The draft saved by an earlier instance (or an empty one); null when an added food cannot be read again. */
    private suspend fun initialDraft(template: MealTemplate): DetailsDraft? {
        val added = restoredAdded() ?: return null
        val total = template.items.size + added.size
        val removed = (0 until total).filter { savedState.get<Boolean>(removedKey(it)) == true }.toSet()
        val restoredOpenFood = savedState.get<Int>(KEY_OPEN_FOOD)
            ?.takeIf { it in 0 until total && it != NO_FOOD && it !in removed }
        return DetailsDraft(
            amounts = (0 until total)
                .mapNotNull { index -> savedState.get<String>(amountKey(index))?.let { index to it } }
                .toMap(),
            removed = removed,
            name = savedState.get<String>(KEY_NAME),
            takenName = savedState.get<String>(KEY_TAKEN),
            added = added,
            openFood = restoredOpenFood,
            openFoodAmount = savedState.get<String>(KEY_OPEN_FOOD_AMOUNT)?.takeIf { restoredOpenFood != null },
        )
    }

    private suspend fun restoredAdded(): List<AddedItem>? {
        val ids = savedState.get<ArrayList<String>>(KEY_ADDED_FOODS).orEmpty()
        val amounts = savedState.get<ArrayList<String>>(KEY_ADDED_AMOUNTS).orEmpty()
        val labels = savedState.get<ArrayList<String>>(KEY_ADDED_LABELS).orEmpty()
        val restored = ids.mapIndexed { position, id ->
            val food = foodRepository.getById(id).getOrNull() ?: return null
            AddedItem(id, amounts[position].toDouble(), labels[position].takeIf { it != NO_LABEL }, food)
        }
        return restored
    }

    private fun updateDraft(change: (DetailsDraft) -> DetailsDraft) {
        val updated = change(draft.value ?: return)
        draft.value = updated
        persist(updated)
    }

    private fun persist(current: DetailsDraft) {
        current.amounts.forEach { (index, text) -> savedState[amountKey(index)] = text }
        // Removals only grow (a removed food cannot be restored), so nothing stale is left behind.
        current.removed.forEach { savedState[removedKey(it)] = true }
        savedState[KEY_NAME] = current.name
        savedState[KEY_TAKEN] = current.takenName
        savedState[KEY_ADDED_FOODS] = ArrayList(current.added.map { it.foodId })
        savedState[KEY_ADDED_AMOUNTS] = ArrayList(current.added.map { it.amount.toString() })
        savedState[KEY_ADDED_LABELS] = ArrayList(current.added.map { it.portionLabel ?: NO_LABEL })
        savedState[KEY_OPEN_FOOD] = current.openFood ?: NO_FOOD
        if (current.openFoodAmount == null) {
            savedState.remove<String>(KEY_OPEN_FOOD_AMOUNT)
        } else {
            savedState[KEY_OPEN_FOOD_AMOUNT] = current.openFoodAmount
        }
    }

    private fun openFoodOf(template: MealTemplate, index: Int): Food? =
        draft.value?.let { entries(template, it) }?.firstOrNull { it.index == index }?.food

    private fun openFood(index: Int) {
        val template = readyTemplate()
        val current = draft.value
        val entry = if (template == null || current == null) {
            null
        } else {
            remainingEntries(template, current).firstOrNull { it.index == index }
        }
        if (current != null && entry != null) {
            updateDraft { it.copy(openFood = index, openFoodAmount = amountText(current, entry)) }
            loadMicronutrients(entry.food)
        }
    }

    /** Applies the pending food-sheet amount to the draft and closes the sheet; an invalid amount does nothing. */
    private fun saveFood() {
        val current = draft.value
        val index = current?.openFood
        val text = current?.openFoodAmount
        if (index != null && text != null && currentContent()?.openFood?.canSave == true) {
            updateDraft { it.copy(amounts = it.amounts + (index to text), openFood = null, openFoodAmount = null) }
        }
    }

    /** Optional info: a failure or an empty result just hides the section. */
    private fun loadMicronutrients(food: Food) {
        if (food.id in micronutrients.value) return
        viewModelScope.launch {
            foodRepository.micronutrients(food).getOrNull()?.takeIf { !it.isEmpty }?.let { found ->
                micronutrients.value += (food.id to found)
            }
        }
    }

    /** At least one food must remain: the last one cannot be removed. */
    private fun removeFood(index: Int) {
        val template = readyTemplate() ?: return
        val current = draft.value ?: return
        val remaining = remainingEntries(template, current)
        if (remaining.size > 1 && remaining.any { it.index == index }) {
            updateDraft {
                val stillOpen = it.openFood != index
                it.copy(
                    removed = it.removed + index,
                    openFood = it.openFood.takeIf { stillOpen },
                    openFoodAmount = it.openFoodAmount.takeIf { stillOpen },
                )
            }
        }
    }

    /** Appends the picked food to the draft; it waits for the draft when the editor is still loading. */
    private fun addFood(picked: TemplateDetailsEvent.FoodPicked) {
        viewModelScope.launch {
            val result = foodRepository.getById(picked.foodId)
            val food = result.getOrNull()
            if (food == null) {
                effectChannel.send(TemplateDetailsEffect.ActionFailed(result.appErrorOrNull()))
                return@launch
            }
            draft.filterNotNull().first()
            updateDraft { it.copy(added = it.added + AddedItem(food.id, picked.amount, picked.portionLabel, food)) }
        }
    }

    private fun saveDraft() {
        val template = readyTemplate() ?: return
        val items = savableItems(template) ?: return
        val name = (draft.value?.name ?: template.name).trim()
        busy.value = true
        viewModelScope.launch {
            try {
                val result = mealTemplates.save(templateId, name, template.mealSlotId, items)
                if (result.isSuccess) {
                    effectChannel.send(TemplateDetailsEffect.Saved)
                    return@launch
                }
                val error = result.appErrorOrNull()
                if (error == AppError.NameAlreadyExists) {
                    updateDraft { it.copy(takenName = nameKey(name)) }
                } else {
                    effectChannel.send(TemplateDetailsEffect.ActionFailed(error))
                }
            } finally {
                busy.value = false
            }
        }
    }

    private fun delete() {
        if (!online.value || busy.value || readyTemplate() == null) return
        busy.value = true
        viewModelScope.launch {
            try {
                val result = mealTemplates.delete(templateId)
                effectChannel.send(
                    if (result.isSuccess) {
                        TemplateDetailsEffect.Deleted
                    } else {
                        TemplateDetailsEffect.ActionFailed(result.appErrorOrNull())
                    },
                )
            } finally {
                busy.value = false
            }
        }
    }

    private fun readyTemplate(): MealTemplate? = (loaded.value as? DetailsLoaded.Ready)?.template

    private fun currentContent(): TemplateDetailsUiState.Content? =
        buildState(loaded.value, draft.value, micronutrients.value, online.value, busy.value) as?
            TemplateDetailsUiState.Content

    /** The items to store for the current draft, or null when the draft cannot be saved now. */
    private fun savableItems(template: MealTemplate): List<NewTemplateItem>? {
        val current = draft.value ?: return null
        return if (currentContent()?.canSave != true) {
            null
        } else {
            remainingEntries(template, current).map { entry ->
                val text = amountText(current, entry)
                NewTemplateItem(
                    foodId = entry.food.id,
                    // canSave guarantees that every remaining amount parses.
                    amount = parseAmount(text) ?: 0.0,
                    // Only an untouched amount keeps its portion label; the label is just a snapshot of the amount.
                    portionLabel = entry.portionLabel.takeIf { text == formatNumber(entry.amount) },
                )
            }
        }
    }

    @Suppress("LongParameterList") // One argument per combined flow.
    private fun buildState(
        loaded: DetailsLoaded,
        draft: DetailsDraft?,
        micronutrients: Map<String, Micronutrients>,
        online: Boolean,
        busy: Boolean,
    ): TemplateDetailsUiState = when (loaded) {
        DetailsLoaded.Loading -> TemplateDetailsUiState.Loading

        DetailsLoaded.Failed -> TemplateDetailsUiState.Error

        is DetailsLoaded.Ready ->
            draft?.let { buildContent(loaded, it, micronutrients, online, busy) } ?: TemplateDetailsUiState.Loading
    }

    private fun nameErrorOf(ready: DetailsLoaded.Ready, name: String, takenName: String?): TemplateNameError? = when {
        validateTemplateName(name) == TemplateNameCheck.TooLong -> TemplateNameError.TooLong

        nameKey(name) in ready.otherNames || (takenName != null && nameKey(name) == takenName) ->
            TemplateNameError.Taken

        else -> null
    }

    private fun buildContent(
        ready: DetailsLoaded.Ready,
        current: DetailsDraft,
        micronutrients: Map<String, Micronutrients>,
        online: Boolean,
        busy: Boolean,
    ): TemplateDetailsUiState.Content {
        val template = ready.template
        val remaining = remainingEntries(template, current)
        val rows = remaining.map { buildRow(it, current, remaining.size > 1) }
        val name = current.name ?: template.name
        val nameError = nameErrorOf(ready, name, current.takenName)
        val nameValid = validateTemplateName(name) == TemplateNameCheck.Valid && nameError == null
        val isDirty = name.trim() != template.name ||
            remaining.size != template.items.size ||
            remaining.any { it.added || parseAmount(amountText(current, it)) != it.amount }
        val open = current.openFood?.let { index ->
            remaining.firstOrNull { it.index == index }?.let { entry ->
                // Live preview: the open row follows the amount typed in the sheet, not the committed one.
                entry to buildRow(entry, current, remaining.size > 1, current.openFoodAmount)
            }
        }
        return TemplateDetailsUiState.Content(
            name = name,
            nameError = nameError,
            totalKcal = rows.sumOf { it.kcal },
            totalProteinG = rows.sumOf { it.proteinG },
            totalCarbsG = rows.sumOf { it.carbsG },
            totalFatG = rows.sumOf { it.fatG },
            rows = rows,
            canSave = isDirty && nameValid && rows.isNotEmpty() && rows.all { it.quantityError == null } &&
                online && !busy,
            isDirty = isDirty,
            isOffline = !online,
            busy = busy,
            openFood = open?.let { (entry, row) ->
                OpenFood(row, entry.food, micronutrients[entry.food.id], canSave = row.quantityError == null)
            },
        )
    }

    private fun buildRow(
        entry: Entry,
        current: DetailsDraft,
        removable: Boolean,
        pendingText: String? = null,
    ): DetailsRow {
        val text = pendingText ?: amountText(current, entry)
        val amount = parseAmount(text)
        val check = amount?.let { validateQuantity(it) } ?: QuantityCheck.TooLow
        val error = check.takeUnless { it == QuantityCheck.Valid }
        val nutrition = scaleNutrition(entry.food.nutrition, if (error == null) amount ?: 0.0 else 0.0)
        return DetailsRow(
            index = entry.index,
            foodId = entry.food.id,
            name = entry.food.name,
            unit = entry.food.baseUnit,
            amountText = text,
            quantityError = error,
            portionLabel = entry.portionLabel.takeIf { text == formatNumber(entry.amount) },
            kcal = nutrition.kcal,
            proteinG = nutrition.proteinG,
            carbsG = nutrition.carbsG,
            fatG = nutrition.fatG,
            removable = removable,
            added = entry.added,
        )
    }
}
