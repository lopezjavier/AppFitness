package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraft
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraftCodec
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodPreferencesRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.usecase.CookingImpact
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import io.github.lopezjavier.appfitness.domain.usecase.FoodEnergyCheck
import io.github.lopezjavier.appfitness.domain.usecase.OffFoodToNewFoodDraft
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
import kotlinx.coroutines.flow.first
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
private const val KEY_COOKING = "add_food_cooking"
private const val KEY_CONFIDENCE = "add_food_estimate_confidence"
private const val MAX_CONFIDENCE = 10
private const val DEFAULT_AMOUNT = 100.0
private const val SINGLE = 1.0
private const val HALF = 0.5
private const val TENTHS = 10.0

/** What the sheet is built from once the food and the meals are loaded; [prefs] is null when they could not be read. */
private class Setup(val food: Food, val slots: List<MealSlot>, val prefs: FoodPreferences?)

private sealed interface Loaded {
    data object Loading : Loaded

    data object Failed : Loaded

    data class Ready(val setup: Setup) : Loaded
}

/** The sources of the sheet state besides the extras. */
private class Base(val loaded: Loaded, val draft: Draft?, val online: Boolean, val submitting: Boolean)

/**
 * The energy check of the loaded food: [warning] is null when the values agree (or could not be checked); [correction]
 * is the form prefill of a failing OFF food. Both are derived on every load, never persisted.
 */
private class EnergyCheck(val warning: EnergyWarning?, val correction: NewFoodDraft? = null)

/** The user's input: [portionId] is "" for the raw base unit (a saved state cannot hold null reliably). */
private data class Draft(
    val amountText: String,
    val portionId: String,
    val slotId: String?,
    val cookingMethod: CookingMethod = CookingMethod.Raw,
    /** Confidence 1..10 of the photo estimate now in the amount; 0 = none (a saved state cannot hold null reliably). */
    val estimateConfidence: Int = 0,
)

/**
 * Add-food sheet (US-17, US-22): the quantity in the base unit or in a named portion, the meal and the live
 * nutrition preview. The food is loaded by [foodId] so the sheet survives process death; the draft (amount, portion
 * and meal) lives in [savedState] (ADR-40). [initialSlotId] is the meal tapped in the Diary; without it the meal is
 * chosen by the time of day. Logging needs the server, so [AddFoodContent.canSubmit] is false offline.
 */
