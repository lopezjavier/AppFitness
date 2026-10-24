package io.github.lopezjavier.appfitness.feature.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.usecase.AddTemplateToDiary
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import kotlinx.coroutines.channels.Channel
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

/** The comida as read, or why it is not shown. */
private sealed interface PreviewLoaded {
    data object Loading : PreviewLoaded

    data object Failed : PreviewLoaded

    data class Ready(val template: MealTemplate) : PreviewLoaded
}

/**
 * Read-only details of a comida (US-53): its foods with amounts and nutrition, the totals, "Añadir" (logs it to
 * [slotId] on [date]; null [slotId] = the meal of the time of day) and "Editar" (the editor is another screen). The
 * route arguments are the whole state, so nothing needs restoring ([savedState] is kept for the Koin factory
 * contract); opening only reads, nothing is added until [TemplatePreviewEvent.Add]. Adding needs the server, so it
 * does nothing offline.
 */
@Suppress("LongParameterList") // One dependency per port plus the route arguments.
class TemplatePreviewViewModel(
    private val templateId: String,
    private val slotId: String?,
    private val date: LocalDate,
    private val mealTemplates: MealTemplateRepository,
    private val scaleNutrition: ScaleNutrition,
    private val addTemplateToDiary: AddTemplateToDiary,
    private val mealSlots: MealSlotRepository,
    private val defaultMealSlot: DefaultMealSlot,
    private val clock: Clock,
    connectivity: ConnectivityObserver,
    @Suppress("unused") private val savedState: SavedStateHandle = SavedStateHandle(),
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val loaded = MutableStateFlow<PreviewLoaded>(PreviewLoaded.Loading)
    private val online = MutableStateFlow(true)
    private val adding = MutableStateFlow(false)
    private val addFailed = MutableStateFlow(false)

    private val effectChannel = Channel<TemplatePreviewEffect>(Channel.BUFFERED)
    val effects: Flow<TemplatePreviewEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<TemplatePreviewUiState> =
        combine(loaded, online, adding, addFailed, ::buildState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, TemplatePreviewUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        load(showLoading = true)
    }

    fun onEvent(event: TemplatePreviewEvent) {
        when (event) {
            TemplatePreviewEvent.Add -> add()
            TemplatePreviewEvent.Edit -> effectChannel.trySend(TemplatePreviewEffect.OpenEditor)
            TemplatePreviewEvent.Refresh -> load(showLoading = false)
            TemplatePreviewEvent.Retry -> load(showLoading = true)
        }
    }

    private fun load(showLoading: Boolean) {
        if (showLoading) loaded.value = PreviewLoaded.Loading
        viewModelScope.launch {
            val result = mealTemplates.list()
            val template = result.getOrNull()?.firstOrNull { it.id == templateId }
            when {
                template != null -> loaded.value = PreviewLoaded.Ready(template)

                // A failed reload keeps what is on screen; a comida missing from a good list is gone.
                result.isFailure && loaded.value is PreviewLoaded.Ready -> Unit

                else -> loaded.value = PreviewLoaded.Failed
            }
        }
    }

    private fun add() {
        val ready = loaded.value as? PreviewLoaded.Ready ?: return
        if (adding.value || !online.value || ready.template.items.isEmpty()) return
        adding.value = true
        addFailed.value = false
        viewModelScope.launch {
            try {
                val mealSlotId = slotId ?: defaultSlotId()
                if (mealSlotId == null) {
                    fail(null)
                    return@launch
                }
                val result = addTemplateToDiary(ready.template, mealSlotId, date)
                if (result.isSuccess) {
                    effectChannel.trySend(
                        TemplatePreviewEffect.Added,
                    )
                } else {
                    fail(result.appErrorOrNull())
                }
            } finally {
                adding.value = false
            }
        }
    }

    private fun fail(error: AppError?) {
        addFailed.value = true
        effectChannel.trySend(TemplatePreviewEffect.Failed(error))
    }

    /** The meal chosen by the time of day when the route has none; null when no meal is enabled. */
    private suspend fun defaultSlotId(): String? {
        val slots = mealSlots.listEnabled().getOrNull() ?: return null
        return defaultMealSlot(clock.now().toLocalDateTime(timeZone).time, slots)?.id
    }

    private fun buildState(
        loaded: PreviewLoaded,
        online: Boolean,
        adding: Boolean,
        addFailed: Boolean,
    ): TemplatePreviewUiState = when (loaded) {
        PreviewLoaded.Loading -> TemplatePreviewUiState.Loading

        PreviewLoaded.Failed -> TemplatePreviewUiState.Error

        is PreviewLoaded.Ready -> {
            val rows = loaded.template.items.map { item ->
                val nutrition = scaleNutrition(item.food.nutrition, item.amount)
                PreviewRow(
                    foodId = item.food.id,
                    name = item.food.name,
                    unit = item.food.baseUnit,
                    amount = item.amount,
                    portionLabel = item.portionLabel,
                    kcal = nutrition.kcal,
                    proteinG = nutrition.proteinG,
                    carbsG = nutrition.carbsG,
                    fatG = nutrition.fatG,
                )
            }
            TemplatePreviewUiState.Content(
                name = loaded.template.name,
                rows = rows,
                totalKcal = rows.sumOf { it.kcal },
                totalProteinG = rows.sumOf { it.proteinG },
                totalCarbsG = rows.sumOf { it.carbsG },
                totalFatG = rows.sumOf { it.fatG },
                isOffline = !online,
                adding = adding,
                addFailed = addFailed,
            )
        }
    }
}
