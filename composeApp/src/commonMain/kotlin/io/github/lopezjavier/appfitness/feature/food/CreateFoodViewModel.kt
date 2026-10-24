package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.NewFoodError
import io.github.lopezjavier.appfitness.domain.model.NewFoodErrors
import io.github.lopezjavier.appfitness.domain.model.NewFoodValidation
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_STARTED = "create_food_started"
private const val KEY_BARCODE = "create_food_barcode"
private const val KEY_UNIT = "create_food_unit"
private const val KEY_ORIGINAL_UNIT = "create_food_original_unit"
private const val KEY_UNIT_LOCKED = "create_food_unit_locked"
private const val KEY_UNIT_REFUSED = "create_food_unit_refused"
private const val KEY_TOUCHED = "create_food_touched"
private const val KEY_PORTIONS = "create_food_portions"
private const val KEY_SECTIONS = "create_food_sections"
private const val KEY_FIELD_PREFIX = "create_food_field_"
private const val MAX_PORTIONS = 10
private val UNITS = setOf("g", "ml")

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
 */
@Suppress("TooManyFunctions") // One function per step of the form.
class CreateFoodViewModel(
    barcode: String?,
    name: String?,
    private val foodRepository: FoodRepository,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val editFoodId: String? = null,
) : ViewModel() {
    private val draft = MutableStateFlow(restoreOrPrefill(barcode, name))
    private val online = MutableStateFlow(true)
    private val saving = MutableStateFlow(false)
    private val saveFailed = MutableStateFlow(false)

    /** The unit as stored (edit mode); the unit goes back to it when the server refuses a change. */
    private var originalUnit: String? = savedState.get<String>(KEY_ORIGINAL_UNIT)
    private val unitLocked = MutableStateFlow(savedState.get<Boolean>(KEY_UNIT_LOCKED) == true)
    private val unitRefused = MutableStateFlow(savedState.get<Boolean>(KEY_UNIT_REFUSED) == true)
    private val sections = MutableStateFlow(
        savedState.get<List<String>>(KEY_SECTIONS).orEmpty().mapNotNull { id ->
            CreateFoodSection.entries.firstOrNull { it.name == id }
        }.toSet(),
    )
    private val loadState = MutableStateFlow(
        if (editFoodId != null && savedState.get<Boolean>(KEY_STARTED) != true) LoadState.Loading else LoadState.Idle,
    )

    private val effectChannel = Channel<CreateFoodEffect>(Channel.BUFFERED)
    val effects: Flow<CreateFoodEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<CreateFoodUiState> =
        combine(draft, online, saving, saveFailed, loadState, ::buildState)
            .combine(sections) { state, expanded -> state.copy(expandedSections = expanded) }
            .combine(unitLocked) { state, locked -> state.copy(unitLocked = locked) }
            .combine(unitRefused) { state, refused -> state.copy(unitRefused = refused) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                buildState(draft.value, true, false, false, loadState.value).copy(
                    expandedSections = sections.value,
                    unitLocked = unitLocked.value,
                    unitRefused = unitRefused.value,
                ),
            )

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        if (loadState.value == LoadState.Loading) load()
    }

    fun onEvent(event: CreateFoodEvent) {
        when (event) {
            is CreateFoodEvent.FieldChanged -> update {
                it.copy(
                    fields = it.fields + (event.field to event.text),
                    touched = it.touched + event.field.name,
                )
            }

            is CreateFoodEvent.NutrientChanged -> update {
                it.copy(
                    nutrients = it.nutrients + (event.kind to event.text),
                    touched = it.touched + event.kind.name,
                )
            }

            // Only "g" and "ml" exist; the unit of a food that is in use never changes.
            is CreateFoodEvent.UnitSelected -> selectUnit(event.unit)

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

            CreateFoodEvent.Retry -> if (loadState.value == LoadState.Failed) load()
        }
    }

    private fun selectUnit(unit: String) {
        if (!unitLocked.value && unit in UNITS) update { it.copy(baseUnit = unit) }
    }

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
        persist(updated)
    }

    private fun updateRow(index: Int, change: (PortionRow) -> PortionRow) = update { current ->
        current.copy(portions = current.portions.mapIndexed { i, row -> if (i == index) change(row) else row })
    }

    private fun save() {
        val current = draft.value
        if (saving.value || loadState.value != LoadState.Idle || !isSavable(current, online.value)) return
        saving.value = true
        saveFailed.value = false
        viewModelScope.launch {
            val result: Result<Food> = if (editFoodId != null) {
                foodRepository.updateFood(current.toUpdatedFood(editFoodId))
            } else {
                foodRepository.createFood(current.toNewFood())
            }
            saving.value = false
            result.fold(
                onSuccess = { effectChannel.send(CreateFoodEffect.Saved(it.id)) },
                onFailure = {
                    if ((it as? AppException)?.error == AppError.FoodInUse) {
                        refuseUnitChange()
                    } else {
                        saveFailed.value = true
                        effectChannel.send(CreateFoodEffect.SaveFailed)
                    }
                },
            )
        }
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

    private fun restoreOrPrefill(barcode: String?, name: String?): FormDraft {
        if (savedState.get<Boolean>(KEY_STARTED) == true) {
            return FormDraft(
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
        }
        val blank = FormDraft.blank(name, barcode)
        // In edit mode the draft only starts once the stored food has been read.
        return if (editFoodId == null) blank.also(::persist) else blank
    }

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
            nutrients = value.nutrients,
            barcode = value.barcode,
            portions = value.portions.map { PortionDraft(it.label, it.amount) },
            errors = errors,
            canSave = !saving && load == LoadState.Idle && isSavable(value, online),
            canAddPortion = value.portions.size < MAX_PORTIONS,
            isSaving = saving,
            isOffline = !online,
            saveFailed = failed,
            isEdit = editFoodId != null,
            isLoading = load == LoadState.Loading,
            loadFailed = load == LoadState.Failed,
        )
    }

    private fun isSavable(value: FormDraft, online: Boolean) = online && validate(value).isValid

    /** The validation result, where a blank kcal is [NewFoodError.Required] instead of "not a number". */
    private fun validate(value: FormDraft): NewFoodErrors {
        val errors = NewFoodValidation.validate(value.toNewFood())
        return if (value.text(CreateFoodField.Kcal).isBlank()) errors.copy(kcal = NewFoodError.Required) else errors
    }
}
