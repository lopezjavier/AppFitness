package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.NewFood
import io.github.lopezjavier.appfitness.domain.model.NewFoodError
import io.github.lopezjavier.appfitness.domain.model.NewFoodErrors
import io.github.lopezjavier.appfitness.domain.model.NewFoodValidation
import io.github.lopezjavier.appfitness.domain.model.NewMicronutrients
import io.github.lopezjavier.appfitness.domain.model.NewPortion
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
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
private const val KEY_TOUCHED = "create_food_touched"
private const val KEY_PORTIONS = "create_food_portions"
private const val KEY_FIELD_PREFIX = "create_food_field_"
private const val ROW_SEPARATOR = '\u0001'
private const val MAX_PORTIONS = 10

/** A portion row plus whether the user has edited it (errors only show for edited rows). */
private data class PortionRow(val label: String, val amount: String, val touched: Boolean)

/** Everything the user typed; [touched] holds the [CreateFoodField]s already edited. */
private data class FormDraft(
    val fields: Map<CreateFoodField, String>,
    val baseUnit: String,
    val barcode: String?,
    val portions: List<PortionRow>,
    val touched: Set<CreateFoodField>,
)

/**
 * Create-food form (US-18, US-22): a personal food with optional named portions. The draft lives in [savedState]
 * (ADR-40) and is prefilled from [barcode] / [name] only the first time. Blank protein, carbs and fat count as 0;
 * kcal is required. Saving needs the server, so [CreateFoodUiState.canSave] is false offline.
 */
