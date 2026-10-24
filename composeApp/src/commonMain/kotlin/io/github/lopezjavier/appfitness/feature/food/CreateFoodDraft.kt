package io.github.lopezjavier.appfitness.feature.food

import io.github.lopezjavier.appfitness.domain.model.EditableFood
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.NewFood
import io.github.lopezjavier.appfitness.domain.model.NewMicronutrients
import io.github.lopezjavier.appfitness.domain.model.NewPortion
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.model.UpdatedFood
import kotlin.math.roundToLong

private const val ROW_SEPARATOR = '\u0001'
private const val DECIMALS_FACTOR = 10_000.0
private const val DECIMALS_DIGITS = 4

/** A portion row plus whether the user has edited it (errors only show for edited rows). */
internal data class PortionRow(val label: String, val amount: String, val touched: Boolean)

/**
 * Everything the user typed. [touched] holds the names of the [CreateFoodField]s and [MicronutrientKind]s already
 * edited (their names never clash).
 */
internal data class FormDraft(
    val fields: Map<CreateFoodField, String>,
    val nutrients: Map<MicronutrientKind, String>,
    val baseUnit: String,
    val barcode: String?,
    val portions: List<PortionRow>,
    val touched: Set<String>,
) {
    fun text(field: CreateFoodField): String = fields[field].orEmpty()

    fun text(kind: MicronutrientKind): String = nutrients[kind].orEmpty()

    private fun number(field: CreateFoodField, blankIsZero: Boolean): Double {
        val text = text(field).trim().replace(',', '.')
        return if (text.isEmpty() && blankIsZero) 0.0 else text.toDoubleOrNull() ?: Double.NaN
    }

    private fun toNutrition() = NutritionPer100(
        kcal = number(CreateFoodField.Kcal, blankIsZero = false),
        proteinG = number(CreateFoodField.Protein, blankIsZero = true),
        carbsG = number(CreateFoodField.Carbs, blankIsZero = true),
        fatG = number(CreateFoodField.Fat, blankIsZero = true),
    )

    private fun toPortions() = portions.map {
        NewPortion(it.label.trim(), it.amount.trim().replace(',', '.').toDoubleOrNull() ?: Double.NaN)
    }

    /** Blank is "not typed" (absent); anything else that is not a number becomes NaN so validation flags it. */
    private fun toMicronutrients() = NewMicronutrients(
        MicronutrientKind.editableKinds.mapNotNull { kind ->
            val typed = text(kind).trim().replace(',', '.')
            if (typed.isEmpty()) null else kind to (typed.toDoubleOrNull() ?: Double.NaN)
        }.toMap(),
    )

    fun toNewFood() = NewFood(
        name = text(CreateFoodField.Name).trim(),
        brand = text(CreateFoodField.Brand).trim().ifEmpty { null },
        baseUnit = baseUnit,
        nutrition = toNutrition(),
        barcode = barcode,
        portions = toPortions(),
        micronutrients = toMicronutrients(),
    )

    fun toUpdatedFood(id: String) = UpdatedFood(
        id = id,
        name = text(CreateFoodField.Name).trim(),
        brand = text(CreateFoodField.Brand).trim().ifEmpty { null },
        nutrition = toNutrition(),
        portions = toPortions(),
        micronutrients = toMicronutrients(),
        baseUnit = baseUnit,
    )

    companion object {
        fun blank(name: String? = null, barcode: String? = null) = FormDraft(
            fields = CreateFoodField.entries.associateWith { if (it == CreateFoodField.Name) name.orEmpty() else "" },
            nutrients = MicronutrientKind.editableKinds.associateWith { "" },
            baseUnit = "g",
            barcode = barcode?.takeIf { it.isNotBlank() },
            portions = emptyList(),
            touched = emptySet(),
        )

        /** The draft of an existing food: stored grams are shown in each nutrient's label unit. */
        fun from(editable: EditableFood): FormDraft {
            val food = editable.food
            val nutrition = food.nutrition
            return FormDraft(
                fields = mapOf(
                    CreateFoodField.Name to food.name,
                    CreateFoodField.Brand to food.brand.orEmpty(),
                    CreateFoodField.Kcal to formatEditable(nutrition.kcal),
                    CreateFoodField.Protein to formatEditable(nutrition.proteinG),
                    CreateFoodField.Carbs to formatEditable(nutrition.carbsG),
                    CreateFoodField.Fat to formatEditable(nutrition.fatG),
                ),
                nutrients = MicronutrientKind.editableKinds.associateWith { kind ->
                    editable.micronutrients[kind]?.let { formatEditable(kind.labelUnit.fromGrams(it)) }.orEmpty()
                },
                baseUnit = food.baseUnit,
                barcode = null,
                portions = food.portions.map { PortionRow(it.label, formatEditable(it.amount), touched = false) },
                touched = emptySet(),
            )
        }
    }
}

/** [value] as the text of a field: comma decimal, no grouping, up to four decimals without trailing zeros. */
internal fun formatEditable(value: Double): String {
    val scaled = (value * DECIMALS_FACTOR).roundToLong()
    val whole = scaled / DECIMALS_FACTOR.toLong()
    val decimals = (scaled % DECIMALS_FACTOR.toLong()).toString().padStart(DECIMALS_DIGITS, '0').trimEnd('0')
    return if (decimals.isEmpty()) whole.toString() else "$whole,$decimals"
}

internal fun encodeRow(row: PortionRow) = "${row.label}$ROW_SEPARATOR${row.amount}$ROW_SEPARATOR${row.touched}"

internal fun decodeRow(encoded: String): PortionRow {
    val parts = encoded.split(ROW_SEPARATOR)
    return PortionRow(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }, parts.getOrNull(2) == "true")
}
