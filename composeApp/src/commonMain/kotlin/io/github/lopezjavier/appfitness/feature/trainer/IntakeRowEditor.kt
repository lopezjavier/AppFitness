package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import io.github.lopezjavier.appfitness.domain.repository.CoachIntakeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The small sheet of one intake row (R-55): one value, one editor. */
sealed interface IntakeRowSheet {
    /** [years] null is "Sin indicar", the lowest step of the stepper. */
    data class Years(val years: Double?) : IntakeRowSheet

    /** [text] is what the user typed; empty clears the value. */
    data class Maintenance(val text: String) : IntakeRowSheet {
        /** The parsed value, or null when [text] is empty or invalid. */
        val kcal: Int? get() = text.trim().toIntOrNull()?.takeIf { it in IntakeLimits.MIN_KCAL..IntakeLimits.MAX_KCAL }

        /** "Entre 1200 y 6000 kcal": a non-empty text that is not an integer in range. */
        val invalid: Boolean get() = text.isNotBlank() && kcal == null
    }
}

data class IntakeRowEditorState(
    val sheet: IntakeRowSheet? = null,
    val saving: Boolean = false,
    val notice: IntakeNotice? = null,
) {
    val canSave: Boolean
        get() = sheet != null && !saving && (sheet as? IntakeRowSheet.Maintenance)?.invalid != true
}

sealed interface IntakeRowEvent {
    /** [current] is the stored value, the starting point of the stepper. */
    data class OpenYears(val current: Double?) : IntakeRowEvent

    data class OpenMaintenance(val current: Int?) : IntakeRowEvent

    data object YearsUp : IntakeRowEvent

    data object YearsDown : IntakeRowEvent

    data class EditMaintenance(val text: String) : IntakeRowEvent

    data object Save : IntakeRowEvent

    /** X, back or "Cancelar": the draft is dropped silently (R-14 for sheets). */
    data object Close : IntakeRowEvent
}

/**
 * Editor state shared by "Antes de empezar" and "Tus datos" for the years and maintenance rows. The open sheet and its
 * draft live in [savedState] (ADR-40). Saving sends only the edited value; a failure keeps the sheet and the draft.
 */
class IntakeRowEditor(
    private val repository: CoachIntakeRepository,
    private val scope: CoroutineScope,
    private val savedState: SavedStateHandle,
    private val onSaved: (CoachIntake) -> Unit,
) {
    private val _state = MutableStateFlow(IntakeRowEditorState(sheet = restoredSheet()))
    val state: StateFlow<IntakeRowEditorState> = _state.asStateFlow()

    fun onEvent(event: IntakeRowEvent) {
        when (event) {
            is IntakeRowEvent.OpenYears -> setSheet(IntakeRowSheet.Years(event.current))

            is IntakeRowEvent.OpenMaintenance -> setSheet(
                IntakeRowSheet.Maintenance(event.current?.toString().orEmpty()),
            )

            IntakeRowEvent.YearsUp -> stepYears(+1)

            IntakeRowEvent.YearsDown -> stepYears(-1)

            is IntakeRowEvent.EditMaintenance -> edit(IntakeRowSheet.Maintenance(event.text.take(MAX_DIGITS)))

            IntakeRowEvent.Save -> save()

            IntakeRowEvent.Close -> close()
        }
    }

    private fun edit(next: IntakeRowSheet) {
        val current = _state.value.sheet ?: return
        if (current::class != next::class || _state.value.saving) return
        setSheet(next)
    }

    private fun stepYears(direction: Int) {
        val sheet = _state.value.sheet as? IntakeRowSheet.Years ?: return
        if (_state.value.saving) return
        val years = sheet.years
        val next = if (direction > 0) {
            years?.let { minOf(it + IntakeLimits.YEARS_STEP, IntakeLimits.MAX_YEARS) } ?: IntakeLimits.MIN_YEARS
        } else {
            years?.takeIf { it > IntakeLimits.MIN_YEARS }?.let { it - IntakeLimits.YEARS_STEP }
        }
        setSheet(IntakeRowSheet.Years(next))
    }

    private fun setSheet(sheet: IntakeRowSheet) {
        _state.update { it.copy(sheet = sheet, notice = null) }
        persist(sheet)
    }

    private fun close() {
        _state.value = IntakeRowEditorState()
        savedState.remove<String>(KEY_KIND)
        savedState.remove<String>(KEY_VALUE)
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave) return
        val update = when (val sheet = current.sheet) {
            is IntakeRowSheet.Years -> IntakeUpdate.TrainingYears(sheet.years)
            is IntakeRowSheet.Maintenance -> IntakeUpdate.KnownMaintenance(sheet.kcal)
            null -> return
        }
        _state.update { it.copy(saving = true, notice = null) }
        scope.launch {
            val result = repository.save(update)
            result.fold(
                onSuccess = { intake ->
                    close()
                    onSaved(intake)
                },
                onFailure = {
                    val notice = if (result.appErrorOrNull() == AppError.Network) {
                        IntakeNotice.SaveOffline
                    } else {
                        IntakeNotice.SaveFailed
                    }
                    _state.update { it.copy(saving = false, notice = notice) }
                },
            )
        }
    }

    // --- Restoration (ADR-40): the kind and the draft value as strings. ---

    private fun persist(sheet: IntakeRowSheet) {
        when (sheet) {
            is IntakeRowSheet.Years -> {
                savedState[KEY_KIND] = KIND_YEARS
                savedState[KEY_VALUE] = sheet.years?.toString().orEmpty()
            }

            is IntakeRowSheet.Maintenance -> {
                savedState[KEY_KIND] = KIND_MAINTENANCE
                savedState[KEY_VALUE] = sheet.text
            }
        }
    }

    private fun restoredSheet(): IntakeRowSheet? {
        val value = savedState.get<String>(KEY_VALUE).orEmpty()
        return when (savedState.get<String>(KEY_KIND)) {
            KIND_YEARS -> IntakeRowSheet.Years(value.toDoubleOrNull())
            KIND_MAINTENANCE -> IntakeRowSheet.Maintenance(value)
            else -> null
        }
    }

    private companion object {
        const val KEY_KIND = "intake_row_sheet"
        const val KEY_VALUE = "intake_row_value"
        const val KIND_YEARS = "years"
        const val KIND_MAINTENANCE = "maintenance"
        const val MAX_DIGITS = 6
    }
}