@Suppress("LongParameterList", "TooManyFunctions") // One dependency per port; one function per step.
class AddFoodViewModel(
    private val foodId: String,
    private val initialSlotId: String?,
    private val date: LocalDate,
    private val foodRepository: FoodRepository,
    private val foodDiary: FoodDiaryRepository,
    private val mealSlots: MealSlotRepository,
    private val foodPreferencesRepository: FoodPreferencesRepository,
    private val resolvePortion: ResolvePortion,
    private val scaleNutrition: ScaleNutrition,
    private val validateQuantity: ValidateQuantity,
    private val defaultMealSlot: DefaultMealSlot,
    private val clock: Clock,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val pick: Boolean = false,
) : ViewModel() {
    private val loaded = MutableStateFlow<Loaded>(Loaded.Loading)
    private val draft = MutableStateFlow<Draft?>(null)
    private val online = MutableStateFlow(true)
    private val submitting = MutableStateFlow(false)
    private val micronutrients = MutableStateFlow<MicronutrientsState>(MicronutrientsState.Loading)

    /** The energy check of an OFF food: null while it is being computed, then the warning it raises (or none). */
    private val energyCheck = MutableStateFlow<EnergyCheck?>(null)

    private val effectChannel = Channel<AddFoodEffect>(Channel.BUFFERED)
    val effects: Flow<AddFoodEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<AddFoodUiState> =
        combine(
            combine(loaded, draft, online, submitting, ::Base),
            combine(micronutrients, energyCheck) { micro, check -> micro to check?.warning },
            ::buildState,
        )
            .stateIn(viewModelScope, SharingStarted.Eagerly, AddFoodUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        load()
    }

    fun onEvent(event: AddFoodEvent) {
        when (event) {
            is AddFoodEvent.AmountChanged -> updateDraft { it.copy(amountText = event.text, estimateConfidence = 0) }

            is AddFoodEvent.AmountEstimated ->
                applyEstimate(event.amount, event.baselineAmountText, event.confidence)

            is AddFoodEvent.PortionSelected -> selectPortion(event.portionId)

            is AddFoodEvent.SlotSelected -> updateDraft { it.copy(slotId = event.slotId) }

            is AddFoodEvent.CookingMethodSelected -> selectCooking(event.method)

            AddFoodEvent.Submit -> submit()

            AddFoodEvent.CorrectOff -> correctOff()

            AddFoodEvent.Retry -> load()

            AddFoodEvent.Refresh -> load(silent = true)
        }
    }

    private fun load(silent: Boolean = false) {
        if (!silent) {
            loaded.value = Loaded.Loading
            micronutrients.value = MicronutrientsState.Loading
            energyCheck.value = null
        }
        viewModelScope.launch {
            val loadedParts = coroutineScope {
                val food = async { foodRepository.getById(foodId) }
                // Pick mode logs nothing, so it needs no meals.
                val slots = async { if (pick) Result.success(emptyList()) else mealSlots.listEnabled() }
                // Pick mode has no selector, so it does not read the preferences.
                val prefs = async { if (pick) null else foodPreferencesRepository.get().getOrNull() }
                Triple(food.await(), slots.await(), prefs.await())
            }
            val (food, slots, prefs) = loadedParts
            val loadedFood = food.getOrNull()
            val loadedSlots = slots.getOrNull()
            if (loadedFood == null || loadedSlots == null) {
                // A failed silent refresh keeps showing the food that was already loaded.
                if (!silent || loaded.value !is Loaded.Ready) loaded.value = Loaded.Failed
                return@launch
            }
            val previous = (loaded.value as? Loaded.Ready)?.setup?.food
            if (draft.value == null) {
                draft.value = initialDraft(loadedFood, loadedSlots)
            } else if (previous != null) {
                reconcilePortion(previous, loadedFood)
            }
            loaded.value = Loaded.Ready(Setup(loadedFood, loadedSlots, prefs))
            val extras = loadMicronutrients(loadedFood)
            energyCheck.value = if (loadedFood.isOff) energyCheckOf(loadedFood, extras) else EnergyCheck(null)
        }
    }

    /**
     * The food was reloaded (portions get new ids on every edit): the selected portion follows its label, and falls
     * back to the base unit when it no longer exists.
     */
    private fun reconcilePortion(previous: Food, reloaded: Food) {
        val current = draft.value ?: return
        if (current.portionId.isEmpty() || reloaded.portions.any { it.id == current.portionId }) return
        val label = previous.portions.firstOrNull { it.id == current.portionId }?.label
        val renewed = reloaded.portions.firstOrNull { it.label == label }
        updateDraft {
            if (renewed != null) {
                it.copy(portionId = renewed.id)
            } else {
                it.copy(portionId = "", amountText = defaultAmountText(reloaded, ""))
            }
        }
    }

    /** Optional info: an empty result or any failure hides the section and never blocks the sheet. */
    private suspend fun loadMicronutrients(food: Food): Micronutrients? {
        val found = foodRepository.micronutrients(food).getOrNull()
        micronutrients.value = found?.takeIf { !it.isEmpty }?.let { MicronutrientsState.Shown(it) }
            ?: MicronutrientsState.Hidden
        return found
    }

    /**
     * The check of an OFF food (07 section 4) on its own values; fibre and polyols come from the extras, alcohol from
     * the catalogue (none = 0 g). Any failed read means no warning: it is optional info and never blocks.
     */
    private suspend fun energyCheckOf(food: Food, extras: Micronutrients?): EnergyCheck {
        val alcohol = extras?.let { foodRepository.offAlcoholPer100(food.id) }
        if (extras == null || alcohol == null || alcohol.isFailure) return EnergyCheck(null)
        val per100 = alcohol.getOrNull()
        val warning = warningFor(food, extras, per100)
        return EnergyCheck(warning, warning?.let { OffFoodToNewFoodDraft(food, extras, per100) })
    }

    private fun warningFor(food: Food, extras: Micronutrients, alcohol: Double?): EnergyWarning? {
        fun grams(kind: MicronutrientKind) =
            extras.items.firstOrNull { it.kind == kind }?.let { it.unit.toGrams(it.amount) }

        val n = food.nutrition
        val result = FoodEnergyCheck.check(
            kcal = n.kcal,
            protein = n.proteinG,
            carbs = n.carbsG,
            fat = n.fatG,
            fibre = grams(MicronutrientKind.Fiber),
            polyols = grams(MicronutrientKind.Polyols),
            alcohol = alcohol,
        )
        val failing = result.level == EnergyCheckLevel.Mismatch || result.level == EnergyCheckLevel.ClearlyWrong
        return if (failing) EnergyWarning(result.level, n.kcal, result.computed, result.gap) else null
    }

    private fun correctOff() {
        val correction = energyCheck.value?.correction ?: return
        effectChannel.trySend(AddFoodEffect.CorrectOffFood(NewFoodDraftCodec.encode(correction), correction.barcode))
    }

    private fun initialDraft(food: Food, slots: List<MealSlot>): Draft {
        val slotId = savedState.get<String>(KEY_SLOT)?.takeIf { id -> slots.any { it.id == id } }
            ?: initialSlotId?.takeIf { id -> slots.any { it.id == id } }
            ?: defaultMealSlot(clock.now().toLocalDateTime(timeZone).time, slots)?.id
        val portionId = savedState.get<String>(KEY_PORTION).orEmpty().takeIf { id -> food.portions.any { it.id == id } }
            .orEmpty()
        val amount = savedState.get<String>(KEY_AMOUNT) ?: defaultAmountText(food, portionId)
        val method = CookingMethod.fromWireName(savedState.get<String>(KEY_COOKING)) ?: CookingMethod.Raw
        val confidence = savedState.get<Int>(KEY_CONFIDENCE) ?: 0
        return Draft(amount, portionId, slotId, method, confidence)
    }

    /** Ignored for drinks, in pick mode (the comida editor chooses its own) and while the preferences are unknown. */
    private fun selectCooking(method: CookingMethod) {
        val setup = (loaded.value as? Loaded.Ready)?.setup ?: return
        if (!showCooking(setup)) return
        updateDraft { it.copy(cookingMethod = method) }
    }

    private fun showCooking(setup: Setup): Boolean =
        !pick && setup.prefs != null && CookingImpact.appliesTo(setup.food.baseUnit)

    private fun selectPortion(portionId: String?) {
        val food = (loaded.value as? Loaded.Ready)?.setup?.food ?: return
        val id = portionId.orEmpty()
        if (id.isNotEmpty() && food.portions.none { it.id == id }) return
        updateDraft { it.copy(portionId = id, amountText = defaultAmountText(food, id), estimateConfidence = 0) }
    }

    private fun applyEstimate(amount: Double, baselineAmountText: String, confidence: Int?) {
        if (draft.value?.amountText != baselineAmountText) return
        updateDraft {
            it.copy(
                portionId = "",
                amountText = estimatedAmountText(amount),
                estimateConfidence = confidence?.takeIf { score -> score in 1..MAX_CONFIDENCE } ?: 0,
            )
        }
    }

    private fun updateDraft(change: (Draft) -> Draft) {
        val updated = change(draft.value ?: return)
        draft.value = updated
        savedState[KEY_AMOUNT] = updated.amountText
        savedState[KEY_PORTION] = updated.portionId
        updated.slotId?.let { savedState[KEY_SLOT] = it }
        savedState[KEY_COOKING] = updated.cookingMethod.wireName
        savedState[KEY_CONFIDENCE] = updated.estimateConfidence
    }

    private fun submit() {
        val ready = (loaded.value as? Loaded.Ready)?.setup ?: return
        if (ready.food.isOff) {
            submitOff(ready)
        } else if (pick) {
            pickFood(ready)
        } else {
            logEntry(ready)
        }
    }

    private fun logEntry(ready: Setup) {
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

    private fun pickFood(setup: Setup) {
        val picked = draft.value?.let { pickedOf(setup, it) } ?: return
        effectChannel.trySend(picked)
    }

    private fun pickedOf(setup: Setup, current: Draft): AddFoodEffect.Picked? {
        val content = buildContent(setup, current, online.value, submitting.value).takeIf { it.canSubmit }
        val amount = content?.resolvedAmount ?: return null
        val portion = setup.food.portions.firstOrNull { it.id == current.portionId }
        return AddFoodEffect.Picked(
            foodId = setup.food.id,
            amount = amount,
            portionLabel = portion?.let { portionLabel(it, parseAmount(current.amountText)) },
        )
    }

    /**
     * Adds an Open Food Facts food that passes the energy check: the personal copy is made (or reused), the entry (or
     * the pick) refers to the copy. The snapshot keeps the OFF values, which are the copy's values. A failing food
     * never gets here: [AddFoodContent.canSubmit] is false and the user corrects it first.
     */
    private fun submitOff(setup: Setup) {
        val current = draft.value ?: return
        val entry = if (pick) null else newEntry(setup, current)
        val picked = if (pick) pickedOf(setup, current) else null
        if (entry == null && picked == null) return
        submitting.value = true
        viewModelScope.launch {
            val effect = addOff(setup.food.id, entry, picked)
            submitting.value = false
            effect?.let { effectChannel.send(it) }
        }
    }

    /** The effect of adding the OFF food [offId]; null when it is blocked (the check failed) or the server refused. */
    private suspend fun addOff(offId: String, entry: NewFoodEntry?, picked: AddFoodEffect.Picked?): AddFoodEffect? {
        val blocked = energyCheck.first { it != null }?.warning != null
        val outcome = if (blocked) null else foodRepository.copyOffFood(offId).map { it.id }
        val refused = outcome?.appErrorOrNull() == AppError.FoodEnergyCheckFailed
        // The server found what the client did not: reload so the sheet shows the state of the catalogue.
        if (refused) load(silent = true)
        return if (outcome == null || refused) null else settle(outcome, entry, picked)
    }

    private suspend fun settle(outcome: Result<String>, entry: NewFoodEntry?, picked: AddFoodEffect.Picked?) =
        when (val copyId = outcome.getOrNull()) {
            null -> AddFoodEffect.SubmitFailed(outcome.appErrorOrNull())
            else -> picked?.copy(foodId = copyId) ?: logCopy(entry, copyId)
        }

    private suspend fun logCopy(entry: NewFoodEntry?, copyId: String): AddFoodEffect {
        val result = entry?.let { foodDiary.addEntry(it.copy(foodId = copyId)) }
        val added = result != null && result.isSuccess
        return if (added) AddFoodEffect.Added else AddFoodEffect.SubmitFailed(result?.appErrorOrNull())
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
                cookingMethod = content.cookingMethod,
                cookingOilRetainedG = content.retainedOilG,
            )
        }
    }

    private fun buildState(base: Base, extras: Pair<MicronutrientsState, EnergyWarning?>): AddFoodUiState = when {
        base.loaded == Loaded.Failed -> AddFoodUiState.Error

        base.loaded !is Loaded.Ready || base.draft == null -> AddFoodUiState.Loading

        else -> AddFoodUiState.Content(
            buildContent(base.loaded.setup, base.draft, base.online, base.submitting, extras.first, extras.second),
        )
    }

    private fun buildContent(
        setup: Setup,
        draft: Draft,
        online: Boolean,
        submitting: Boolean,
        micronutrients: MicronutrientsState = MicronutrientsState.Hidden,
        energyWarning: EnergyWarning? = null,
    ): AddFoodContent {
        val portion = setup.food.portions.firstOrNull { it.id == draft.portionId }
        val typed = parseAmount(draft.amountText)
        val typedCheck = typed?.let(validateQuantity::invoke) ?: QuantityCheck.TooLow
        val resolved = if (typedCheck == QuantityCheck.Valid && typed != null) resolvePortion(typed, portion) else null
        val check = if (resolved != null) validateQuantity(resolved) else typedCheck
        val valid = check == QuantityCheck.Valid && resolved != null
        val prefs = setup.prefs.takeIf { showCooking(setup) }
        val method = if (prefs != null) draft.cookingMethod else CookingMethod.Raw
        // The added fat depends on the raw weight in grams (ADR-65); an invalid amount adds none.
        val retained = if (prefs != null && valid && resolved != null) {
            CookingImpact.addedFatG(method, resolved, prefs)
        } else {
            0.0
        }
        val scaled = resolved?.takeIf { valid }?.let { scaleNutrition(setup.food.nutrition, it) }
        return AddFoodContent(
            food = setup.food,
            amountText = draft.amountText,
            selectedPortionId = portion?.id,
            slots = setup.slots,
            selectedSlotId = draft.slotId,
            resolvedAmount = resolved.takeIf { valid },
            preview = scaled?.let { CookingImpact.apply(it, retained) },
            quantityError = check.takeIf { it != QuantityCheck.Valid },
            isOffline = !online,
            submitting = submitting,
            micronutrients = micronutrients,
            energyWarning = energyWarning,
            pick = pick,
            cookingMethod = method,
            estimateConfidence = draft.estimateConfidence.takeIf { it > 0 },
            showCooking = prefs != null,
            retainedOilG = retained,
            cookingImpact = if (method == CookingMethod.Raw) {
                null
            } else {
                CookingImpactPreview(CookingImpact.addedKcal(retained), retained)
            },
        )
    }
}

private fun defaultAmountText(food: Food, portionId: String): String =
    if (portionId.isNotEmpty()) formatNumber(SINGLE) else formatNumber(food.servingAmount ?: DEFAULT_AMOUNT)

/** An estimate as typed text: whole units half-up, or one decimal when it would round to 0 (never "0"). */
private fun estimatedAmountText(amount: Double): String {
    val whole = floor(amount + HALF)
    return if (whole >= SINGLE) formatNumber(whole) else formatNumber(floor(amount * TENTHS + HALF) / TENTHS)
}

internal fun portionLabel(portion: FoodPortion, count: Double?): String =
    if (count == null || count == SINGLE) portion.label else "${formatNumber(count)} × ${portion.label}"

internal fun parseAmount(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

internal fun formatNumber(value: Double): String =
    if (value == floor(value)) value.toLong().toString() else value.toString()
