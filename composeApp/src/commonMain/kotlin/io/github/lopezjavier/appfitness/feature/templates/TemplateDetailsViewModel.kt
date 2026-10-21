package io.github.lopezjavier.appfitness.feature.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_AMOUNT = "template_details_amount_"
private const val KEY_REMOVED = "template_details_removed_"
private const val KEY_NAME = "template_details_draft_name"
private const val KEY_SHEET = "template_details_sheet"
private const val KEY_RENAME = "template_details_rename_text"
private const val KEY_RENAME_TAKEN = "template_details_rename_taken"
private const val KEY_NEW_NAME = "template_details_new_name"
private const val KEY_NEW_TAKEN = "template_details_new_taken"
private const val KEY_OPEN_FOOD = "template_details_open_food"
private const val KEY_OPEN_FOOD_AMOUNT = "template_details_open_food_amount"
private const val NO_FOOD = -1

private sealed interface DetailsLoaded {
    data object Loading : DetailsLoaded

    data object Failed : DetailsLoaded

    data class Ready(val template: MealTemplate) : DetailsLoaded
}

/**
 * The user's draft, by item index: the amounts that were typed (an absent index keeps the saved amount), the removed
 * items, the new name (null: unchanged), the open sheet with its typed names, and the open food sheet.
 */
private data class DetailsDraft(
    val amounts: Map<Int, String>,
    val removed: Set<Int>,
    val name: String?,
    val sheet: DetailsSheet,
    val renameText: String,
    val renameTaken: Boolean,
    val newNameText: String,
    val newNameTaken: Boolean,
    val openFood: Int?,
    val openFoodAmount: String?,
)

private fun amountKey(index: Int) = "$KEY_AMOUNT$index"

private fun removedKey(index: Int) = "$KEY_REMOVED$index"

/** The items left, each with its index in the saved comida (the identity of an item; a food may repeat). */
private fun remainingItems(template: MealTemplate, draft: DetailsDraft): List<IndexedValue<MealTemplateItem>> =
    template.items.withIndex().filter { it.index !in draft.removed }

private fun amountText(draft: DetailsDraft, index: Int, item: MealTemplateItem) =
    draft.amounts[index] ?: formatNumber(item.amount)

