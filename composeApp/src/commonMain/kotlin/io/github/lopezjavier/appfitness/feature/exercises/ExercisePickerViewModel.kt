package io.github.lopezjavier.appfitness.feature.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.SearchExercises
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Longest exercise name, matching the DB check. */
private const val NAME_MAX_LENGTH = 100

private const val KEY_QUERY = "picker_query"
private const val KEY_MUSCLE = "picker_muscle"
private const val KEY_EQUIPMENT = "picker_equipment"
private const val KEY_FORM_OPEN = "picker_form_open"
private const val KEY_FORM_NAME = "picker_form_name"
private const val KEY_FORM_MUSCLE = "picker_form_muscle"
private const val KEY_FORM_EQUIPMENT = "picker_form_equipment"
private const val KEY_FORM_METRIC = "picker_form_metric"
private const val KEY_FORM_PER_HAND = "picker_form_per_hand"

/**
 * Exercise picker (US-09): the searchable, filterable list and the "create exercise" sheet. In [selectMode] a row
 * (or a freshly created exercise) is returned to the caller; otherwise it opens the detail. The search, the filters
 * and an open create form are kept in [savedState], so they survive process death (never submitted on their own).
 */
@Suppress("TooManyFunctions") // One function per picker event plus the refresh.
class ExercisePickerViewModel(
    private val loadCatalog: LoadExerciseCatalog,
    private val searchExercises: SearchExercises,
    private val repository: ExerciseRepository,
    connectivity: ConnectivityObserver,
    private val selectMode: Boolean,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<ExercisePickerUiState>(ExercisePickerUiState.Loading)
    val uiState: StateFlow<ExercisePickerUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<ExercisePickerEffect>(Channel.BUFFERED)
    val effects: Flow<ExercisePickerEffect> = effectChannel.receiveAsFlow()

    private var all: List<CatalogExercise> = emptyList()
    private var online = true

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { isOnline ->
                online = isOnline
                updateContent { it.copy(isOnline = isOnline) }
            }
        }
        load()
    }

    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the events.
    fun onEvent(event: ExercisePickerEvent) {
        when (event) {
            is ExercisePickerEvent.SetQuery -> filter { it.copy(query = event.query) }

            is ExercisePickerEvent.SelectMuscleGroup -> filter { it.copy(muscleGroup = event.group) }

            is ExercisePickerEvent.SelectEquipment -> filter { it.copy(equipment = event.equipment) }

            is ExercisePickerEvent.OpenExercise -> open(event.id)

            ExercisePickerEvent.Retry -> if (_uiState.value == ExercisePickerUiState.Error) load()

            ExercisePickerEvent.OpenCreate ->
                updateContent {
                    if (it.canCreate) {
                        it.copy(
                            createForm = CreateExerciseForm(name = it.query.take(NAME_MAX_LENGTH)),
                        )
                    } else {
                        it
                    }
                }

            ExercisePickerEvent.DismissCreate -> updateContent { it.copy(createForm = null) }

            is ExercisePickerEvent.SetCreateName ->
                editForm { it.copy(name = event.name.take(NAME_MAX_LENGTH), hasError = false) }

            is ExercisePickerEvent.SelectCreateMuscleGroup -> editForm { it.copy(muscleGroup = event.group) }

            is ExercisePickerEvent.SelectCreateEquipment -> editForm { it.copy(equipment = event.equipment) }

            is ExercisePickerEvent.SelectCreateMetricType -> editForm { it.copy(metricType = event.metricType) }

            is ExercisePickerEvent.SetCreatePerHand -> editForm { it.copy(perHand = event.perHand) }

            ExercisePickerEvent.SubmitCreate -> submitCreate()
        }
    }

    private fun load() {
        _uiState.value = ExercisePickerUiState.Loading
        viewModelScope.launch {
            loadCatalog().fold(
                onSuccess = {
                    all = it
                    _uiState.value = restoredContent(it.size)
                    filter { content -> content }
                },
                onFailure = { _uiState.value = ExercisePickerUiState.Error },
            )
        }
    }

    private fun open(id: String) {
        effectChannel.trySend(
            if (selectMode) ExercisePickerEffect.Selected(id) else ExercisePickerEffect.OpenDetail(id),
        )
    }

    private fun submitCreate() {
        val form = (_uiState.value as? ExercisePickerUiState.Content)?.createForm?.takeIf { it.canSubmit && online }
        val group = form?.muscleGroup
        val equipment = form?.equipment
        if (form == null || group == null || equipment == null) return
        editForm { it.copy(isSubmitting = true, hasError = false) }
        viewModelScope.launch {
            repository.createCustom(form.name.trim(), group, equipment, form.metricType, form.perHand).fold(
                onSuccess = { created ->
                    updateContent { it.copy(createForm = null) }
                    reloadAfterCreate()
                    open(created.id)
                },
                onFailure = { editForm { it.copy(isSubmitting = false, hasError = true) } },
            )
        }
    }

    /** Reloads the catalogue after an edit or a delete made elsewhere (the session cache was invalidated). */
    fun refresh() {
        viewModelScope.launch { reloadAfterCreate() }
    }

    private suspend fun reloadAfterCreate() {
        loadCatalog().onSuccess {
            all = it
            filter { content -> content.copy(totalCount = it.size) }
        }
    }

    private fun filter(transform: (ExercisePickerUiState.Content) -> ExercisePickerUiState.Content) {
        updateContent { current ->
            val next = transform(current)
            next.copy(visible = searchExercises(all, next.query, next.muscleGroup, next.equipment))
        }
    }

    private fun editForm(transform: (CreateExerciseForm) -> CreateExerciseForm) {
        updateContent { content -> content.createForm?.let { content.copy(createForm = transform(it)) } ?: content }
    }

    private fun updateContent(transform: (ExercisePickerUiState.Content) -> ExercisePickerUiState.Content) {
        _uiState.update { if (it is ExercisePickerUiState.Content) transform(it).also(::persist) else it }
    }

    private fun restoredContent(totalCount: Int): ExercisePickerUiState.Content {
        val formOpen = savedState.get<Boolean>(KEY_FORM_OPEN) == true
        return ExercisePickerUiState.Content(
            visible = emptyList(),
            totalCount = totalCount,
            query = savedState.get<String>(KEY_QUERY).orEmpty(),
            muscleGroup = savedState.get<String>(KEY_MUSCLE)?.let { enumOrNull<MuscleGroup>(it) },
            equipment = savedState.get<String>(KEY_EQUIPMENT)?.let { enumOrNull<Equipment>(it) },
            isOnline = online,
            createForm =
            if (formOpen) {
                CreateExerciseForm(
                    name = savedState.get<String>(KEY_FORM_NAME).orEmpty(),
                    muscleGroup = savedState.get<String>(KEY_FORM_MUSCLE)?.let { enumOrNull<MuscleGroup>(it) },
                    equipment = savedState.get<String>(KEY_FORM_EQUIPMENT)?.let { enumOrNull<Equipment>(it) },
                    metricType = savedState.get<String>(KEY_FORM_METRIC)?.let { enumOrNull<ExerciseMetricType>(it) }
                        ?: ExerciseMetricType.WeightReps,
                    perHand = savedState.get<Boolean>(KEY_FORM_PER_HAND) == true,
                )
            } else {
                null
            },
        )
    }

    /** Keeps the draft (search, filters, open form) in the saved state; enums by name, nothing is submitted. */
    private fun persist(content: ExercisePickerUiState.Content) {
        savedState[KEY_QUERY] = content.query
        savedState[KEY_MUSCLE] = content.muscleGroup?.name
        savedState[KEY_EQUIPMENT] = content.equipment?.name
        val form = content.createForm
        savedState[KEY_FORM_OPEN] = form != null
        savedState[KEY_FORM_NAME] = form?.name
        savedState[KEY_FORM_MUSCLE] = form?.muscleGroup?.name
        savedState[KEY_FORM_EQUIPMENT] = form?.equipment?.name
        savedState[KEY_FORM_METRIC] = form?.metricType?.name
        savedState[KEY_FORM_PER_HAND] = form?.perHand
    }
}

private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }
