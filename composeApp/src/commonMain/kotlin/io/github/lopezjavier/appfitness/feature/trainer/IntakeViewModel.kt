package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.CoachIntakeDraft
import io.github.lopezjavier.appfitness.domain.model.InjuredArea
import io.github.lopezjavier.appfitness.domain.model.PreBreakBest
import io.github.lopezjavier.appfitness.domain.model.Situation
import io.github.lopezjavier.appfitness.domain.repository.CoachIntakeRepository
import io.github.lopezjavier.appfitness.domain.repository.IntakePromptPreference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Para conocerte" (US-81): the intake form plus the free-text extraction. Nothing is stored until [IntakeEvent.Save];
 * the extraction only fills empty fields and marks them. The whole form, the markers, the open picker and the discard
 * dialog survive recreation through [savedState] (ADR-40); the stored intake is always re-fetched and the draft wins.
 */
@Suppress("TooManyFunctions") // One small handler per event.
class IntakeViewModel(
    private val repository: CoachIntakeRepository,
    private val prompt: IntakePromptPreference,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<IntakeUiState>(IntakeUiState.Loading)
    val uiState: StateFlow<IntakeUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<IntakeEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var stored: CoachIntake? = null

    init {
        load()
    }

    fun onEvent(event: IntakeEvent) {
        when (event) {
            IntakeEvent.Retry -> load()
            is IntakeEvent.OpenPicker -> update { copy(picker = event.picker) }
            IntakeEvent.ClosePicker -> update { copy(picker = null) }
            IntakeEvent.Extract -> extract()
            IntakeEvent.Save -> save()
            IntakeEvent.Skip -> skip()
            IntakeEvent.Back -> back()
            IntakeEvent.ConfirmDiscard -> confirmDiscard()
            IntakeEvent.DismissDiscard -> update { copy(discardDialog = false) }
            else -> onFieldEvent(event)
        }
    }

    private fun onFieldEvent(event: IntakeEvent) {
        when (event) {
            is IntakeEvent.EditYears -> edit(IntakeField.Years) { copy(years = event.years) }
            is IntakeEvent.SelectSituation -> edit(IntakeField.Situation) { copy(situation = event.situation) }
            is IntakeEvent.SelectInjuredArea -> edit(IntakeField.InjuredArea) { copy(injuredArea = event.area) }
            is IntakeEvent.AddBest -> addBest(event.best)
            is IntakeEvent.EditBest -> editBest(event.best)
            is IntakeEvent.RemoveBest -> removeBest(event.exerciseId)
            is IntakeEvent.EditMaintenance -> edit(IntakeField.Maintenance) { copy(maintenanceKcal = event.kcal) }
            is IntakeEvent.EditCutKcal -> edit(IntakeField.CutKcal) { copy(cutKcal = event.kcal) }
            is IntakeEvent.EditFreeText -> editFreeText(event.text)
            else -> Unit
        }
    }

    private fun removeBest(exerciseId: String) =
        edit(IntakeField.Bests) { copy(bests = bests.filter { it.exerciseId != exerciseId }) }

    private fun load() {
        _uiState.value = IntakeUiState.Loading
        viewModelScope.launch {
            val result = repository.get()
            _uiState.value = result.fold(
                onSuccess = { intake ->
                    stored = intake
                    val baseline = IntakeForm.from(intake)
                    restoredContent(baseline) ?: IntakeUiState.Content(baseline, baseline)
                },
                onFailure = {
                    if (result.appErrorOrNull() == AppError.Network) IntakeUiState.Offline else IntakeUiState.Error
                },
            )
        }
    }

    private fun update(transform: IntakeUiState.Content.() -> IntakeUiState.Content) {
        _uiState.update { state -> if (state is IntakeUiState.Content) state.transform() else state }
        persist()
    }

    /** A field edit: applies it, clears the field's marker (first touch) and any stale notice. */
    private fun edit(field: IntakeField, change: IntakeForm.() -> IntakeForm) =
        update { copy(form = form.change(), extracted = extracted - field, notice = null) }

    private fun editFreeText(text: String) =
        update { copy(form = form.copy(freeText = text.take(IntakeLimits.MAX_FREE_TEXT)), notice = null) }

    private fun addBest(best: PreBreakBest) {
        val content = _uiState.value as? IntakeUiState.Content ?: return
        if (!best.isValid() || !content.canAddBest ||
            content.form.bests.any { it.exerciseId == best.exerciseId }
        ) {
            return
        }
        edit(IntakeField.Bests) { copy(bests = bests + best) }
    }

    private fun editBest(best: PreBreakBest) {
        val content = _uiState.value as? IntakeUiState.Content ?: return
        if (!best.isValid() || content.form.bests.none { it.exerciseId == best.exerciseId }) return
        edit(IntakeField.Bests) { copy(bests = bests.map { if (it.exerciseId == best.exerciseId) best else it }) }
    }

    private fun extract() {
        val content = _uiState.value as? IntakeUiState.Content ?: return
        if (!content.canExtract) return
        update { copy(phase = IntakePhase.Extracting, notice = null) }
        val sent = content.form
        viewModelScope.launch {
            val result = repository.extract(sent.freeText, sent.toDraft())
            result.fold(
                onSuccess = { draft -> update { applyDraft(draft) } },
                onFailure = { error ->
                    val notice = when {
                        result.appErrorOrNull() == AppError.Network -> IntakeNotice.ExtractOffline
                        error is CoachException && error.failure.isLimit() -> IntakeNotice.Limit(error.failure)
                        else -> IntakeNotice.ExtractFailed
                    }
                    update { copy(phase = IntakePhase.Editing, notice = notice) }
                },
            )
        }
    }

    /** Fills only the empty fields with valid values; the user's own answers stay as they are. */
    private fun IntakeUiState.Content.applyDraft(draft: CoachIntakeDraft): IntakeUiState.Content {
        val filled = mutableSetOf<IntakeField>()
        var next = form
        draft.trainingYears?.takeIf { next.years == null && IntakeForm(years = it).errors.isEmpty() }?.let {
            next = next.copy(years = it)
            filled += IntakeField.Years
        }
        draft.situation?.takeIf { next.situation == null }?.let {
            next = next.copy(situation = it)
            filled += IntakeField.Situation
        }
        draft.injuredArea?.takeIf { next.injuredArea == null }?.let {
            next = next.copy(injuredArea = it)
            filled += IntakeField.InjuredArea
        }
        val bests = draft.preBreakBests.filter { it.isValid() }.distinctBy { it.exerciseId }
            .take(IntakeLimits.MAX_BESTS)
        if (next.bests.isEmpty() && bests.isNotEmpty()) {
            next = next.copy(bests = bests)
            filled += IntakeField.Bests
        }
        draft.knownMaintenanceKcal?.takeIf {
            next.maintenanceKcal == null &&
                IntakeForm(maintenanceKcal = it).errors.isEmpty()
        }
            ?.let {
                next = next.copy(maintenanceKcal = it)
                filled += IntakeField.Maintenance
            }
        draft.knownCutKcal?.takeIf { next.cutKcal == null && IntakeForm(cutKcal = it).errors.isEmpty() }?.let {
            next = next.copy(cutKcal = it)
            filled += IntakeField.CutKcal
        }
        return copy(form = next, extracted = extracted + filled, phase = IntakePhase.Editing)
    }

    private fun save() {
        val content = _uiState.value as? IntakeUiState.Content ?: return
        if (!content.canSave) return
        update { copy(phase = IntakePhase.Saving, notice = null) }
        viewModelScope.launch {
            val result = repository.save(content.form.toIntake(stored))
            result.fold(
                onSuccess = { intake ->
                    stored = intake
                    prompt.markAsked()
                    update { copy(baseline = content.form, extracted = emptySet(), phase = IntakePhase.Editing) }
                    clearDraft()
                    effectChannel.send(IntakeEffect.Saved)
                },
                onFailure = {
                    val notice = if (result.appErrorOrNull() == AppError.Network) {
                        IntakeNotice.SaveOffline
                    } else {
                        IntakeNotice.SaveFailed
                    }
                    update { copy(phase = IntakePhase.Editing, notice = notice) }
                },
            )
        }
    }

    private fun skip() {
        viewModelScope.launch {
            prompt.markAsked()
            effectChannel.send(IntakeEffect.Skipped)
        }
    }

    private fun back() {
        val content = _uiState.value as? IntakeUiState.Content
        if (content?.dirty == true) {
            update { copy(discardDialog = true) }
        } else {
            viewModelScope.launch { effectChannel.send(IntakeEffect.Close) }
        }
    }

    private fun confirmDiscard() {
        update { copy(discardDialog = false) }
        clearDraft()
        viewModelScope.launch { effectChannel.send(IntakeEffect.Close) }
    }

    private fun CoachFailure.isLimit() =
        this is CoachFailure.LimitReached || this == CoachFailure.NoCredit || this == CoachFailure.Timeout

    // --- Restoration (ADR-40): primitives and lists of strings only. ---

    @Suppress("ReturnCount") // Guards for a missing or unreadable draft.
    private fun restoredContent(baseline: IntakeForm): IntakeUiState.Content? {
        val encoded = savedState.get<ArrayList<String>>(KEY_FORM) ?: return null
        val form = decodeForm(encoded) ?: return null
        return IntakeUiState.Content(
            form = form,
            baseline = baseline,
            extracted = savedState.get<ArrayList<String>>(KEY_EXTRACTED).orEmpty()
                .mapNotNull { name -> IntakeField.entries.firstOrNull { it.name == name } }.toSet(),
            discardDialog = savedState.get<Boolean>(KEY_DISCARD) == true,
            picker = savedState.get<String>(KEY_PICKER)?.let { name ->
                IntakePicker.entries.firstOrNull {
                    it.name ==
                        name
                }
            },
        )
    }

    private fun persist() {
        val content = _uiState.value as? IntakeUiState.Content ?: return
        if (!content.dirty && !content.discardDialog && content.picker == null) return clearDraft()
        savedState[KEY_FORM] = encodeForm(content.form)
        savedState[KEY_EXTRACTED] = ArrayList(content.extracted.map { it.name })
        savedState[KEY_DISCARD] = content.discardDialog
        content.picker?.let { savedState[KEY_PICKER] = it.name } ?: savedState.remove<String>(KEY_PICKER)
    }

    private fun clearDraft() {
        savedState.remove<ArrayList<String>>(KEY_FORM)
        savedState.remove<ArrayList<String>>(KEY_EXTRACTED)
        savedState.remove<Boolean>(KEY_DISCARD)
        savedState.remove<String>(KEY_PICKER)
    }

    private fun encodeForm(form: IntakeForm): ArrayList<String> = arrayListOf(
        form.years?.toString().orEmpty(),
        form.situation?.name.orEmpty(),
        form.injuredArea?.name.orEmpty(),
        form.maintenanceKcal?.toString().orEmpty(),
        form.cutKcal?.toString().orEmpty(),
        form.freeText,
    ).apply { form.bests.forEach { add("${it.exerciseId}|${it.weightKg}|${it.reps}") } }

    private fun decodeForm(parts: List<String>): IntakeForm? {
        if (parts.size < FORM_FIELDS) return null
        return IntakeForm(
            years = parts[F_YEARS].toDoubleOrNull(),
            situation = Situation.entries.firstOrNull { it.name == parts[F_SITUATION] },
            injuredArea = InjuredArea.entries.firstOrNull { it.name == parts[F_AREA] },
            maintenanceKcal = parts[F_MAINTENANCE].toIntOrNull(),
            cutKcal = parts[F_CUT].toIntOrNull(),
            freeText = parts[F_TEXT],
            bests = parts.drop(FORM_FIELDS).mapNotNull(::decodeBest),
        )
    }

    @Suppress("ReturnCount") // Guards for a malformed entry.
    private fun decodeBest(text: String): PreBreakBest? {
        val parts = text.split('|')
        if (parts.size != BEST_PARTS) return null
        val weight = parts[B_WEIGHT].toDoubleOrNull() ?: return null
        val reps = parts[B_REPS].toIntOrNull() ?: return null
        return PreBreakBest(parts[B_ID], weight, reps)
    }

    private companion object {
        const val KEY_FORM = "intake_form"
        const val KEY_EXTRACTED = "intake_extracted"
        const val KEY_PICKER = "intake_picker"
        const val KEY_DISCARD = "intake_discard"
        const val FORM_FIELDS = 6
        const val BEST_PARTS = 3
        const val F_YEARS = 0
        const val F_SITUATION = 1
        const val F_AREA = 2
        const val F_MAINTENANCE = 3
        const val F_CUT = 4
        const val F_TEXT = 5
        const val B_ID = 0
        const val B_WEIGHT = 1
        const val B_REPS = 2
    }
}