class CreateFoodViewModel(
    barcode: String?,
    name: String?,
    private val foodRepository: FoodRepository,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val draft = MutableStateFlow(restoreOrPrefill(barcode, name))
    private val online = MutableStateFlow(true)
    private val saving = MutableStateFlow(false)
    private val saveFailed = MutableStateFlow(false)

    private val effectChannel = Channel<CreateFoodEffect>(Channel.BUFFERED)
    val effects: Flow<CreateFoodEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<CreateFoodUiState> =
        combine(draft, online, saving, saveFailed, ::buildState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, buildState(draft.value, true, false, false))

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
    }

    fun onEvent(event: CreateFoodEvent) {
        when (event) {
            is CreateFoodEvent.FieldChanged -> update {
                it.copy(fields = it.fields + (event.field to event.text), touched = it.touched + event.field)
            }

            is CreateFoodEvent.UnitSelected -> update { it.copy(baseUnit = event.unit) }

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
                it.copy(
                    portions = it.portions.filterIndexed { i, _ ->
                        i !=
                            event.index
                    },
                )
            }

            is CreateFoodEvent.PortionLabelChanged -> updateRow(event.index) {
                it.copy(label = event.text, touched = true)
            }

            is CreateFoodEvent.PortionAmountChanged -> updateRow(event.index) {
                it.copy(amount = event.text, touched = true)
            }

            CreateFoodEvent.Save -> save()
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
        if (saving.value || !isSavable(current, online.value)) return
        saving.value = true
        saveFailed.value = false
        viewModelScope.launch {
            val result = foodRepository.createFood(toNewFood(current))
            saving.value = false
            result.fold(
                onSuccess = { effectChannel.send(CreateFoodEffect.Saved(it.id)) },
                onFailure = {
                    saveFailed.value = true
                    effectChannel.send(CreateFoodEffect.SaveFailed)
                },
            )
        }
    }

    private fun restoreOrPrefill(barcode: String?, name: String?): FormDraft {
        if (savedState.get<Boolean>(KEY_STARTED) == true) {
            return FormDraft(
                fields = CreateFoodField.entries.associateWith {
                    savedState.get<String>(KEY_FIELD_PREFIX + it.name).orEmpty()
                },
                baseUnit = savedState.get<String>(KEY_UNIT) ?: "g",
                barcode = savedState.get<String>(KEY_BARCODE),
                portions = savedState.get<List<String>>(KEY_PORTIONS).orEmpty().map(::decodeRow),
                touched = savedState.get<List<String>>(KEY_TOUCHED).orEmpty().mapNotNull { n ->
                    CreateFoodField.entries.firstOrNull { it.name == n }
                }.toSet(),
            )
        }
        return FormDraft(
            fields = CreateFoodField.entries.associateWith { if (it == CreateFoodField.Name) name.orEmpty() else "" },
            baseUnit = "g",
            barcode = barcode?.takeIf { it.isNotBlank() },
            portions = emptyList(),
            touched = emptySet(),
        ).also(::persist)
    }

    private fun persist(value: FormDraft) {
        savedState[KEY_STARTED] = true
        value.fields.forEach { (field, text) -> savedState[KEY_FIELD_PREFIX + field.name] = text }
        savedState[KEY_UNIT] = value.baseUnit
        savedState[KEY_BARCODE] = value.barcode
        savedState[KEY_TOUCHED] = ArrayList(value.touched.map { it.name })
        savedState[KEY_PORTIONS] = ArrayList(value.portions.map(::encodeRow))
    }

    private fun buildState(value: FormDraft, online: Boolean, saving: Boolean, failed: Boolean): CreateFoodUiState {
        val all = validate(value)
        val shown = NewFoodErrors(
            name = all.name.takeIf { CreateFoodField.Name in value.touched },
            brand = all.brand.takeIf { CreateFoodField.Brand in value.touched },
            kcal = all.kcal.takeIf { CreateFoodField.Kcal in value.touched },
            protein = all.protein.takeIf { CreateFoodField.Protein in value.touched },
            carbs = all.carbs.takeIf { CreateFoodField.Carbs in value.touched },
            fat = all.fat.takeIf { CreateFoodField.Fat in value.touched },
            portions = all.portions.filterKeys { value.portions[it].touched },
            saturatedFat = all.saturatedFat.takeIf { CreateFoodField.SaturatedFat in value.touched },
            sugars = all.sugars.takeIf { CreateFoodField.Sugars in value.touched },
            fiber = all.fiber.takeIf { CreateFoodField.Fiber in value.touched },
            salt = all.salt.takeIf { CreateFoodField.Salt in value.touched },
        )
        return CreateFoodUiState(
            name = value.fields.getValue(CreateFoodField.Name),
            brand = value.fields.getValue(CreateFoodField.Brand),
            baseUnit = value.baseUnit,
            kcal = value.fields.getValue(CreateFoodField.Kcal),
            protein = value.fields.getValue(CreateFoodField.Protein),
            carbs = value.fields.getValue(CreateFoodField.Carbs),
            fat = value.fields.getValue(CreateFoodField.Fat),
            saturatedFat = value.fields.getValue(CreateFoodField.SaturatedFat),
            sugars = value.fields.getValue(CreateFoodField.Sugars),
            fiber = value.fields.getValue(CreateFoodField.Fiber),
            salt = value.fields.getValue(CreateFoodField.Salt),
            barcode = value.barcode,
            portions = value.portions.map { PortionDraft(it.label, it.amount) },
            errors = shown,
            canSave = !saving && isSavable(value, online),
            canAddPortion = value.portions.size < MAX_PORTIONS,
            isSaving = saving,
            isOffline = !online,
            saveFailed = failed,
        )
    }

    private fun isSavable(value: FormDraft, online: Boolean) = online && validate(value).isValid

    /** The validation result, where a blank kcal is [NewFoodError.Required] instead of "not a number". */
    private fun validate(value: FormDraft): NewFoodErrors {
        val errors = NewFoodValidation.validate(toNewFood(value))
        return if (value.fields.getValue(
                CreateFoodField.Kcal,
            ).isBlank()
        ) {
            errors.copy(kcal = NewFoodError.Required)
        } else {
            errors
        }
    }

    private fun toNewFood(value: FormDraft): NewFood {
        fun number(field: CreateFoodField, blankIsZero: Boolean): Double {
            val text = value.fields.getValue(field).trim().replace(',', '.')
            return if (text.isEmpty() && blankIsZero) 0.0 else text.toDoubleOrNull() ?: Double.NaN
        }

        /** Blank is "not typed" (null); anything else that is not a number becomes NaN so validation flags it. */
        fun optional(field: CreateFoodField): Double? =
            if (value.fields.getValue(field).isBlank()) null else number(field, blankIsZero = false)
        return NewFood(
            name = value.fields.getValue(CreateFoodField.Name).trim(),
            brand = value.fields.getValue(CreateFoodField.Brand).trim().ifEmpty { null },
            baseUnit = value.baseUnit,
            nutrition = NutritionPer100(
                kcal = number(CreateFoodField.Kcal, blankIsZero = false),
                proteinG = number(CreateFoodField.Protein, blankIsZero = true),
                carbsG = number(CreateFoodField.Carbs, blankIsZero = true),
                fatG = number(CreateFoodField.Fat, blankIsZero = true),
            ),
            barcode = value.barcode,
            portions = value.portions.map {
                NewPortion(it.label.trim(), it.amount.trim().replace(',', '.').toDoubleOrNull() ?: Double.NaN)
            },
            micronutrients = NewMicronutrients(
                saturatedFat = optional(CreateFoodField.SaturatedFat),
                sugars = optional(CreateFoodField.Sugars),
                fiber = optional(CreateFoodField.Fiber),
                salt = optional(CreateFoodField.Salt),
            ),
        )
    }
}

private fun encodeRow(row: PortionRow) = "${row.label}$ROW_SEPARATOR${row.amount}$ROW_SEPARATOR${row.touched}"

private fun decodeRow(encoded: String): PortionRow {
    val parts = encoded.split(ROW_SEPARATOR)
    return PortionRow(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }, parts.getOrNull(2) == "true")
}
