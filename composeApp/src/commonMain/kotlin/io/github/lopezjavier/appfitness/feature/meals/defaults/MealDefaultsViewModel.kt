package io.github.lopezjavier.appfitness.feature.meals.defaults

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.MealDefault
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.FoodPreferencesRepository
import io.github.lopezjavier.appfitness.domain.repository.MealDefaultsRepository
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.usecase.CookedItemNutrition
import io.github.lopezjavier.appfitness.domain.usecase.IsoWeekday
import io.github.lopezjavier.appfitness.domain.usecase.MealDefaultSelection
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val KEY_DRAFT = "meal_defaults_draft"
private const val KEY_BASELINE = "meal_defaults_baseline"
private const val KEY_PICKER = "meal_defaults_picker"
private const val KEY_SHEET = "meal_defaults_sheet"
private const val ROW_SEPARATOR = ","
private const val FIELD_SEPARATOR = ":"
private const val DAY_SEPARATOR = "-"
private const val SHEET_FIELDS = 3

/** The open assignment sheet: the days chosen so far for [templateId] in meal [slotId]. */
private data class SheetDraft(val slotId: String, val templateId: String, val days: Set<Int>)

/** What the screen shows besides the draft, loaded once. */
private class Catalog(val slots: List<MealSlot>, val templates: List<MealTemplate>, val prefs: FoodPreferences?)

/**
 * Default comidas editor (US-51, ADR-61): per enabled meal, which saved comida is the default of which weekdays. The
 * working draft is a flat list of [MealDefault] rows; [MealDefaultSelection] derives everything else. The sheet only
 * edits the draft; "Guardar" persists it all at once (replacing every default). The draft, its baseline, the picker and
 * the sheet live in [savedState] (ADR-40). A failed save keeps the draft.
 */
