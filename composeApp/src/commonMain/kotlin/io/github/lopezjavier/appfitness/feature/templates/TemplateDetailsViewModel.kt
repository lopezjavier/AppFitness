package io.github.lopezjavier.appfitness.feature.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.FoodPreferencesRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.usecase.AddTemplateToDiary
import io.github.lopezjavier.appfitness.domain.usecase.CookedItemNutrition
import io.github.lopezjavier.appfitness.domain.usecase.CookingImpact
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.TemplateNameCheck
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.food.CookingImpactPreview
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val KEY_AMOUNT = "template_details_amount_"
private const val KEY_REMOVED = "template_details_removed_"
private const val KEY_NAME = "template_details_draft_name"
private const val KEY_TAKEN = "template_details_taken_name"
private const val KEY_ADDED_FOODS = "template_details_added_foods"
private const val KEY_ADDED_AMOUNTS = "template_details_added_amounts"
private const val KEY_ADDED_LABELS = "template_details_added_labels"
private const val KEY_OPEN_FOOD = "template_details_open_food"
private const val KEY_OPEN_FOOD_AMOUNT = "template_details_open_food_amount"
private const val KEY_METHOD = "template_details_method_"
private const val KEY_OPEN_FOOD_METHOD = "template_details_open_food_method"
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
    val methods: Map<Int, CookingMethod>,
    val openFoodMethod: CookingMethod?,
)

/**
 * What is in flight or failed: saving or deleting ([busy]), and "Añadir" ([adding], [addFailed]); plus the current
 * olive oil [prefs] (null: not read, so cooking has no impact on the numbers shown).
 */
private data class Progress(
    val busy: Boolean,
    val adding: Boolean,
    val addFailed: Boolean,
    val prefs: FoodPreferences?,
)

/** A food of the comida as drafted (saved or added), before the draft's amount is applied. */
private class Entry(
    val index: Int,
    val food: Food,
    val amount: Double,
    val portionLabel: String?,
    val added: Boolean,
    val method: CookingMethod,
)

private fun amountKey(index: Int) = "$KEY_AMOUNT$index"

private fun methodKey(index: Int) = "$KEY_METHOD$index"

private fun removedKey(index: Int) = "$KEY_REMOVED$index"

private fun nameKey(name: String) = name.trim().lowercase()

/** Every food of the draft, saved ones first, then the added ones in the order they arrived. */
private fun entries(template: MealTemplate, draft: DetailsDraft): List<Entry> {
    val saved = template.items.mapIndexed { index, item ->
        Entry(index, item.food, item.amount, item.portionLabel, added = false, method = item.cookingMethod)
    }
    val added = draft.added.mapIndexed { position, item ->
        Entry(
            template.items.size + position,
            item.food,
            item.amount,
            item.portionLabel,
            added = true,
            method = CookingMethod.Raw,
        )
    }
    return saved + added
}

/** The foods left, with the index that identifies each (a food may repeat). */
private fun remainingEntries(template: MealTemplate, draft: DetailsDraft): List<Entry> =
    entries(template, draft).filter { it.index !in draft.removed }

private fun amountText(draft: DetailsDraft, entry: Entry) = draft.amounts[entry.index] ?: formatNumber(entry.amount)

/** The method the draft gives to [entry] (drinks are always raw). */
private fun methodOf(draft: DetailsDraft, entry: Entry): CookingMethod =
    CookingImpact.effectiveMethod(draft.methods[entry.index] ?: entry.method, entry.food.baseUnit)

/**
 * Editor of a saved comida (US-21, US-47, ADR-45). Every change (name, amounts, removed and added foods) goes into a
 * draft that survives process death ([savedState], ADR-40) and is stored only by "Guardar", in a single request;
 * totals and rows follow the draft. Changing an amount clears that food's portion label. Saving and deleting need the
 * server, so they do nothing offline; renaming and the other draft changes work offline.
 *
 * It is also the comida's details screen (US-53, ADR-63): "Añadir" logs the foods as the draft shows them to [slotId]
 * on [date] (null [slotId] = the meal of the time of day) without storing the comida; only "Guardar" does that.
 */
