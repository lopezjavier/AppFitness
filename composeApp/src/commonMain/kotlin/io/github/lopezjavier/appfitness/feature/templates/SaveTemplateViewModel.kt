package io.github.lopezjavier.appfitness.feature.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildTemplateItems
import io.github.lopezjavier.appfitness.domain.usecase.CookingImpact
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import kotlinx.coroutines.channels.Channel
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

private const val KEY_NAME = "save_template_name"
private const val KEY_TAKEN = "save_template_taken"
private const val KEY_METHOD_PREFIX = "save_template_method_"

/** What the sheet is built from once the day is read: the entries of the chosen meal only. */
private sealed interface Loaded {
    data object Loading : Loaded

    data object Failed : Loaded

    data class Ready(val sectionEntries: List<FoodLogEntry>) : Loaded
}

/** The save flag and the chosen cooking methods, combined as one input of the state. */
private data class SaveProgress(val saving: Boolean, val methods: Map<String, CookingMethod>)

/**
 * Save-as-comida sheet (US-21): names the diary section [slotId] of [date] and stores its foods and quantities as a
 * new comida. The section is read from a fresh copy of the day; the name and the duplicate-name error live in
 * [savedState] (ADR-40). Saving needs the server, so [SaveTemplateContent.canSave] is false offline.
 */
@Suppress("LongParameterList") // One dependency per port.
class SaveTemplateViewModel(
    private val slotId: String,
    private val date: LocalDate,
    private val foodDiary: FoodDiaryRepository,
    private val buildTemplateItems: BuildTemplateItems,
    private val validateTemplateName: ValidateTemplateName,
    private val mealTemplates: MealTemplateRepository,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val loaded = MutableStateFlow<Loaded>(Loaded.Loading)
    private val name = MutableStateFlow(savedState.get<String>(KEY_NAME).orEmpty())
    private val taken = MutableStateFlow(savedState.get<Boolean>(KEY_TAKEN) ?: false)
    private val online = MutableStateFlow(true)
    private val saving = MutableStateFlow(false)
    private val methods = MutableStateFlow<Map<String, CookingMethod>>(emptyMap())

    private val effectChannel = Channel<SaveTemplateEffect>(Channel.BUFFERED)
    val effects: Flow<SaveTemplateEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<SaveTemplateUiState> =
        combine(loaded, name, taken, online, combine(saving, methods, ::SaveProgress), ::buildState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, SaveTemplateUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        load()
    }

    fun onEvent(event: SaveTemplateEvent) {
        when (event) {
            is SaveTemplateEvent.NameChanged -> changeName(event.text)
            is SaveTemplateEvent.MethodChanged -> changeMethod(event.entryId, event.method)
            SaveTemplateEvent.Submit -> save()
            SaveTemplateEvent.Retry -> load()
        }
    }

    private fun load() {
        loaded.value = Loaded.Loading
        viewModelScope.launch {
            loaded.value = if (foodDiary.refresh(date).isSuccess) {
                val sectionEntries = foodDiary.entries(date).first().filter { it.mealSlotId == slotId }
                methods.value = restoreMethods(sectionEntries)
                Loaded.Ready(sectionEntries)
            } else {
                Loaded.Failed
            }
        }
    }

    private fun restoreMethods(entries: List<FoodLogEntry>): Map<String, CookingMethod> = buildMap {
        entries.forEach { entry ->
            CookingMethod.fromWireName(savedState.get<String>(KEY_METHOD_PREFIX + entry.id))
                ?.let { put(entry.id, it) }
        }
    }

    private fun changeMethod(entryId: String, method: CookingMethod) {
        val entry = (loaded.value as? Loaded.Ready)?.sectionEntries?.firstOrNull { it.id == entryId } ?: return
        if (!CookingImpact.appliesTo(entry.baseUnit)) return
        methods.value = methods.value + (entryId to method)
        savedState[KEY_METHOD_PREFIX + entryId] = method.wireName
    }

    private fun changeName(text: String) {
        name.value = text
        savedState[KEY_NAME] = text
        setTaken(false)
    }

    private fun setTaken(value: Boolean) {
        taken.value = value
        savedState[KEY_TAKEN] = value
    }

    private fun save() {
        val ready = loaded.value as? Loaded.Ready ?: return
        val content = buildContent(ready, name.value, taken.value, online.value, saving.value, methods.value)
        if (!content.canSave) return
        val items = buildTemplateItems(ready.sectionEntries, methods.value)
        saving.value = true
        viewModelScope.launch {
            val result = mealTemplates.save(null, name.value.trim(), slotId, items)
            saving.value = false
            if (result.isSuccess) {
                effectChannel.send(SaveTemplateEffect.Saved)
            } else {
                val error = result.appErrorOrNull()
                if (error == AppError.NameAlreadyExists) {
                    setTaken(true)
                } else {
                    effectChannel.send(SaveTemplateEffect.SaveFailed(error))
                }
            }
        }
    }

    private fun buildState(
        loaded: Loaded,
        name: String,
        taken: Boolean,
        online: Boolean,
        progress: SaveProgress,
    ): SaveTemplateUiState = when (loaded) {
        Loaded.Loading -> SaveTemplateUiState.Loading

        Loaded.Failed -> SaveTemplateUiState.Error

        is Loaded.Ready -> SaveTemplateUiState.Content(
            buildContent(loaded, name, taken, online, progress.saving, progress.methods),
        )
    }

    private fun buildContent(
        ready: Loaded.Ready,
        name: String,
        taken: Boolean,
        online: Boolean,
        saving: Boolean,
        methods: Map<String, CookingMethod>,
    ): SaveTemplateContent = SaveTemplateContent(
        name = name,
        nameCheck = validateTemplateName(name),
        nameTaken = taken,
        itemCount = ready.sectionEntries.size,
        isOffline = !online,
        saving = saving,
        items = ready.sectionEntries.map { entry ->
            val canCook = CookingImpact.appliesTo(entry.baseUnit)
            SaveTemplateItemRow(
                entryId = entry.id,
                foodName = entry.foodName,
                amount = entry.amount,
                portionLabel = entry.portionLabel,
                baseUnit = entry.baseUnit,
                method = CookingImpact.effectiveMethod(methods[entry.id] ?: entry.cookingMethod, entry.baseUnit),
                canCook = canCook,
            )
        },
    )
}