@Suppress("LongParameterList", "TooManyFunctions") // One dependency per port; one small function per concern.
class MealDefaultsViewModel(
    private val mealSlots: MealSlotRepository,
    private val mealTemplates: MealTemplateRepository,
    private val mealDefaults: MealDefaultsRepository,
    private val foodPreferences: FoodPreferencesRepository,
    private val cookedItemNutrition: CookedItemNutrition,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private var draft: List<MealDefault> = decodeRows(savedState[KEY_DRAFT])
    private var baseline: List<MealDefault> = decodeRows(savedState[KEY_BASELINE])
    private val restoredDraft = savedState.get<String>(KEY_DRAFT) != null
    private var catalog: Catalog? = null
    private var picker: String? = savedState[KEY_PICKER]
    private var sheet: SheetDraft? = savedState.get<String>(KEY_SHEET)?.let(::decodeSheet)
    private var loadFailed = false
    private var online = true
    private var saving = false
    private var discardAsked = false
    private var saveError: MealDefaultsSaveError? = null

    private val _uiState = MutableStateFlow<MealDefaultsUiState>(render())
    val uiState: StateFlow<MealDefaultsUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<MealDefaultsEffect>(Channel.BUFFERED)
    val effects: Flow<MealDefaultsEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect {
                online = it
                publish()
            }
        }
        load()
    }

    fun onEvent(event: MealDefaultsEvent) {
        when (event) {
            MealDefaultsEvent.Retry -> if (loadFailed) load()

            MealDefaultsEvent.Save -> save()

            MealDefaultsEvent.Back -> back()

            MealDefaultsEvent.ConfirmDiscard -> effectChannel.trySend(MealDefaultsEffect.Close)

            MealDefaultsEvent.DismissDiscard -> {
                discardAsked = false
                publish()
            }

            is MealDefaultsEvent.OpenPicker -> openPicker(event.slotId)

            MealDefaultsEvent.DismissPicker -> setPicker(null)

            is MealDefaultsEvent.OpenAssignment -> openSheet(event.slotId, event.templateId)

            is MealDefaultsEvent.ToggleDay -> toggleDay(event.day)

            MealDefaultsEvent.ConfirmSheet -> confirmSheet()

            MealDefaultsEvent.DismissSheet -> setSheet(null)

            is MealDefaultsEvent.Remove -> remove(event.slotId, event.templateId)
        }
    }

    private fun load() {
        loadFailed = false
        publish()
        viewModelScope.launch {
            val (loaded, saved) = coroutineScope {
                val slots = async { mealSlots.listEnabled() }
                val templates = async { mealTemplates.list() }
                val defaults = async { mealDefaults.list() }
                val prefs = async { foodPreferences.get() }
                val slotList = slots.await().getOrNull()
                val templateList = templates.await().getOrNull()
                val defaultList = defaults.await().getOrNull()
                val preferences = prefs.await().getOrNull()
                val ready = slotList != null && templateList != null && defaultList != null
                (if (ready) Catalog(slotList.orEmpty(), templateList.orEmpty(), preferences) else null) to defaultList
            }
            if (loaded == null || saved == null) {
                loadFailed = true
            } else {
                catalog = loaded
                if (!restoredDraft) {
                    draft = saved
                    persistDraft()
                }
                if (savedState.get<String>(KEY_BASELINE) == null) {
                    baseline = saved
                    savedState[KEY_BASELINE] = encodeRows(baseline)
                }
            }
            publish()
        }
    }

    private fun openPicker(slotId: String) {
        if (!isIdle() || catalog?.slots?.none { it.id == slotId } != false) return
        setPicker(slotId)
    }

    private fun setPicker(slotId: String?) {
        picker = slotId
        savedState[KEY_PICKER] = slotId
        publish()
    }

    private fun openSheet(slotId: String, templateId: String) {
        val loaded = catalog
        if (!isIdle() || loaded == null) return
        if (loaded.slots.none { it.id == slotId } || loaded.templates.none { it.id == templateId }) return
        val own = draft.filter { it.mealSlotId == slotId && it.templateId == templateId }.map { it.weekday }.toSet()
        val days = own.ifEmpty { MealDefaultSelection.availableDays(draft, slotId, templateId) }
        picker = null
        savedState[KEY_PICKER] = null
        setSheet(SheetDraft(slotId, templateId, days))
    }

    private fun setSheet(next: SheetDraft?) {
        sheet = next
        savedState[KEY_SHEET] = next?.let(::encodeSheet)
        publish()
    }

    private fun toggleDay(day: Int) {
        val current = sheet ?: return
        val blocked = MealDefaultSelection.conflictingDays(draft, current.slotId, current.templateId)
        if (day !in IsoWeekday.ALL || day in blocked) return
        setSheet(current.copy(days = if (day in current.days) current.days - day else current.days + day))
    }

    private fun confirmSheet() {
        val current = sheet ?: return
        if (current.days.isEmpty()) return
        val rows = current.days.sorted().map { MealDefault(current.slotId, current.templateId, it) }
        val firstIndex = draft.indexOfFirst { it.mealSlotId == current.slotId && it.templateId == current.templateId }
        val others = draft.filterNot { it.mealSlotId == current.slotId && it.templateId == current.templateId }
        draft = if (firstIndex < 0) others + rows else others.take(firstIndex) + rows + others.drop(firstIndex)
        saveError = null
        persistDraft()
        setSheet(null)
    }

    private fun remove(slotId: String, templateId: String) {
        if (!isIdle()) return
        draft = draft.filterNot { it.mealSlotId == slotId && it.templateId == templateId }
        saveError = null
        persistDraft()
        publish()
    }

    private fun back() {
        if (saving) return
        if (isDirty()) {
            discardAsked = true
            publish()
        } else {
            effectChannel.trySend(MealDefaultsEffect.Close)
        }
    }

    private fun save() {
        val state = _uiState.value as? MealDefaultsUiState.Content
        if (state == null || !state.canSave) return
        saving = true
        saveError = null
        publish()
        viewModelScope.launch {
            mealDefaults.save(MealDefaultSelection.toAssignments(draft)).fold(
                onSuccess = {
                    saving = false
                    baseline = draft
                    savedState[KEY_BASELINE] = encodeRows(baseline)
                    publish()
                    effectChannel.trySend(MealDefaultsEffect.Close)
                },
                onFailure = {
                    saving = false
                    saveError = if ((it as? AppException)?.error == AppError.Network) {
                        MealDefaultsSaveError.Offline
                    } else {
                        MealDefaultsSaveError.Failed
                    }
                    publish()
                },
            )
        }
    }

    private fun isIdle() = catalog != null && !saving

    private fun isDirty() = draft.toSet() != baseline.toSet()

    private fun persistDraft() {
        savedState[KEY_DRAFT] = encodeRows(draft)
    }

    private fun publish() {
        _uiState.value = render()
    }

    private fun render(): MealDefaultsUiState {
        val loaded = catalog
        return when {
            loaded != null -> content(loaded)
            loadFailed -> MealDefaultsUiState.LoadError
            else -> MealDefaultsUiState.Loading
        }
    }

    private fun content(loaded: Catalog): MealDefaultsUiState.Content {
        val templatesById = loaded.templates.associateBy { it.id }
        val options = loaded.templates.associate { it.id to option(it, loaded.prefs) }
        val dirty = isDirty()
        val slots = loaded.slots.sortedBy { it.position }
        return MealDefaultsUiState.Content(
            meals = slots.map { slot ->
                MealDefaultsCardUi(
                    slotId = slot.id,
                    label = slot.label,
                    assignments = MealDefaultSelection.defaultsOf(draft, slot.id).mapNotNull { a ->
                        options[a.templateId]?.let {
                            AssignmentUi(it.templateId, it.name, it.foodCount, it.kcal, a.weekdays)
                        }
                    },
                    canAdd = !MealDefaultSelection.isEveryDayAssigned(draft, slot.id),
                )
            },
            comidas = options.values.toList(),
            hasSavedComidas = loaded.templates.isNotEmpty(),
            isOnline = online,
            isSaving = saving,
            isDirty = dirty,
            canSave = dirty && online && !saving,
            showDiscardConfirm = discardAsked,
            saveError = saveError,
            picker = picker?.takeIf { id -> slots.any { it.id == id } },
            sheet = sheet?.let { sheetUi(it, slots, templatesById) },
        )
    }

    private fun sheetUi(
        current: SheetDraft,
        slots: List<MealSlot>,
        templates: Map<String, MealTemplate>,
    ): AssignSheetUi? {
        val slot = slots.firstOrNull { it.id == current.slotId }
        val template = templates[current.templateId]
        if (slot == null || template == null) return null
        val names = draft.filter { it.mealSlotId == slot.id && it.templateId != template.id }
            .associate { it.weekday to (templates[it.templateId]?.name.orEmpty()) }
        return AssignSheetUi(
            slotId = slot.id,
            mealLabel = slot.label,
            templateId = template.id,
            templateName = template.name,
            selectedDays = current.days,
            conflicts = names,
            canSave = current.days.isNotEmpty(),
        )
    }

    private fun option(template: MealTemplate, prefs: FoodPreferences?) = DefaultComidaOptionUi(
        templateId = template.id,
        name = template.name,
        foodCount = template.items.size,
        kcal = cookedItemNutrition.total(template.items, prefs).kcal,
    )
}

private fun encodeRows(rows: List<MealDefault>): String =
    rows.joinToString(ROW_SEPARATOR) { "${it.mealSlotId}$FIELD_SEPARATOR${it.templateId}$FIELD_SEPARATOR${it.weekday}" }

private fun decodeRows(text: String?): List<MealDefault> = text.orEmpty().split(ROW_SEPARATOR).mapNotNull { row ->
    val parts = row.split(FIELD_SEPARATOR)
    val day = parts.getOrNull(2)?.toIntOrNull()
    if (parts.size == SHEET_FIELDS && day != null) MealDefault(parts[0], parts[1], day) else null
}

private fun encodeSheet(sheet: SheetDraft): String =
    listOf(sheet.slotId, sheet.templateId, sheet.days.sorted().joinToString(DAY_SEPARATOR))
        .joinToString(FIELD_SEPARATOR)

private fun decodeSheet(text: String): SheetDraft? {
    val parts = text.split(FIELD_SEPARATOR)
    if (parts.size != SHEET_FIELDS) return null
    return SheetDraft(parts[0], parts[1], parts[2].split(DAY_SEPARATOR).mapNotNull { it.toIntOrNull() }.toSet())
}
