package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredFoodPhotoRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoDraft
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraftCodec
import io.github.lopezjavier.appfitness.domain.model.NewFoodError
import io.github.lopezjavier.appfitness.domain.model.NewFoodErrors
import io.github.lopezjavier.appfitness.domain.model.NewFoodValidation
import io.github.lopezjavier.appfitness.domain.model.toNewFoodDraft
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.PhotoCapture
import io.github.lopezjavier.appfitness.domain.port.UnconfiguredPhotoCapture
import io.github.lopezjavier.appfitness.domain.repository.FoodPhotoRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import io.github.lopezjavier.appfitness.domain.usecase.AlcoholByVolume
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoRetryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val KEY_AI_NOTE = "create_food_ai_note"
private const val KEY_STARTED = "create_food_started"
private const val KEY_BARCODE = "create_food_barcode"
private const val KEY_UNIT = "create_food_unit"
private const val KEY_ORIGINAL_UNIT = "create_food_original_unit"
private const val KEY_UNIT_LOCKED = "create_food_unit_locked"
private const val KEY_UNIT_REFUSED = "create_food_unit_refused"
private const val KEY_TOUCHED = "create_food_touched"
private const val KEY_PORTIONS = "create_food_portions"
private const val KEY_SECTIONS = "create_food_sections"
private const val KEY_ALCOHOL_MODE = "create_food_alcohol_mode"
private const val KEY_ALCOHOL_ABV = "create_food_alcohol_abv"
private const val KEY_RETRY_TOKEN = "create_food_retry_token"
private const val KEY_RETRY_MODE = "create_food_retry_mode"
private const val KEY_FIELD_PREFIX = "create_food_field_"
private const val MAX_PORTIONS = 10
private val UNITS = setOf("g", "ml")

/** The collapsible section that holds the polyols field; expanded while the energy warning shows. */
private val SECTION_OF_CHECK_FIELDS = CreateFoodSection.Fats

/** Where the edit-mode read of the food stands; create mode is always [Idle]. */
private enum class LoadState { Idle, Loading, Failed }

/**
 * Create / edit food form (US-18, US-22, US-47, US-52): a personal food with optional named portions and any label
 * nutrient. With [editFoodId] it edits that manual food: the food is read once, the base unit is editable until an
 * entry or comida references it ([CreateFoodUiState.unitLocked]) and "Guardar" calls `updateFood`; when the server
 * refuses a stale unit change the unit reverts to the stored one, locks and the rest of the draft is kept; a
 * discard writes nothing because the repository is only called on [CreateFoodEvent.Save]. The draft
 * lives in [savedState] (ADR-40) and is prefilled (from [barcode] / [name] or from the stored food) only the first
 * time. Blank protein, carbs and fat count as 0; kcal is required. Saving needs the server, so
 * [CreateFoodUiState.canSave] is false offline.
 *
 * The energy check is a hard block (ADR-104): [CreateFoodUiState.energyWarning] is derived from the draft, so it shows
 * live, survives process death by itself and disables "Guardar" while the values fail. A form that came from a failing
 * AI estimate ([retryToken]) can repeat it from [CreateFoodEvent.RetryEstimate]; the photo stays in memory in
 * [retryStore] and is released when the form is saved, closed, or the retry produces values that pass.
 */
