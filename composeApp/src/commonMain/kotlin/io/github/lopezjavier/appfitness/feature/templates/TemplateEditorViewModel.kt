package io.github.lopezjavier.appfitness.feature.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck
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

private const val KEY_AMOUNT = "template_editor_amount_"
private const val KEY_REMOVED = "template_editor_removed_"
private const val KEY_SHEET = "template_editor_sheet"
private const val KEY_NEW_NAME = "template_editor_new_name"
private const val KEY_NEW_TAKEN = "template_editor_new_taken"

private sealed interface EditorLoaded {
    data object Loading : EditorLoaded

    data object Failed : EditorLoaded

    data class Ready(val template: MealTemplate) : EditorLoaded
}

/** The user's input: amounts as typed (by food id), the removed foods, the open sheet and the new-name text. */
private data class Draft(
    val amounts: Map<String, String>,
    val removed: Set<String>,
    val sheet: EditorSheet,
    val newName: String,
    val newNameTaken: Boolean,
)

private fun amountKey(foodId: String) = "$KEY_AMOUNT$foodId"

private fun removedKey(foodId: String) = "$KEY_REMOVED$foodId"

private fun remainingItems(template: MealTemplate, draft: Draft) =
    template.items.filter { it.food.id !in draft.removed }

/**
 * Editor of a saved comida (US-21): change the amounts, remove foods, then "Guardar" overwrites the comida or saves a
 * new one with its own name. Amounts are typed text; the draft (amounts, removed foods, the open sheet and the new
 * name) lives in [savedState] (ADR-40). Changing an amount clears that food's portion label. Saving needs the server,
 * so [TemplateEditorContent.canSave] is false offline.
 */
