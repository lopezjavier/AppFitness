package io.github.lopezjavier.appfitness.feature.food

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_biotin
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_calcium
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_chloride
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_chromium
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_copper
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_fiber
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_fluoride
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_iodine
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_iron
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_magnesium
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_manganese
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_molybdenum
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_monounsaturated_fat
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_pantothenic_acid
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_phosphorus
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_polyols
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_polyunsaturated_fat
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_potassium
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_salt
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_saturated_fat
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_selenium
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_sodium
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_starch
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_sugars
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_unit_g
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_unit_mg
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_unit_ug
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_vitamin_a
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_vitamin_b1
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_vitamin_b12
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_vitamin_b2
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_vitamin_b3
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_vitamin_b6
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_vitamin_b9
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_vitamin_c
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_vitamin_d
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_vitamin_e
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_vitamin_k
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrient_zinc
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import org.jetbrains.compose.resources.StringResource
import kotlin.math.floor

private const val HUNDREDTHS = 100
private const val HALF = 0.5
private const val TWO_DIGITS = 10

/** The Spanish label of [kind] (a string resource, the domain stays free of UI text). */
@Suppress("CyclomaticComplexMethod") // A flat mapping of one resource per nutrient.
internal fun micronutrientLabel(kind: MicronutrientKind): StringResource = when (kind) {
    MicronutrientKind.Fiber -> Res.string.micronutrient_fiber
    MicronutrientKind.Sugars -> Res.string.micronutrient_sugars
    MicronutrientKind.SaturatedFat -> Res.string.micronutrient_saturated_fat
    MicronutrientKind.Salt -> Res.string.micronutrient_salt
    MicronutrientKind.MonounsaturatedFat -> Res.string.micronutrient_monounsaturated_fat
    MicronutrientKind.PolyunsaturatedFat -> Res.string.micronutrient_polyunsaturated_fat
    MicronutrientKind.Polyols -> Res.string.micronutrient_polyols
    MicronutrientKind.Starch -> Res.string.micronutrient_starch
    MicronutrientKind.Sodium -> Res.string.micronutrient_sodium
    MicronutrientKind.VitaminA -> Res.string.micronutrient_vitamin_a
    MicronutrientKind.VitaminD -> Res.string.micronutrient_vitamin_d
    MicronutrientKind.VitaminE -> Res.string.micronutrient_vitamin_e
    MicronutrientKind.VitaminK -> Res.string.micronutrient_vitamin_k
    MicronutrientKind.VitaminC -> Res.string.micronutrient_vitamin_c
    MicronutrientKind.VitaminB1 -> Res.string.micronutrient_vitamin_b1
    MicronutrientKind.VitaminB2 -> Res.string.micronutrient_vitamin_b2
    MicronutrientKind.VitaminB3 -> Res.string.micronutrient_vitamin_b3
    MicronutrientKind.VitaminB6 -> Res.string.micronutrient_vitamin_b6
    MicronutrientKind.VitaminB9 -> Res.string.micronutrient_vitamin_b9
    MicronutrientKind.VitaminB12 -> Res.string.micronutrient_vitamin_b12
    MicronutrientKind.Biotin -> Res.string.micronutrient_biotin
    MicronutrientKind.PantothenicAcid -> Res.string.micronutrient_pantothenic_acid
    MicronutrientKind.Calcium -> Res.string.micronutrient_calcium
    MicronutrientKind.Iron -> Res.string.micronutrient_iron
    MicronutrientKind.Magnesium -> Res.string.micronutrient_magnesium
    MicronutrientKind.Potassium -> Res.string.micronutrient_potassium
    MicronutrientKind.Phosphorus -> Res.string.micronutrient_phosphorus
    MicronutrientKind.Zinc -> Res.string.micronutrient_zinc
    MicronutrientKind.Iodine -> Res.string.micronutrient_iodine
    MicronutrientKind.Selenium -> Res.string.micronutrient_selenium
    MicronutrientKind.Copper -> Res.string.micronutrient_copper
    MicronutrientKind.Manganese -> Res.string.micronutrient_manganese
    MicronutrientKind.Chromium -> Res.string.micronutrient_chromium
    MicronutrientKind.Molybdenum -> Res.string.micronutrient_molybdenum
    MicronutrientKind.Fluoride -> Res.string.micronutrient_fluoride
    MicronutrientKind.Chloride -> Res.string.micronutrient_chloride
}

internal fun micronutrientUnitLabel(unit: MicronutrientUnit): StringResource = when (unit) {
    MicronutrientUnit.G -> Res.string.micronutrient_unit_g
    MicronutrientUnit.MG -> Res.string.micronutrient_unit_mg
    MicronutrientUnit.UG -> Res.string.micronutrient_unit_ug
}

/** The test tag suffix of [kind]: its OFF key in snake case ("saturated_fat"). */
internal fun micronutrientTag(kind: MicronutrientKind): String = kind.offKey.replace('-', '_')

/** [value] with at most two decimals and a comma ("2,5", "0,25", "12", "1.200"), half up. */
internal fun formatAmount(value: Double): String {
    val hundredths = floor(value * HUNDREDTHS + HALF).toLong()
    val whole = AppNumberFormat.integer((hundredths / HUNDREDTHS).toInt())
    val decimals = (hundredths % HUNDREDTHS).toInt()
    return when {
        decimals == 0 -> whole
        decimals % TWO_DIGITS == 0 -> "$whole,${decimals / TWO_DIGITS}"
        else -> "$whole,${decimals.toString().padStart(2, '0')}"
    }
}
