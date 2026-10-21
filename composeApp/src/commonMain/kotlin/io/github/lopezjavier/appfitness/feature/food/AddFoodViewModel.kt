package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.floor

private const val KEY_AMOUNT = "add_food_amount"
private const val KEY_PORTION = "add_food_portion"
private const val KEY_SLOT = "add_food_slot"
private const val DEFAULT_AMOUNT = 100.0
private const val SINGLE = 1.0

/** What the sheet is built from once the food and the meals are loaded. */
private class Setup(val food: Food, val slots: List<MealSlot>)

/** The user's input: [portionId] is "" for the raw base unit (a saved state cannot hold null reliably). */
private data class Draft(val amountText: String, val portionId: String, val slotId: String?)

/**
 * Add-food sheet (US-17, US-22): the quantity in the base unit or in a named portion, the meal and the live
 * nutrition preview. The food is loaded by [foodId] so the sheet survives process death; the draft (amount, portion
 * and meal) lives in [savedState] (ADR-40). [initialSlotId] is the meal tapped in the Diary; without it the meal is
 * chosen by the time of day. Logging needs the server, so [AddFoodContent.canSubmit] is false offline.
 */
@Suppress("LongParameterList") // One dependency per port.
class AddFoodViewModel(
    private val foodId: String,
    private val initialSlotId: String?,
    private val date: LocalDate,
    private val foodRepository: FoodRepository,
    private val foodDiary: FoodDiaryRepository,
    private val mealSlots: MealSlotRepository,
    private val resolvePortion: ResolvePortion,
    private val scaleNutrition: ScaleNutrition,
    private val validateQuantity: ValidateQuantity,
    private val defaultMealSlot: DefaultMealSlot,
    private val clock: Clock,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private sealed interface Loaded {
        data object Loading : Loaded

        data object Failed : Loaded

        data class Ready(val setup: Setup) : Loaded
    }

    private val loaded = MutableStateFlow<Loaded>(Loaded.Loading)
    private val draft = MutableStateFlow<Draft?>(null)
    private val online = MutableStateFlow(true)
    private val submitting = MutableStateFlow(false)
    private val micronutrients = MutableStateFlow<MicronutrientsState>(MicronutrientsState.Loading)

    private val effectChannel = Channel<AddFoodEffect>(Channel.BUFFERED)
    val effects: Flow<AddFoodEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<AddFoodUiState> =
        combine(loaded, draft, online, submitting, micronutrients, ::buildState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, AddFoodUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        load()
    }

    fun onEvent(event: AddFoodEvent) {
        when (event) {
            is AddFoodEvent.AmountChanged -> updateDraft { it.copy(amountText = event.text) }
            is AddFoodEvent.PortionSelected -> selectPortion(event.portionId)
            is AddFoodEvent.SlotSelected -> updateDraft { it.copy(slotId = event.slotId) }
            AddFoodEvent.Submit -> submit()
            AddFoodEvent.Retry -> load()
        }
    }

    private fun load() {
        loaded.value = Loaded.Loading
        micronutrients.value = MicronutrientsState.Loading
        viewModelScope.launch {
            val (food, slots) = coroutineScope {
                val food = async { foodRepository.getById(foodId) }
                val slots = async { mealSlots.listEnabled() }
                food.await() to slots.await()
            }
            val loadedFood = food.getOrNull()
            val loadedSlots = slots.getOrNull()
            if (loadedFood == null || loadedSlots == null) {
                loaded.value = Loaded.Failed
                return@launch
            }
            if (draft.value == null) draft.value = initialDraft(loadedFood, loadedSlots)
            loaded.value = Loaded.Ready(Setup(loadedFood, loadedSlots))
            loadMicronutrients(loadedFood)
        }
    }

    /** Optional info: an empty result or any failure hides the section and never blocks the sheet. */
    private suspend fun loadMicronutrients(food: Food) {
        micronutrients.value = foodRepository.micronutrients(food).getOrNull()
            ?.takeIf { !it.isEmpty }
            ?.let { MicronutrientsState.Shown(it) }
            ?: MicronutrientsState.Hidden
    }

    private fun initialDraft(food: Food, slots: List<MealSlot>): Draft {
        val slotId = savedState.get<String>(KEY_SLOT)?.takeIf { id -> slots.any { it.id == id } }
            ?: initialSlotId?.takeIf { id -> slots.any { it.id == id } }
            ?: defaultMealSlot(clock.now().toLocalDateTime(timeZone).time, slots)?.id
        val portionId = savedState.get<String>(KEY_PORTION).orEmpty().takeIf { id -> food.portions.any { it.id == id } }
            .orEmpty()
        val amount = savedState.get<String>(KEY_AMOUNT) ?: defaultAmountText(food, portionId)
        return Draft(amount, portionId, slotId)
    }

    private fun selectPortion(portionId: String?) {
        val food = (loaded.value as? Loaded.Ready)?.setup?.food ?: return
        val id = portionId.orEmpty()
        if (id.isNotEmpty() && food.portions.none { it.id == id }) return
        updateDraft { it.copy(portionId = id, amountText = defaultAmountText(food, id)) }
    }

    private fun updateDraft(change: (Draft) -> Draft) {
        val updated = change(draft.value ?: return)
        draft.value = updated
        savedState[KEY_AMOUNT] = updated.amountText
        savedState[KEY_PORTION] = updated.portionId
        updated.slotId?.let { savedState[KEY_SLOT] = it }
    }

    private fun submit() {
        val ready = (loaded.value as? Loaded.Ready)?.setup ?: return
        val entry = draft.value?.let { newEntry(ready, it) } ?: return
        submitting.value = true
        viewModelScope.launch {
            val result = foodDiary.addEntry(entry)
            submitting.value = false
            effectChannel.send(
                if (result.isSuccess) AddFoodEffect.Added else AddFoodEffect.SubmitFailed(result.appErrorOrNull()),
            )
        }
    }

    /** The entry to log for [current], or null when the sheet cannot be submitted now. */
    private fun newEntry(setup: Setup, current: Draft): NewFoodEntry? {
        val content = buildContent(setup, current, online.value, submitting.value).takeIf { it.canSubmit }
        val amount = content?.resolvedAmount
        val preview = content?.preview
        val slotId = content?.selectedSlotId
        val portion = setup.food.portions.firstOrNull { it.id == current.portionId }
        return if (amount == null || preview == null || slotId == null) {
            null
        } else {
            NewFoodEntry(
                date = date,
                mealSlotId = slotId,
                foodId = setup.food.id,
                foodName = setup.food.name,
                amount = amount,
                portionLabel = portion?.let { portionLabel(it, parseAmount(current.amountText)) },
                snapshot = preview,
            )
        }
    }

    private fun buildState(
        loaded: Loaded,
        draft: Draft?,
        online: Boolean,
        submitting: Boolean,
        micronutrients: MicronutrientsState,
    ): AddFoodUiState = when {
        loaded == Loaded.Failed -> AddFoodUiState.Error
        loaded !is Loaded.Ready || draft == null -> AddFoodUiState.Loading
        else -> AddFoodUiState.Content(buildContent(loaded.setup, draft, online, submitting, micronutrients))
    }

    private fun buildContent(
        setup: Setup,
        draft: Draft,
        online: Boolean,
        submitting: Boolean,
        micronutrients: MicronutrientsState = MicronutrientsState.Hidden,
    ): AddFoodContent {
        val portion = setup.food.portions.firstOrNull { it.id == draft.portionId }
        val typed = parseAmount(draft.amountText)
        val typedCheck = typed?.let(validateQuantity::invoke) ?: QuantityCheck.TooLow
        val resolved = if (typedCheck == QuantityCheck.Valid && typed != null) resolvePortion(typed, portion) else null
        val check = if (resolved != null) validateQuantity(resolved) else typedCheck
        val valid = check == QuantityCheck.Valid && resolved != null
        return AddFoodContent(
            food = setup.food,
            amountText = draft.amountText,
            selectedPortionId = portion?.id,
            slots = setup.slots,
            selectedSlotId = draft.slotId,
            resolvedAmount = resolved.takeIf { valid },
            preview = resolved?.takeIf { valid }?.let { scaleNutrition(setup.food.nutrition, it) },
            quantityError = check.takeIf { it != QuantityCheck.Valid },
            isOffline = !online,
            submitting = submitting,
            micronutrients = micronutrients,
        )
    }
}

private fun defaultAmountText(food: Food, portionId: String): String =
    if (portionId.isNotEmpty()) formatNumber(SINGLE) else formatNumber(food.servingAmount ?: DEFAULT_AMOUNT)

internal fun portionLabel(portion: FoodPortion, count: Double?): String =
    if (count == null || count == SINGLE) portion.label else "${formatNumber(count)} × ${portion.label}"

internal fun parseAmount(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

internal fun formatNumber(value: Double): String =
    if (value == floor(value)) value.toLong().toString() else value.toString()