@Suppress("LongParameterList", "TooManyFunctions") // One dependency per port; one function per editor action.
class TemplateEditorViewModel(
    private val templateId: String,
    private val mealTemplates: MealTemplateRepository,
    private val validateQuantity: ValidateQuantity,
    private val validateTemplateName: ValidateTemplateName,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val loaded = MutableStateFlow<EditorLoaded>(EditorLoaded.Loading)
    private val draft = MutableStateFlow<Draft?>(null)
    private val online = MutableStateFlow(true)
    private val saving = MutableStateFlow(false)

    private val effectChannel = Channel<TemplateEditorEffect>(Channel.BUFFERED)
    val effects: Flow<TemplateEditorEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<TemplateEditorUiState> =
        combine(loaded, draft, online, saving, ::buildState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, TemplateEditorUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        load()
    }

    fun onEvent(event: TemplateEditorEvent) {
        when (event) {
            is TemplateEditorEvent.AmountChanged ->
                updateDraft { it.copy(amounts = it.amounts + (event.foodId to event.text)) }

            is TemplateEditorEvent.RemoveFood -> removeFood(event.foodId)

            TemplateEditorEvent.SaveClicked -> openChoice()

            TemplateEditorEvent.Overwrite -> overwrite()

            TemplateEditorEvent.SaveAsNew -> updateDraft { it.copy(sheet = EditorSheet.NewName) }

            is TemplateEditorEvent.NewNameChanged -> updateDraft { it.copy(newName = event.text, newNameTaken = false) }

            TemplateEditorEvent.SubmitNewName -> submitNewName()

            TemplateEditorEvent.DismissSheet -> updateDraft { it.copy(sheet = EditorSheet.None) }

            TemplateEditorEvent.Retry -> load()
        }
    }

    private fun load() {
        loaded.value = EditorLoaded.Loading
        viewModelScope.launch {
            val template = mealTemplates.list().getOrNull()?.firstOrNull { it.id == templateId }
            if (template == null) {
                loaded.value = EditorLoaded.Failed
                return@launch
            }
            // The draft is read once: a retry keeps what the user has changed.
            if (draft.value == null) draft.value = initialDraft(template)
            loaded.value = EditorLoaded.Ready(template)
        }
    }

    private fun initialDraft(template: MealTemplate) = Draft(
        amounts = template.items.associate { item ->
            item.food.id to (savedState.get<String>(amountKey(item.food.id)) ?: formatNumber(item.amount))
        },
        removed = template.items
            .map { it.food.id }
            .filter { savedState.get<Boolean>(removedKey(it)) == true }
            .toSet(),
        sheet = savedState.get<String>(KEY_SHEET)
            ?.let { name -> EditorSheet.entries.firstOrNull { it.name == name } }
            ?: EditorSheet.None,
        newName = savedState.get<String>(KEY_NEW_NAME).orEmpty(),
        newNameTaken = savedState.get<Boolean>(KEY_NEW_TAKEN) == true,
    )

    private fun updateDraft(change: (Draft) -> Draft) {
        val updated = change(draft.value ?: return)
        draft.value = updated
        persist(updated)
    }

    private fun persist(current: Draft) {
        current.amounts.forEach { (foodId, text) -> savedState[amountKey(foodId)] = text }
        current.removed.forEach { savedState[removedKey(it)] = true }
        savedState[KEY_SHEET] = current.sheet.name
        savedState[KEY_NEW_NAME] = current.newName
        savedState[KEY_NEW_TAKEN] = current.newNameTaken
    }

    /** At least one food must remain: the last one cannot be removed. */
    private fun removeFood(foodId: String) {
        val template = readyTemplate() ?: return
        val current = draft.value ?: return
        val remaining = remainingItems(template, current)
        if (remaining.size > 1 && remaining.any { it.food.id == foodId }) {
            updateDraft { it.copy(removed = it.removed + foodId) }
        }
    }

    private fun openChoice() {
        val template = readyTemplate() ?: return
        val current = draft.value ?: return
        if (buildContent(template, current, online.value, saving.value).canSave) {
            updateDraft { it.copy(sheet = EditorSheet.Choose) }
        }
    }

    private fun overwrite() {
        val template = readyTemplate() ?: return
        val items = savableItems(template) ?: return
        save(templateId, template.name, template.mealSlotId, items)
    }

    private fun submitNewName() {
        val template = readyTemplate() ?: return
        val current = draft.value ?: return
        val nameIsFree = validateTemplateName(current.newName) == TemplateNameCheck.Valid && !current.newNameTaken
        val items = savableItems(template)
        if (nameIsFree && items != null) save(null, current.newName.trim(), template.mealSlotId, items)
    }

    private fun save(id: String?, name: String, slotId: String?, items: List<NewTemplateItem>) {
        saving.value = true
        viewModelScope.launch {
            val result = mealTemplates.save(id, name, slotId, items)
            saving.value = false
            if (result.isSuccess) {
                effectChannel.send(TemplateEditorEffect.Saved)
                return@launch
            }
            val error = result.appErrorOrNull()
            if (error == AppError.NameAlreadyExists) {
                updateDraft { it.copy(newNameTaken = true) }
            } else {
                effectChannel.send(TemplateEditorEffect.SaveFailed(error))
            }
        }
    }

    private fun readyTemplate(): MealTemplate? = (loaded.value as? EditorLoaded.Ready)?.template

    /** The items to store for the current draft, or null when the editor cannot save now. */
    private fun savableItems(template: MealTemplate): List<NewTemplateItem>? {
        val current = draft.value ?: return null
        val savable = buildContent(template, current, online.value, saving.value).canSave
        return if (!savable) {
            null
        } else {
            remainingItems(template, current).map { item ->
                val text = current.amounts.getValue(item.food.id)
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

    private fun buildState(
        loaded: EditorLoaded,
        draft: Draft?,
        online: Boolean,
        saving: Boolean,
    ): TemplateEditorUiState = when (loaded) {
        EditorLoaded.Loading -> TemplateEditorUiState.Loading

        EditorLoaded.Failed -> TemplateEditorUiState.Error

        is EditorLoaded.Ready -> draft?.let {
            TemplateEditorUiState.Content(buildContent(loaded.template, it, online, saving))
        } ?: TemplateEditorUiState.Loading
    }

    private fun buildContent(
        template: MealTemplate,
        current: Draft,
        online: Boolean,
        saving: Boolean,
    ): TemplateEditorContent {
        val remaining = remainingItems(template, current)
        return TemplateEditorContent(
            name = template.name,
            rows = remaining.map { item ->
                EditorRow(
                    foodId = item.food.id,
                    name = item.food.name,
                    unit = item.food.baseUnit,
                    amountText = current.amounts[item.food.id].orEmpty(),
                    quantityError = quantityError(current.amounts[item.food.id].orEmpty()),
                    removable = remaining.size > 1,
                )
            },
            sheet = current.sheet,
            newName = current.newName,
            newNameCheck = validateTemplateName(current.newName),
            newNameTaken = current.newNameTaken,
            isOffline = !online,
            saving = saving,
        )
    }

    /** Null when the amount is valid; an unreadable amount is reported as too low. */
    private fun quantityError(text: String): QuantityCheck? {
        val check = parseAmount(text)?.let { validateQuantity(it) } ?: QuantityCheck.TooLow
        return check.takeUnless { it == QuantityCheck.Valid }
    }
}
