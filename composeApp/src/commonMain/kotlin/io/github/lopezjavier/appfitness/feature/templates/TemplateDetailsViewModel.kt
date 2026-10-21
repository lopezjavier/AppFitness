package io.github.lopezjavier.appfitness.feature.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.food.formatNumber
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_RENAME_OPEN = "template_details_rename_open"
private const val KEY_RENAME_NAME = "template_details_rename_name"
private const val KEY_RENAME_TAKEN = "template_details_rename_taken"
private const val KEY_DELETE = "template_details_delete"

private sealed interface DetailsLoaded {
    data object Loading : DetailsLoaded

    data object Failed : DetailsLoaded

    data class Ready(val template: MealTemplate) : DetailsLoaded
}

/** The rename sheet as typed: null when closed. */
private data class RenameInput(val name: String, val taken: Boolean)

/**
 * Details of a saved comida (US-21): its foods with quantities and total kcal, plus rename and delete (both need the
 * server, so they do nothing offline). The rename sheet and the delete dialog live in [savedState] (ADR-40).
 */
@Suppress("LongParameterList") // One dependency per port.
class TemplateDetailsViewModel(
    private val templateId: String,
    private val mealTemplates: MealTemplateRepository,
    private val scaleNutrition: ScaleNutrition,
    private val validateTemplateName: ValidateTemplateName,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val loaded = MutableStateFlow<DetailsLoaded>(DetailsLoaded.Loading)
    private val rename = MutableStateFlow(restoredRename())
    private val confirmDelete = MutableStateFlow(savedState.get<Boolean>(KEY_DELETE) == true)
    private val online = MutableStateFlow(true)
    private val busy = MutableStateFlow(false)

    private val effectChannel = Channel<TemplateDetailsEffect>(Channel.BUFFERED)
    val effects: Flow<TemplateDetailsEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<TemplateDetailsUiState> =
        combine(loaded, rename, confirmDelete, online, busy) { l, r, d, o, b -> buildState(l, r, d, o, b) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TemplateDetailsUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        load(showLoading = true)
    }

    fun onEvent(event: TemplateDetailsEvent) {
        when (event) {
            TemplateDetailsEvent.OpenRename -> openRename()
            is TemplateDetailsEvent.RenameNameChanged -> rename.value?.let { setRename(RenameInput(event.text, false)) }
            TemplateDetailsEvent.SubmitRename -> submitRename()
            TemplateDetailsEvent.DismissRename -> setRename(null)
            TemplateDetailsEvent.Delete -> if (online.value && loaded.value is DetailsLoaded.Ready) setDelete(true)
            TemplateDetailsEvent.ConfirmDelete -> performDelete()
            TemplateDetailsEvent.DismissDelete -> setDelete(false)
            TemplateDetailsEvent.Retry -> load(showLoading = true)
            TemplateDetailsEvent.Refresh -> load(showLoading = false)
        }
    }

    private fun restoredRename(): RenameInput? = if (savedState.get<Boolean>(KEY_RENAME_OPEN) == true) {
        RenameInput(
            name = savedState.get<String>(KEY_RENAME_NAME).orEmpty(),
            taken = savedState.get<Boolean>(KEY_RENAME_TAKEN) == true,
        )
    } else {
        null
    }

    private fun load(showLoading: Boolean) {
        if (showLoading) loaded.value = DetailsLoaded.Loading
        viewModelScope.launch {
            val template = mealTemplates.list().getOrNull()?.firstOrNull { it.id == templateId }
            loaded.value = if (template == null) DetailsLoaded.Failed else DetailsLoaded.Ready(template)
        }
    }

    private fun setRename(input: RenameInput?) {
        rename.value = input
        savedState[KEY_RENAME_OPEN] = input != null
        savedState[KEY_RENAME_NAME] = input?.name
        savedState[KEY_RENAME_TAKEN] = input?.taken == true
    }

    private fun setDelete(open: Boolean) {
        confirmDelete.value = open
        savedState[KEY_DELETE] = open
    }

    private fun openRename() {
        val template = (loaded.value as? DetailsLoaded.Ready)?.template ?: return
        if (online.value) setRename(RenameInput(template.name, false))
    }

    private fun submitRename() {
        val input = rename.value ?: return
        val draft = RenameDraft(input.name, input.taken, validateTemplateName(input.name))
        if (!draft.canSubmit || !online.value || busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                val result = mealTemplates.rename(templateId, input.name.trim())
                when {
                    result.isSuccess -> {
                        setRename(null)
                        load(showLoading = false)
                    }

                    result.appErrorOrNull() == AppError.NameAlreadyExists -> setRename(input.copy(taken = true))

                    else -> effectChannel.send(TemplateDetailsEffect.ActionFailed(result.appErrorOrNull()))
                }
            } finally {
                busy.value = false
            }
        }
    }

    private fun performDelete() {
        if (!confirmDelete.value || !online.value || busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                val result = mealTemplates.delete(templateId)
                setDelete(false)
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

    @Suppress("LongParameterList") // One argument per combined flow.
    private fun buildState(
        loaded: DetailsLoaded,
        rename: RenameInput?,
        confirmDelete: Boolean,
        online: Boolean,
        busy: Boolean,
    ): TemplateDetailsUiState = when (loaded) {
        DetailsLoaded.Loading -> TemplateDetailsUiState.Loading

        DetailsLoaded.Failed -> TemplateDetailsUiState.Error

        is DetailsLoaded.Ready -> TemplateDetailsUiState.Content(
            name = loaded.template.name,
            kcal = loaded.template.items.sumOf { scaleNutrition(it.food.nutrition, it.amount).kcal },
            rows = loaded.template.items.mapIndexed { index, item ->
                DetailsRow(
                    index = index,
                    name = item.food.name,
                    amountText = formatNumber(item.amount),
                    unit = item.food.baseUnit,
                    portionLabel = item.portionLabel,
                )
            },
            isOffline = !online,
            rename = rename?.let {
                RenameDraft(it.name, it.taken, validateTemplateName(it.name))
            },
            confirmDelete = confirmDelete,
            busy = busy,
        )
    }
}