@Suppress("LongParameterList", "TooManyFunctions") // One dependency per port; one function per user action.
class TemplateDetailsViewModel(
    private val templateId: String,
    private val slotId: String?,
    private val date: LocalDate,
    private val mealTemplates: MealTemplateRepository,
    private val scaleNutrition: ScaleNutrition,
    private val validateQuantity: ValidateQuantity,
    private val validateTemplateName: ValidateTemplateName,
    private val foodRepository: FoodRepository,
    private val foodPreferences: FoodPreferencesRepository,
    private val addTemplateToDiary: AddTemplateToDiary,
    private val mealSlots: MealSlotRepository,
    private val defaultMealSlot: DefaultMealSlot,
    private val clock: Clock,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val loaded = MutableStateFlow<DetailsLoaded>(DetailsLoaded.Loading)
    private val draft = MutableStateFlow<DetailsDraft?>(null)
    private val micronutrients = MutableStateFlow<Map<String, Micronutrients>>(emptyMap())
    private val prefs = MutableStateFlow<FoodPreferences?>(null)
    private val cookedItemNutrition = CookedItemNutrition(scaleNutrition)
    private val online = MutableStateFlow(true)
    private val busy = MutableStateFlow(false)
    private val adding = MutableStateFlow(false)
    private val addFailed = MutableStateFlow(false)

    private val effectChannel = Channel<TemplateDetailsEffect>(Channel.BUFFERED)
    val effects: Flow<TemplateDetailsEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<TemplateDetailsUiState> =
        combine(
            loaded,
            draft,
            micronutrients,
            online,
            combine(busy, adding, addFailed, prefs, ::Progress),
            ::buildState,
        )
            .stateIn(viewModelScope, SharingStarted.Eagerly, TemplateDetailsUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        load(showLoading = true)
    }

    fun onEvent(event: TemplateDetailsEvent) {
        when (event) {
            is TemplateDetailsEvent.OpenFood -> openFood(event.index)

            TemplateDetailsEvent.DismissFood ->
                updateDraft { it.copy(openFood = null, openFoodAmount = null, openFoodMethod = null) }

            is TemplateDetailsEvent.FoodAmountChanged ->
                updateDraft { if (it.openFood == null) it else it.copy(openFoodAmount = event.text) }

            is TemplateDetailsEvent.MethodChanged -> changeOpenFoodMethod(event.method)

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
            TemplateDetailsEvent.Add -> add()
            TemplateDetailsEvent.Discard -> discardDraft()
            TemplateDetailsEvent.Retry -> load(showLoading = true)
            TemplateDetailsEvent.Refresh -> load(showLoading = false)
            else -> Unit
        }
    }

    private fun load(showLoading: Boolean) {
        if (showLoading) loaded.value = DetailsLoaded.Loading
        viewModelScope.launch {
            // Optional info: without the preferences the numbers just omit the cooking oil.
            prefs.value = foodPreferences.get().getOrNull()
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

    private fun discardDraft() {
        val template = readyTemplate() ?: return
        if (draft.value == null) return
        clearSavedDraft(template)
        draft.value = DetailsDraft(
            amounts = emptyMap(),
            removed = emptySet(),
            name = null,
            takenName = null,
            added = emptyList(),
            openFood = null,
            openFoodAmount = null,
            methods = emptyMap(),
            openFoodMethod = null,
        )
    }

    private fun clearSavedDraft(template: MealTemplate) {
        val indices = template.items.indices + (template.items.size until template.items.size + addedCount())
        indices.forEach {
            savedState.remove<String>(amountKey(it))
            savedState.remove<Boolean>(removedKey(it))
            savedState.remove<String>(methodKey(it))
        }
        listOf(KEY_NAME, KEY_TAKEN, KEY_OPEN_FOOD_AMOUNT, KEY_OPEN_FOOD_METHOD).forEach {
            savedState.remove<String>(it)
        }
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
            methods = (0 until total)
                .mapNotNull { index ->
                    CookingMethod.fromWireName(savedState.get<String>(methodKey(index)))?.let { index to it }
                }
                .toMap(),
            openFoodMethod = CookingMethod.fromWireName(savedState.get<String>(KEY_OPEN_FOOD_METHOD))
                ?.takeIf { restoredOpenFood != null },
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
        current.methods.forEach { (index, method) -> savedState[methodKey(index)] = method.wireName }
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
        if (current.openFoodMethod == null) {
            savedState.remove<String>(KEY_OPEN_FOOD_METHOD)
        } else {
            savedState[KEY_OPEN_FOOD_METHOD] = current.openFoodMethod.wireName
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
            updateDraft {
                it.copy(
                    openFood = index,
                    openFoodAmount = amountText(current, entry),
                    openFoodMethod = methodOf(current, entry),
                )
            }
            loadMicronutrients(entry.food)
        }
    }

    /** Sets the method pending in the open food sheet; ignored for drinks (always raw). */
    private fun changeOpenFoodMethod(method: CookingMethod) {
        val template = readyTemplate()
        val current = draft.value
        val entry = if (template != null && current != null) {
            remainingEntries(template, current).firstOrNull { it.index == current.openFood }
        } else {
            null
        }
        if (entry != null && CookingImpact.appliesTo(entry.food.baseUnit)) {
            updateDraft { it.copy(openFoodMethod = method) }
        }
    }

    /** Applies the pending food-sheet amount and method to the draft and closes the sheet (invalid amount: no-op). */
    private fun saveFood() {
        val current = draft.value
        val index = current?.openFood
        val text = current?.openFoodAmount
        if (index != null && text != null && currentContent()?.openFood?.canSave == true) {
            updateDraft {
                it.copy(
                    amounts = it.amounts + (index to text),
                    methods = it.openFoodMethod?.let { method -> it.methods + (index to method) } ?: it.methods,
                    openFood = null,
                    openFoodAmount = null,
                    openFoodMethod = null,
                )
            }
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
                    methods = it.methods - index,
                    openFood = it.openFood.takeIf { stillOpen },
                    openFoodAmount = it.openFoodAmount.takeIf { stillOpen },
                    openFoodMethod = it.openFoodMethod.takeIf { stillOpen },
                )
            }
            savedState.remove<String>(methodKey(index))
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

    /** Logs the foods as the draft shows them (nothing is stored in the comida); needs the server, so not offline. */
    private fun add() {
        val template = readyTemplate()
        val current = draft.value
        if (template == null || current == null || currentContent()?.canAdd != true) return
        val shown = template.copy(
            items = remainingEntries(template, current).map { entry ->
                val text = amountText(current, entry)
                MealTemplateItem(
                    food = entry.food,
                    amount = parseAmount(text) ?: return,
                    portionLabel = entry.portionLabel.takeIf { text == formatNumber(entry.amount) },
                    cookingMethod = methodOf(current, entry),
                )
            },
        )
        adding.value = true
        addFailed.value = false
        viewModelScope.launch {
            try {
                val mealSlotId = slotId ?: defaultSlotId()
                val result = mealSlotId?.let { addTemplateToDiary(shown, it, date) }
                if (result?.isSuccess == true) {
                    effectChannel.trySend(TemplateDetailsEffect.Added)
                } else {
                    addFailed.value = true
                    effectChannel.trySend(TemplateDetailsEffect.ActionFailed(result?.appErrorOrNull()))
                }
            } finally {
                adding.value = false
            }
        }
    }

    /** The meal chosen by the time of day when the route has none; null when no meal is enabled. */
    private suspend fun defaultSlotId(): String? {
        val slots = mealSlots.listEnabled().getOrNull() ?: return null
        return defaultMealSlot(clock.now().toLocalDateTime(timeZone).time, slots)?.id
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

    private fun currentContent(): TemplateDetailsUiState.Content? = buildState(
        loaded.value,
        draft.value,
        micronutrients.value,
        online.value,
        Progress(busy.value, adding.value, addFailed.value, prefs.value),
    ) as?
        TemplateDetailsUiState.Content

    /** The items to store for the current draft, or null when the draft cannot be saved now. */
    private fun savableItems(template: MealTemplate): List<NewTemplateItem>? {
        val current = draft.value
        if (current == null || currentContent()?.canSave != true) return null
        val items = remainingEntries(template, current).map { entry ->
            val text = amountText(current, entry)
            parseAmount(text)?.let { amount ->
                NewTemplateItem(
                    foodId = entry.food.id,
                    amount = amount,
                    // Only an untouched amount keeps its portion label; the label is just a snapshot of the amount.
                    portionLabel = entry.portionLabel.takeIf { text == formatNumber(entry.amount) },
                    cookingMethod = methodOf(current, entry),
                )
            }
        }
        return if (items.any { it == null }) null else items.filterNotNull()
    }

    @Suppress("LongParameterList") // One argument per combined flow.
    private fun buildState(
        loaded: DetailsLoaded,
        draft: DetailsDraft?,
        micronutrients: Map<String, Micronutrients>,
        online: Boolean,
        progress: Progress,
    ): TemplateDetailsUiState = when (loaded) {
        DetailsLoaded.Loading -> TemplateDetailsUiState.Loading

        DetailsLoaded.Failed -> TemplateDetailsUiState.Error

        is DetailsLoaded.Ready ->
            draft?.let { buildContent(loaded, it, micronutrients, online, progress) } ?: TemplateDetailsUiState.Loading
    }

    private fun nameErrorOf(ready: DetailsLoaded.Ready, name: String, takenName: String?): TemplateNameError? = when {
        validateTemplateName(name) == TemplateNameCheck.TooLong -> TemplateNameError.TooLong

        nameKey(name) in ready.otherNames || (takenName != null && nameKey(name) == takenName) ->
            TemplateNameError.Taken

        else -> null
    }

    /** The food is new or its amount or method differs from the saved comida. */
    private fun isChanged(current: DetailsDraft, entry: Entry) = entry.added ||
        parseAmount(amountText(current, entry)) != entry.amount ||
        methodOf(current, entry) != entry.method

    private fun buildContent(
        ready: DetailsLoaded.Ready,
        current: DetailsDraft,
        micronutrients: Map<String, Micronutrients>,
        online: Boolean,
        progress: Progress,
    ): TemplateDetailsUiState.Content {
        val busy = progress.busy
        val template = ready.template
        val remaining = remainingEntries(template, current)
        val rows = remaining.map { buildRow(it, current, remaining.size > 1, progress.prefs) }
        val name = current.name ?: template.name
        val nameError = nameErrorOf(ready, name, current.takenName)
        val nameValid = validateTemplateName(name) == TemplateNameCheck.Valid && nameError == null
        val isDirty = name.trim() != template.name ||
            remaining.size != template.items.size ||
            remaining.any { isChanged(current, it) }
        val open = current.openFood?.let { index ->
            remaining.firstOrNull { it.index == index }?.let { entry ->
                // Live preview: the open row follows the amount typed in the sheet, not the committed one.
                entry to buildRow(
                    entry,
                    current,
                    remaining.size > 1,
                    progress.prefs,
                    current.openFoodAmount,
                    current.openFoodMethod,
                )
            }
        }
        return TemplateDetailsUiState.Content(
            title = template.name,
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
            adding = progress.adding,
            addFailed = progress.addFailed,
            canAdd = canAdd(rows, online, progress),
            openFood = open?.let { (entry, row) ->
                OpenFood(row, entry.food, micronutrients[entry.food.id], canSave = row.quantityError == null)
            },
        )
    }

    /** Añadir needs the server, a food left and valid amounts; the name does not matter. */
    private fun canAdd(rows: List<DetailsRow>, online: Boolean, progress: Progress) =
        online && !progress.busy && !progress.adding && rows.isNotEmpty() && rows.all { it.quantityError == null }

    private fun buildRow(
        entry: Entry,
        current: DetailsDraft,
        removable: Boolean,
        prefs: FoodPreferences?,
        pendingText: String? = null,
        pendingMethod: CookingMethod? = null,
    ): DetailsRow {
        val text = pendingText ?: amountText(current, entry)
        val amount = parseAmount(text)
        val check = amount?.let { validateQuantity(it) } ?: QuantityCheck.TooLow
        val error = check.takeUnless { it == QuantityCheck.Valid }
        val method = pendingMethod
            ?.let { CookingImpact.effectiveMethod(it, entry.food.baseUnit) }
            ?: methodOf(current, entry)
        val item = MealTemplateItem(entry.food, if (error == null) amount ?: 0.0 else 0.0, null, method)
        val nutrition = cookedItemNutrition(item, prefs.takeIf { error == null })
        val retainedG = cookedItemNutrition.retainedG(item, prefs)?.takeIf { error == null }
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
            cookingMethod = method,
            canCook = CookingImpact.appliesTo(entry.food.baseUnit),
            cookingImpact = retainedG?.let {
                CookingImpactPreview(CookingImpact.addedKcal(it), it)
            },
        )
    }
}