/**
 * Details and editor of a saved comida (US-21, ADR-45). Every change (amounts, removed foods, a new name) goes into a
 * draft that survives process death ([savedState], ADR-40) and is stored only by "Guardar" ("Sobrescribir" or
 * "Guardar como nueva"); totals and rows follow the draft. Changing an amount clears that food's portion label.
 * Saving and deleting need the server, so they do nothing offline; renaming is only a draft change.
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

            else -> onNameOrSaveEvent(event)
        }
    }

    private fun onNameOrSaveEvent(event: TemplateDetailsEvent) {
        when (event) {
            TemplateDetailsEvent.OpenRename -> openRename()

            is TemplateDetailsEvent.RenameNameChanged ->
                updateDraft { it.copy(renameText = event.text, renameTaken = false) }

            TemplateDetailsEvent.SubmitRename -> submitRename()

            TemplateDetailsEvent.DismissRename, TemplateDetailsEvent.DismissSheet ->
                updateDraft { it.copy(sheet = DetailsSheet.None) }

            TemplateDetailsEvent.SaveClicked -> openChoice()

            TemplateDetailsEvent.Overwrite -> overwrite()

            TemplateDetailsEvent.SaveAsNew -> updateDraft { it.copy(sheet = DetailsSheet.NewName) }

            is TemplateDetailsEvent.NewNameChanged ->
                updateDraft { it.copy(newNameText = event.text, newNameTaken = false) }

            TemplateDetailsEvent.SubmitNewName -> submitNewName()

            TemplateDetailsEvent.Delete -> delete()

            TemplateDetailsEvent.Retry -> load(showLoading = true)

            TemplateDetailsEvent.Refresh -> load(showLoading = false)

            else -> Unit
        }
    }

    private fun load(showLoading: Boolean) {
        if (showLoading) loaded.value = DetailsLoaded.Loading
        viewModelScope.launch {
            val template = mealTemplates.list().getOrNull()?.firstOrNull { it.id == templateId }
            if (template == null) {
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
            if (draft.value == null) draft.value = initialDraft(template)
            loaded.value = DetailsLoaded.Ready(template)
            draft.value?.openFood?.let { loadMicronutrients(template, it) }
        }
    }

    private fun clearSavedDraft(template: MealTemplate) {
        template.items.indices.forEach {
            savedState.remove<String>(amountKey(it))
            savedState.remove<Boolean>(removedKey(it))
        }
        savedState.remove<String>(KEY_NAME)
        savedState.remove<Int>(KEY_OPEN_FOOD)
        savedState.remove<String>(KEY_OPEN_FOOD_AMOUNT)
    }

    private fun initialDraft(template: MealTemplate): DetailsDraft {
        val restoredOpenFood = restoredOpenFood(template)
        return restoredDraft(template, restoredOpenFood)
    }

    private fun restoredDraft(template: MealTemplate, restoredOpenFood: Int?) = DetailsDraft(
        amounts = template.items.indices
            .mapNotNull { index -> savedState.get<String>(amountKey(index))?.let { index to it } }
            .toMap(),
        removed = template.items.indices.filter { savedState.get<Boolean>(removedKey(it)) == true }.toSet(),
        name = savedState.get<String>(KEY_NAME),
        sheet = savedState.get<String>(KEY_SHEET)
            ?.let { name -> DetailsSheet.entries.firstOrNull { it.name == name } }
            ?: DetailsSheet.None,
        renameText = savedState.get<String>(KEY_RENAME).orEmpty(),
        renameTaken = savedState.get<Boolean>(KEY_RENAME_TAKEN) == true,
        newNameText = savedState.get<String>(KEY_NEW_NAME).orEmpty(),
        newNameTaken = savedState.get<Boolean>(KEY_NEW_TAKEN) == true,
        openFood = restoredOpenFood,
        openFoodAmount = savedState.get<String>(KEY_OPEN_FOOD_AMOUNT)?.takeIf { restoredOpenFood != null },
    )

    private fun restoredOpenFood(template: MealTemplate): Int? =
        savedState.get<Int>(KEY_OPEN_FOOD)?.takeIf { it in template.items.indices && it != NO_FOOD }

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
        savedState[KEY_SHEET] = current.sheet.name
        savedState[KEY_RENAME] = current.renameText
        savedState[KEY_RENAME_TAKEN] = current.renameTaken
        savedState[KEY_NEW_NAME] = current.newNameText
        savedState[KEY_NEW_TAKEN] = current.newNameTaken
        savedState[KEY_OPEN_FOOD] = current.openFood ?: NO_FOOD
        if (current.openFoodAmount == null) {
            savedState.remove<String>(KEY_OPEN_FOOD_AMOUNT)
        } else {
            savedState[KEY_OPEN_FOOD_AMOUNT] = current.openFoodAmount
        }
    }

    private fun openFood(index: Int) {
        val template = readyTemplate() ?: return
        val current = draft.value ?: return
        if (remainingItems(template, current).any { it.index == index }) {
            val committed = amountText(current, index, template.items[index])
            updateDraft { it.copy(openFood = index, openFoodAmount = committed) }
            loadMicronutrients(template, index)
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
    private fun loadMicronutrients(template: MealTemplate, index: Int) {
        val food = template.items.getOrNull(index)?.food ?: return
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
        val remaining = remainingItems(template, current)
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

    private fun openRename() {
        updateDraft {
            val name = it.name ?: readyTemplate()?.name.orEmpty()
            it.copy(sheet = DetailsSheet.Rename, renameText = name, renameTaken = false)
        }
    }

    /** Only the draft changes: the name reaches the server with "Guardar". */
    private fun submitRename() {
        val current = draft.value ?: return
        if (validateTemplateName(current.renameText) == TemplateNameCheck.Valid && !current.renameTaken) {
            updateDraft { it.copy(name = it.renameText.trim(), sheet = DetailsSheet.None) }
        }
    }

    private fun openChoice() {
        if (currentContent()?.canSave == true) updateDraft { it.copy(sheet = DetailsSheet.Choose) }
    }

    private fun overwrite() {
        val template = readyTemplate() ?: return
        val items = savableItems(template) ?: return
        val name = draft.value?.name ?: template.name
        save(templateId, name, template.mealSlotId, items, DetailsSheet.Rename)
    }

    private fun submitNewName() {
        val template = readyTemplate() ?: return
        val current = draft.value ?: return
        val nameIsFree = validateTemplateName(current.newNameText) == TemplateNameCheck.Valid && !current.newNameTaken
        val items = savableItems(template)
        if (nameIsFree && items != null) {
            save(null, current.newNameText.trim(), template.mealSlotId, items, DetailsSheet.NewName)
        }
    }

    /** [onTaken] is the sheet that shows the "name already exists" error: the one holding the saved name. */
    private fun save(id: String?, name: String, slotId: String?, items: List<NewTemplateItem>, onTaken: DetailsSheet) {
        busy.value = true
        viewModelScope.launch {
            try {
                val result = mealTemplates.save(id, name, slotId, items)
                if (result.isSuccess) {
                    effectChannel.send(TemplateDetailsEffect.Saved)
                    return@launch
                }
                val error = result.appErrorOrNull()
                if (error == AppError.NameAlreadyExists) {
                    showTakenName(onTaken, name)
                } else {
                    updateDraft { it.copy(sheet = DetailsSheet.None) }
                    effectChannel.send(TemplateDetailsEffect.ActionFailed(error))
                }
            } finally {
                busy.value = false
            }
        }
    }

    private fun showTakenName(sheet: DetailsSheet, name: String) {
        updateDraft {
            if (sheet == DetailsSheet.Rename) {
                it.copy(sheet = sheet, renameText = name, renameTaken = true)
            } else {
                it.copy(sheet = sheet, newNameTaken = true)
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
            remainingItems(template, current).map { (index, item) ->
                val text = amountText(current, index, item)
                NewTemplateItem(
                    foodId = item.food.id,
                    // canSave guarantees that every remaining amount parses.
                    amount = parseAmount(text) ?: 0.0,
                    // Only an untouched amount keeps its portion label; the label is just a snapshot of the amount.
                    portionLabel = item.portionLabel.takeIf { text == formatNumber(item.amount) },
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
            draft?.let { buildContent(loaded.template, it, micronutrients, online, busy) }
                ?: TemplateDetailsUiState.Loading
    }

    private fun buildContent(
        template: MealTemplate,
        current: DetailsDraft,
        micronutrients: Map<String, Micronutrients>,
        online: Boolean,
        busy: Boolean,
    ): TemplateDetailsUiState.Content {
        val remaining = remainingItems(template, current)
        val rows = remaining.map { (index, item) -> buildRow(index, item, current, remaining.size > 1) }
        val name = current.name ?: template.name
        val isDirty = name.trim() != template.name ||
            current.removed.isNotEmpty() ||
            template.items.withIndex().any { (index, item) ->
                parseAmount(amountText(current, index, item)) != item.amount
            }
        val open = current.openFood?.let { index ->
            remaining.firstOrNull { it.index == index }?.let { (_, item) ->
                // Live preview: the open row follows the amount typed in the sheet, not the committed one.
                buildRow(index, item, current, remaining.size > 1, current.openFoodAmount)
            }
        }
        return TemplateDetailsUiState.Content(
            name = name,
            totalKcal = rows.sumOf { it.kcal },
            totalProteinG = rows.sumOf { it.proteinG },
            totalCarbsG = rows.sumOf { it.carbsG },
            totalFatG = rows.sumOf { it.fatG },
            rows = rows,
            canSave = isDirty && rows.isNotEmpty() && rows.all { it.quantityError == null } && online && !busy,
            isDirty = isDirty,
            isOffline = !online,
            busy = busy,
            sheet = current.sheet,
            openFood = open?.let {
                val food = template.items[it.index].food
                OpenFood(it, food, micronutrients[food.id], canSave = it.quantityError == null)
            },
            rename = nameDraft(current.renameText, current.renameTaken).takeIf { current.sheet == DetailsSheet.Rename },
            newName = nameDraft(current.newNameText, current.newNameTaken).takeIf {
                current.sheet == DetailsSheet.NewName
            },
        )
    }

    private fun nameDraft(text: String, taken: Boolean) = RenameDraft(text, taken, validateTemplateName(text))

    private fun buildRow(
        index: Int,
        item: MealTemplateItem,
        current: DetailsDraft,
        removable: Boolean,
        pendingText: String? = null,
    ): DetailsRow {
        val text = pendingText ?: amountText(current, index, item)
        val amount = parseAmount(text)
        val check = amount?.let { validateQuantity(it) } ?: QuantityCheck.TooLow
        val error = check.takeUnless { it == QuantityCheck.Valid }
        val nutrition = scaleNutrition(item.food.nutrition, if (error == null) amount ?: 0.0 else 0.0)
        return DetailsRow(
            index = index,
            foodId = item.food.id,
            name = item.food.name,
            unit = item.food.baseUnit,
            amountText = text,
            quantityError = error,
            portionLabel = item.portionLabel.takeIf { text == formatNumber(item.amount) },
            kcal = nutrition.kcal,
            proteinG = nutrition.proteinG,
            carbsG = nutrition.carbsG,
            fatG = nutrition.fatG,
            removable = removable,
        )
    }
}