@Suppress("TooManyFunctions", "LongParameterList") // One function per step of the form; one dependency per port.
class CreateFoodViewModel(
    barcode: String?,
    name: String?,
    private val foodRepository: FoodRepository,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val editFoodId: String? = null,
    prefill: String? = null,
    retryToken: String? = null,
    private val photoRepository: FoodPhotoRepository = UnconfiguredFoodPhotoRepository(),
    private val photoCapture: PhotoCapture = UnconfiguredPhotoCapture(),
    private val retryStore: FoodPhotoRetryStore = FoodPhotoRetryStore(photoCapture),
) : ViewModel() {
    /**
     * What the form held when the user started (the prefill, or the stored food once read): the edit is "dirty" only
     * while the draft differs from it. Null for an edit restored after process death, where the touched fields tell.
     */
    private var baseline: FormDraft? = if (editFoodId == null) createDraft(barcode, name, prefill) else null
    private val draft = MutableStateFlow(restoreOrPrefill(barcode, name, prefill))
    private val aiNote = MutableStateFlow(savedState.get<Boolean>(KEY_AI_NOTE) == true)
    private val online = MutableStateFlow(true)
    private val saving = MutableStateFlow(false)
    private val saveFailed = MutableStateFlow(false)

    /** The token of the photo kept for a retry (create mode only); null once released or when there is none. */
    private var photoToken: String? =
        if (editFoodId == null) retryToken ?: savedState.get<String>(KEY_RETRY_TOKEN) else null
    private val retryState = MutableStateFlow(initialRetryState())

    /** The failing estimate came from a typed text (not a photo): the "gone" copy after process death says so. */
    private val retryFromText: Boolean = photoToken?.let {
        (retryStore.get(it)?.mode?.name ?: savedState.get<String>(KEY_RETRY_MODE)) == FoodPhotoMode.Text.name
    } == true

    /** The unit as stored (edit mode); the unit goes back to it when the server refuses a change. */
    private var originalUnit: String? = savedState.get<String>(KEY_ORIGINAL_UNIT)
    private val unitLocked = MutableStateFlow(savedState.get<Boolean>(KEY_UNIT_LOCKED) == true)
    private val unitRefused = MutableStateFlow(savedState.get<Boolean>(KEY_UNIT_REFUSED) == true)
    private val sections = MutableStateFlow(
        savedState.get<List<String>>(KEY_SECTIONS).orEmpty().mapNotNull { id ->
            CreateFoodSection.entries.firstOrNull { it.name == id }
        }.toSet(),
    )
    private val alcoholMode = MutableStateFlow(
        savedState.get<String>(KEY_ALCOHOL_MODE)?.let { name -> AlcoholMode.entries.firstOrNull { it.name == name } }
            ?: AlcoholMode.Grams,
    )
    private val alcoholAbv = MutableStateFlow(savedState.get<String>(KEY_ALCOHOL_ABV).orEmpty())
    private val loadState = MutableStateFlow(
        if (editFoodId != null && savedState.get<Boolean>(KEY_STARTED) != true) LoadState.Loading else LoadState.Idle,
    )

    private val effectChannel = Channel<CreateFoodEffect>(Channel.BUFFERED)
    val effects: Flow<CreateFoodEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<CreateFoodUiState> =
        combine(draft, online, saving, saveFailed, loadState, ::buildState)
            .combine(sections) { state, expanded -> state.copy(expandedSections = expandedWith(expanded, state)) }
            .combine(alcoholMode) { state, mode -> state.copy(alcoholMode = mode) }
            .combine(alcoholAbv) { state, abv -> state.withAlcoholAbv(abv) }
            .combine(unitLocked) { state, locked -> state.copy(unitLocked = locked) }
            .combine(unitRefused) { state, refused -> state.copy(unitRefused = refused) }
            .combine(aiNote) { state, note -> state.copy(aiEstimateNote = note) }
            .combine(retryState) { state, retry ->
                state.copy(
                    retryState = retry,
                    retryFromText = retryFromText,
                    canSave = state.canSave && retry != RetryState.Running,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                buildState(draft.value, true, false, false, loadState.value).let { initial ->
                    initial.copy(alcoholMode = alcoholMode.value).withAlcoholAbv(alcoholAbv.value).copy(
                        expandedSections = expandedWith(sections.value, initial),
                        unitLocked = unitLocked.value,
                        unitRefused = unitRefused.value,
                        aiEstimateNote = aiNote.value,
                        retryState = retryState.value,
                        retryFromText = retryFromText,
                        canSave = initial.canSave && retryState.value != RetryState.Running,
                    )
                },
            )

    init {
        photoToken?.let { token ->
            savedState[KEY_RETRY_TOKEN] = token
            retryStore.get(token)?.let { savedState[KEY_RETRY_MODE] = it.mode.name }
        }
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        if (loadState.value == LoadState.Loading) load()
    }

    /** Available while the photo is in memory; after process death the store is empty, so the retry is gone. */
    private fun initialRetryState(): RetryState = when (val token = photoToken) {
        null -> RetryState.None
        else -> if (retryStore.has(token)) RetryState.Available else RetryState.PhotoGone
    }

    /** The section of the polyols field is shown while the warning needs the user to look at it (R-59). */
    private fun expandedWith(expanded: Set<CreateFoodSection>, state: CreateFoodUiState): Set<CreateFoodSection> =
        if (state.energyWarning != null) expanded + SECTION_OF_CHECK_FIELDS else expanded

    @Suppress("CyclomaticComplexMethod") // One branch per event.
    fun onEvent(event: CreateFoodEvent) {
        when (event) {
            is CreateFoodEvent.FieldChanged -> changeField(event)

            is CreateFoodEvent.NutrientChanged -> changeNutrient(event)

            // Only "g" and "ml" exist; the unit of a food that is in use never changes.
            is CreateFoodEvent.UnitSelected -> selectUnit(event.unit)

            is CreateFoodEvent.AlcoholModeSelected -> selectAlcoholMode(event.mode)

            is CreateFoodEvent.AlcoholAbvChanged -> changeAlcoholAbv(event.text)

            CreateFoodEvent.AddPortion -> update {
                if (it.portions.size >=
                    MAX_PORTIONS
                ) {
                    it
                } else {
                    it.copy(portions = it.portions + PortionRow("", "", false))
                }
            }

            is CreateFoodEvent.RemovePortion -> update {
                it.copy(portions = it.portions.filterIndexed { i, _ -> i != event.index })
            }

            is CreateFoodEvent.PortionLabelChanged -> updateRow(event.index) {
                it.copy(label = event.text, touched = true)
            }

            is CreateFoodEvent.PortionAmountChanged -> updateRow(event.index) {
                it.copy(amount = event.text, touched = true)
            }

            is CreateFoodEvent.ToggleSection -> toggleSection(event.section)

            CreateFoodEvent.Save -> save()

            CreateFoodEvent.RetryEstimate -> retryEstimate()

            CreateFoodEvent.Retry -> retry()
        }
    }

    private fun changeField(event: CreateFoodEvent.FieldChanged) {
        // Typing the grams makes the grados stale: the grams are the value.
        if (event.field == CreateFoodField.Alcohol) setAlcoholAbv("")
        update {
            it.copy(fields = it.fields + (event.field to event.text), touched = it.touched + event.field.name)
        }
    }

    private fun changeNutrient(event: CreateFoodEvent.NutrientChanged) {
        update {
            it.copy(nutrients = it.nutrients + (event.kind to event.text), touched = it.touched + event.kind.name)
        }
    }

    private fun selectUnit(unit: String) {
        if (unitLocked.value || unit !in UNITS) return
        update { it.copy(baseUnit = unit) }
        // Grados only exist for ml: the grams stay, the mode and the typed grados go.
        if (unit == "g") {
            setAlcoholMode(AlcoholMode.Grams)
            setAlcoholAbv("")
        }
    }

    private fun selectAlcoholMode(mode: AlcoholMode) {
        if (mode == AlcoholMode.Grados && draft.value.baseUnit != "ml") return
        setAlcoholMode(mode)
    }

    private fun changeAlcoholAbv(text: String) {
        if (draft.value.baseUnit != "ml") return
        setAlcoholAbv(text)
        val grams = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
            ?.let { formatEditable(AlcoholByVolume.toGramsPer100(it)) }.orEmpty()
        update {
            it.copy(
                fields = it.fields + (CreateFoodField.Alcohol to grams),
                touched =
                it.touched + CreateFoodField.Alcohol.name,
            )
        }
    }

    private fun setAlcoholMode(mode: AlcoholMode) {
        alcoholMode.value = mode
        savedState[KEY_ALCOHOL_MODE] = mode.name
    }

    private fun setAlcoholAbv(text: String) {
        alcoholAbv.value = text
        savedState[KEY_ALCOHOL_ABV] = text
    }

    private fun CreateFoodUiState.withAlcoholAbv(abv: String) = copy(
        alcoholAbv = abv,
        alcoholConvertedG = alcohol.takeIf { alcoholMode == AlcoholMode.Grados && abv.isNotBlank() && it.isNotBlank() },
    )

    private fun toggleSection(section: CreateFoodSection) {
        val toggled = sections.value.let { if (section in it) it - section else it + section }
        sections.value = toggled
        savedState[KEY_SECTIONS] = ArrayList(toggled.map { it.name })
    }

    private fun load() {
        val id = editFoodId ?: return
        loadState.value = LoadState.Loading
        viewModelScope.launch {
            foodRepository.editableFood(id).fold(
                onSuccess = {
                    // A draft typed meanwhile (restored state) is never overwritten.
                    if (savedState.get<Boolean>(KEY_STARTED) != true) {
                        FormDraft.from(it).also { prefilled ->
                            baseline = prefilled
                            draft.value = prefilled
                            originalUnit = prefilled.baseUnit
                            unitLocked.value = it.inUse
                            savedState[KEY_ORIGINAL_UNIT] = prefilled.baseUnit
                            savedState[KEY_UNIT_LOCKED] = it.inUse
                            persist(prefilled)
                        }
                    }
                    loadState.value = LoadState.Idle
                },
                onFailure = { loadState.value = LoadState.Failed },
            )
        }
    }

    private fun update(change: (FormDraft) -> FormDraft) {
        val updated = change(draft.value)
        draft.value = updated
        saveFailed.value = false
        clearUnitRefusal()
        clearAiNote()
        persist(updated)
    }

    /** The refusal message goes away on the next user action; the unit stays locked (the food is referenced). */
    private fun clearUnitRefusal() {
        unitRefused.value = false
        savedState.remove<Boolean>(KEY_UNIT_REFUSED)
    }

    private fun clearAiNote() {
        aiNote.value = false
        savedState[KEY_AI_NOTE] = false
    }

    private fun updateRow(index: Int, change: (PortionRow) -> PortionRow) = update { current ->
        current.copy(portions = current.portions.mapIndexed { i, row -> if (i == index) change(row) else row })
    }

    private fun save() {
        val current = draft.value
        val busy = saving.value || retryState.value == RetryState.Running
        if (busy || loadState.value != LoadState.Idle || !isSavable(current, online.value)) return
        saving.value = true
        saveFailed.value = false
        clearUnitRefusal()
        viewModelScope.launch {
            val result: Result<Food> = if (editFoodId != null) {
                foodRepository.updateFood(current.toUpdatedFood(editFoodId))
            } else {
                foodRepository.createFood(current.toNewFood())
            }
            saving.value = false
            result.fold(
                onSuccess = {
                    releasePhoto()
                    effectChannel.send(CreateFoodEffect.Saved(it.id))
                },
                onFailure = { onSaveFailed(it) },
            )
        }
    }

    private suspend fun onSaveFailed(failure: Throwable) {
        when ((failure as? AppException)?.error) {
            AppError.FoodInUse -> refuseUnitChange()

            // The warning derived from the draft already says why: no failure message.
            AppError.FoodEnergyCheckFailed -> Unit

            else -> {
                saveFailed.value = true
                effectChannel.send(CreateFoodEffect.SaveFailed)
            }
        }
    }

    private fun retry() {
        if (loadState.value == LoadState.Failed) load()
    }

    /** Releases the kept photo (file included) and forgets the retry; called on save, close and a passing retry. */
    private fun releasePhoto() {
        photoToken?.let(retryStore::release)
        photoToken = null
        savedState.remove<String>(KEY_RETRY_TOKEN)
        savedState.remove<String>(KEY_RETRY_MODE)
        retryState.value = RetryState.None
    }

    /** Releases the kept photo; called when the ViewModel is cleared. */
    internal fun release() = releasePhoto()

    override fun onCleared() = release()

    /**
     * Asks the AI again with the current values and why they fail. Only the nutrition fields are replaced; when the
     * new values pass, the photo is released and the retry goes away, otherwise it stays available.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun retryEstimate() {
        val token = photoToken
        val previous = draft.value.toPreviousEstimate()
        if (retryState.value != RetryState.Available || previous == null || !online.value) return
        val kept = token?.let(retryStore::get)
        if (token == null || kept == null) {
            retryState.value = RetryState.PhotoGone
            return
        }
        retryState.value = RetryState.Running
        viewModelScope.launch {
            val outcome: Result<FoodPhotoDraft> = try {
                val text = kept.text
                if (text != null) {
                    photoRepository.estimateText(text, previous)
                } else {
                    val encoded = Base64.encode(photoCapture.readJpeg(requireNotNull(kept.handle)))
                    photoRepository.estimate(kept.mode, encoded, kept.comment, previous)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The reason is deliberately dropped: nothing about the photo or the response is kept.
                Result.failure(IllegalStateException("estimate failed"))
            }
            outcome.fold(
                onSuccess = { applyEstimate(it, kept.mode) },
                onFailure = {
                    retryState.value = RetryState.Available
                    effectChannel.send(CreateFoodEffect.RetryFailed)
                },
            )
        }
    }

    private fun applyEstimate(estimate: FoodPhotoDraft, mode: FoodPhotoMode) {
        val corrected = draft.value.withEstimate(FormDraft.from(estimate.toNewFoodDraft(mode)))
        update { corrected }
        // The values are the AI's again, until the user edits something.
        aiNote.value = true
        savedState[KEY_AI_NOTE] = true
        if (corrected.text(MicronutrientKind.Polyols).isNotBlank()) toggleSectionOn(SECTION_OF_CHECK_FIELDS)
        if (corrected.energyIssue() == null) releasePhoto() else retryState.value = RetryState.Available
    }

    private fun toggleSectionOn(section: CreateFoodSection) {
        val expanded = sections.value + section
        sections.value = expanded
        savedState[KEY_SECTIONS] = ArrayList(expanded.map { it.name })
    }

    private fun warningOf(value: FormDraft): EnergyWarning? = value.energyIssue()?.let {
        EnergyWarning(it.result, it.kcal, it.computed, it.gap)
    }

    /** The server found the food referenced: back to the stored unit, locked, with the rest of the draft intact. */
    private fun refuseUnitChange() {
        val stored = originalUnit ?: draft.value.baseUnit
        update { it.copy(baseUnit = stored) }
        unitLocked.value = true
        unitRefused.value = true
        savedState[KEY_UNIT_LOCKED] = true
        savedState[KEY_UNIT_REFUSED] = true
    }

    private fun restoreOrPrefill(barcode: String?, name: String?, prefill: String?): FormDraft {
        if (savedState.get<Boolean>(KEY_STARTED) == true) return restore()
        val blank = createDraft(barcode, name, prefill)
        if (editFoodId == null && NewFoodDraftCodec.decode(prefill) != null) savedState[KEY_AI_NOTE] = true
        // In edit mode the draft only starts once the stored food has been read.
        return if (editFoodId == null) blank.also(::persist) else blank
    }

    private fun createDraft(barcode: String?, name: String?, prefill: String?): FormDraft {
        val photoDraft = if (editFoodId == null) NewFoodDraftCodec.decode(prefill) else null
        return photoDraft?.let(FormDraft::from) ?: FormDraft.blank(name, barcode)
    }

    private fun isDirty(value: FormDraft): Boolean {
        val start = baseline
        if (start != null) return !value.sameContentAs(start)
        return value.touched.isNotEmpty() || value.portions.any { it.touched } || value.baseUnit != originalUnit
    }

    private fun restore() = FormDraft(
        fields = CreateFoodField.entries.associateWith {
            savedState.get<String>(KEY_FIELD_PREFIX + it.name).orEmpty()
        },
        nutrients = MicronutrientKind.editableKinds.associateWith {
            savedState.get<String>(KEY_FIELD_PREFIX + it.name).orEmpty()
        },
        baseUnit = savedState.get<String>(KEY_UNIT) ?: "g",
        barcode = savedState.get<String>(KEY_BARCODE),
        portions = savedState.get<List<String>>(KEY_PORTIONS).orEmpty().map(::decodeRow),
        touched = savedState.get<List<String>>(KEY_TOUCHED).orEmpty().toSet(),
    )

    private fun persist(value: FormDraft) {
        savedState[KEY_STARTED] = true
        value.fields.forEach { (field, text) -> savedState[KEY_FIELD_PREFIX + field.name] = text }
        value.nutrients.forEach { (kind, text) -> savedState[KEY_FIELD_PREFIX + kind.name] = text }
        savedState[KEY_UNIT] = value.baseUnit
        savedState[KEY_BARCODE] = value.barcode
        savedState[KEY_TOUCHED] = ArrayList(value.touched)
        savedState[KEY_PORTIONS] = ArrayList(value.portions.map(::encodeRow))
    }

    private fun buildState(
        value: FormDraft,
        online: Boolean,
        saving: Boolean,
        failed: Boolean,
        load: LoadState,
    ): CreateFoodUiState {
        val all = validate(value)
        fun shown(field: CreateFoodField, error: NewFoodError?) = error.takeIf { field.name in value.touched }
        val errors = NewFoodErrors(
            name = shown(CreateFoodField.Name, all.name),
            brand = shown(CreateFoodField.Brand, all.brand),
            kcal = shown(CreateFoodField.Kcal, all.kcal),
            protein = shown(CreateFoodField.Protein, all.protein),
            carbs = shown(CreateFoodField.Carbs, all.carbs),
            fat = shown(CreateFoodField.Fat, all.fat),
            alcohol = shown(CreateFoodField.Alcohol, all.alcohol),
            portions = all.portions.filterKeys { value.portions[it].touched },
            nutrients = all.nutrients.filterKeys { it.name in value.touched },
        )
        return CreateFoodUiState(
            name = value.text(CreateFoodField.Name),
            brand = value.text(CreateFoodField.Brand),
            baseUnit = value.baseUnit,
            kcal = value.text(CreateFoodField.Kcal),
            protein = value.text(CreateFoodField.Protein),
            carbs = value.text(CreateFoodField.Carbs),
            fat = value.text(CreateFoodField.Fat),
            alcohol = value.text(CreateFoodField.Alcohol),
            nutrients = value.nutrients,
            barcode = value.barcode,
            portions = value.portions.map { PortionDraft(it.label, it.amount) },
            errors = errors,
            canSave = !saving && load == LoadState.Idle && isSavable(value, online),
            energyWarning = warningOf(value),
            canAddPortion = value.portions.size < MAX_PORTIONS,
            isSaving = saving,
            isOffline = !online,
            saveFailed = failed,
            isEdit = editFoodId != null,
            isDirty = isDirty(value),
            isLoading = load == LoadState.Loading,
            loadFailed = load == LoadState.Failed,
        )
    }

    /** Valid, online and not failing the energy check: the hard block of ADR-104. */
    private fun isSavable(value: FormDraft, online: Boolean) =
        online && validate(value).isValid && value.energyIssue() == null

    /** The validation result, where a blank kcal is [NewFoodError.Required] instead of "not a number". */
    private fun validate(value: FormDraft): NewFoodErrors {
        val errors = NewFoodValidation.validate(value.toNewFood())
        return if (value.text(CreateFoodField.Kcal).isBlank()) errors.copy(kcal = NewFoodError.Required) else errors
    }
}
