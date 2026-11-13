package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Fixed sizes that feature screens share with the design system, so no `dp` literal lives outside it. */
object FeatureMetrics {
    /** Widest the Diario date navigator (previous, label, next) grows (C-40). */
    val DateNavigatorMaxWidth: Dp = 320.dp

    /** Width of the input of a nutrient table row of the create-food form (C-36). */
    val NutrientInputWidth: Dp = 96.dp

    /** Height of the input of a nutrient table row; the 48 dp touch target. */
    val NutrientInputHeight: Dp = Spacing.minTouchTarget

    /** Minimum height of a nutrient table row. */
    val NutrientRowMinHeight: Dp = 56.dp

    /** Size of the warning icon of the energy notice. */
    val NoticeIconSize: Dp = 20.dp

    /** Width of a placeholder chip of the food sheet skeleton. */
    val SkeletonChipWidth: Dp = 88.dp
}

/** This style in semi-bold (W600), for compact figures such as the kcal pill. */
fun TextStyle.semiBold(): TextStyle = copy(fontWeight = FontWeight.W600)

/** This style in bold (W700), for short macro labels. */
fun TextStyle.bold(): TextStyle = copy(fontWeight = FontWeight.W700)
