package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.CoachIntakeDraft
import io.github.lopezjavier.appfitness.domain.model.InjuredArea
import io.github.lopezjavier.appfitness.domain.model.PreBreakBest
import io.github.lopezjavier.appfitness.domain.model.Situation

/** Ranges that mirror the server validation of `save_coach_intake`. */
object IntakeLimits {
    const val MAX_FREE_TEXT = 500
    const val MAX_YEARS = 80.0
    const val YEARS_STEP = 0.5
    const val MIN_KCAL = 800
    const val MAX_KCAL = 6000
    const val MIN_REPS = 1
    const val MAX_REPS = 30
    const val MAX_BESTS = 10
}

/** The fields of the form that can be filled from the free text (and so carry the "extracted" marker). */
enum class IntakeField { Years, Situation, InjuredArea, Bests, Maintenance, CutKcal }

/** The editable form. Null means "Sin indicar": the coach never guesses. */
data class IntakeForm(
    val years: Double? = null,
    val situation: Situation? = null,
    val injuredArea: InjuredArea? = null,
    val bests: List<PreBreakBest> = emptyList(),
    val maintenanceKcal: Int? = null,
    val cutKcal: Int? = null,
    val freeText: String = "",
) {
    /** What the extraction must not overwrite: every field the user (or an earlier extraction) already holds. */
    fun toDraft() = CoachIntakeDraft(years, situation, injuredArea, bests, maintenanceKcal, cutKcal)

    /** Blank text counts as nothing. Fields outside the form (the handover flag) are kept from [base]. */
    fun toIntake(base: CoachIntake? = null) = CoachIntake(
        trainingYears = years,
        situation = situation,
        injuredArea = injuredArea,
        preBreakBests = bests,
        knownMaintenanceKcal = maintenanceKcal,
        knownCutKcal = cutKcal,
        freeText = freeText.takeIf { it.isNotBlank() },
        maintenanceHandoverPending = base?.maintenanceHandoverPending,
    )

    /** Fields whose value breaks a server range; "Guardar" stays disabled while any exists. */
    val errors: Set<IntakeField>
        get() = buildSet {
            if (years.invalidYears()) add(IntakeField.Years)
            if (maintenanceKcal.outOfKcalRange()) add(IntakeField.Maintenance)
            if (cutKcal.outOfKcalRange()) add(IntakeField.CutKcal)
        }

    companion object {
        fun from(intake: CoachIntake?) = IntakeForm(
            years = intake?.trainingYears,
            situation = intake?.situation,
            injuredArea = intake?.injuredArea,
            bests = intake?.preBreakBests.orEmpty(),
            maintenanceKcal = intake?.knownMaintenanceKcal,
            cutKcal = intake?.knownCutKcal,
            freeText = intake?.freeText.orEmpty(),
        )
    }
}

private fun Double?.invalidYears() =
    this != null && (this !in 0.0..IntakeLimits.MAX_YEARS || (this / IntakeLimits.YEARS_STEP) % 1.0 != 0.0)

private fun Int?.outOfKcalRange() = this != null && (this < IntakeLimits.MIN_KCAL || this > IntakeLimits.MAX_KCAL)

/** A best lift the form accepts: 1-30 reps and a positive weight. */
fun PreBreakBest.isValid() = reps in IntakeLimits.MIN_REPS..IntakeLimits.MAX_REPS && weightKg > 0.0

enum class IntakePhase { Editing, Extracting, Saving }

/** The sheets of the choice fields (apply on tap, R-12). */
enum class IntakePicker { Situation, InjuredArea }

/** An inline message under the action that failed (R-31); the fields stay editable. */
sealed interface IntakeNotice {
    /** The budget or the provider refused the extraction; worded with `CoachLimitMessage`. */
    data class Limit(val failure: CoachFailure) : IntakeNotice

    data object ExtractOffline : IntakeNotice

    /** The model answer was unusable or the call failed for another reason. */
    data object ExtractFailed : IntakeNotice

    data object SaveFailed : IntakeNotice

    data object SaveOffline : IntakeNotice
}

sealed interface IntakeUiState {
    data object Loading : IntakeUiState

    /**
     * [extracted] are the fields filled from the text and not touched since (they carry the marker). [baseline] is the
     * stored form: [dirty] is true when [form] differs from it.
     */
    data class Content(
        val form: IntakeForm,
        val baseline: IntakeForm,
        val extracted: Set<IntakeField> = emptySet(),
        val phase: IntakePhase = IntakePhase.Editing,
        val notice: IntakeNotice? = null,
        val discardDialog: Boolean = false,
        val picker: IntakePicker? = null,
    ) : IntakeUiState {
        val dirty: Boolean get() = form != baseline
        val remainingChars: Int get() = IntakeLimits.MAX_FREE_TEXT - form.freeText.length
        val canExtract: Boolean get() = phase == IntakePhase.Editing && form.freeText.isNotBlank()
        val canSave: Boolean get() = phase == IntakePhase.Editing && form.errors.isEmpty()
        val canAddBest: Boolean get() = form.bests.size < IntakeLimits.MAX_BESTS
    }

    data object Offline : IntakeUiState

    data object Error : IntakeUiState
}

sealed interface IntakeEvent {
    data object Retry : IntakeEvent

    /** `null` returns the stepper to "Sin indicar". */
    data class EditYears(val years: Double?) : IntakeEvent

    data class SelectSituation(val situation: Situation?) : IntakeEvent

    data class SelectInjuredArea(val area: InjuredArea?) : IntakeEvent

    data class OpenPicker(val picker: IntakePicker) : IntakeEvent

    data object ClosePicker : IntakeEvent

    /** Ignored when invalid, when the exercise is already listed or when ten bests exist. */
    data class AddBest(val best: PreBreakBest) : IntakeEvent

    /** Replaces the best of the same exercise; ignored when invalid. */
    data class EditBest(val best: PreBreakBest) : IntakeEvent

    data class RemoveBest(val exerciseId: String) : IntakeEvent

    data class EditMaintenance(val kcal: Int?) : IntakeEvent

    data class EditCutKcal(val kcal: Int?) : IntakeEvent

    /** Text beyond 500 characters is cut. */
    data class EditFreeText(val text: String) : IntakeEvent

    /** "Rellenar con mi texto". */
    data object Extract : IntakeEvent

    data object Save : IntakeEvent

    /** "Ahora no": remembers the prompt was shown and leaves; saves nothing. */
    data object Skip : IntakeEvent

    data object Back : IntakeEvent

    data object ConfirmDiscard : IntakeEvent

    data object DismissDiscard : IntakeEvent
}

sealed interface IntakeEffect {
    data object Saved : IntakeEffect

    data object Skipped : IntakeEffect

    data object Close : IntakeEffect
}
