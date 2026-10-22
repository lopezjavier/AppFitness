package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.BodyMeasurementCheck
import io.github.lopezjavier.appfitness.domain.usecase.BodyMeasurementProblem
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import kotlin.math.roundToInt

private const val TENTHS = 10.0
private const val TENTHS_INT = 10

/** The form as typed: the texts are kept raw so a half-typed number survives. Only the day matters, not the hour. */
internal data class MeasurementDraft(val existingId: String?, val weightText: String, val fatText: String) {
    val weightKg: Double? get() = parseDecimal(weightText)
    val bodyFatPct: Double? get() = parseDecimal(fatText)
}

/** Parses a number typed with a decimal comma or point; null for blank or non-numeric text. */
internal fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** One decimal with a comma, as the screen shows it ("73,9"). */
internal fun decimalText(value: Double): String {
    val tenths = (value * TENTHS).roundToInt()
    return "${tenths / TENTHS_INT},${tenths % TENTHS_INT}"
}

/** The draft that edits [row]: its own values. */
internal fun editDraft(row: BodyMeasurement) = MeasurementDraft(
    existingId = row.id,
    weightText = decimalText(row.weightKg),
    fatText = row.bodyFatPct?.let(::decimalText).orEmpty(),
)

/** The form state of [draft]. A blank field reports no problem; the weight is still required to save. */
internal fun formOf(
    draft: MeasurementDraft,
    validate: ValidateBodyMeasurement,
    online: Boolean,
    saving: Boolean,
    saveError: WeightSaveError?,
): MeasurementForm {
    val weight = draft.weightKg
    val fat = draft.bodyFatPct
    val weightProblem = when {
        draft.weightText.isBlank() -> null
        weight == null -> BodyMeasurementProblem.NotANumber
        else -> (validate(weight, null) as? BodyMeasurementCheck.Invalid)?.problem
    }
    val fatProblem = when {
        draft.fatText.isBlank() -> null
        fat == null -> BodyMeasurementProblem.NotANumber
        else -> (validate(ValidateBodyMeasurement.WEIGHT_MIN, fat) as? BodyMeasurementCheck.Invalid)?.problem
    }
    return MeasurementForm(
        existingId = draft.existingId,
        weightText = draft.weightText,
        fatText = draft.fatText,
        weightProblem = weightProblem,
        fatProblem = fatProblem,
        canSave = weight != null && weightProblem == null && fatProblem == null && online && !saving,
        canDelete = draft.existingId != null && online && !saving,
        isSaving = saving,
        saveError = saveError,
    )
}
