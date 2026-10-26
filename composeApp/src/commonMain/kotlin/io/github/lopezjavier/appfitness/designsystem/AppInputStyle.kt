package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/**
 * The one label style and focus treatment of every input (R-24): [AppTextField] and [NumberInput] both
 * draw a sentence-case `bodyMedium` label in `textSecondary` above the field and a cyan focus border.
 */
internal object AppInputStyle {
    fun labelStyle(typography: AppTypography): TextStyle = typography.bodyMedium

    fun labelColor(appColors: AppColors): Color = appColors.textSecondary

    /** An invalid field shows the error colour even while focused; the text below also says it (not colour-only). */
    fun borderColor(focused: Boolean, hasError: Boolean, appColors: AppColors): Color = when {
        hasError -> appColors.error
        focused -> appColors.athleticCyan
        else -> appColors.borderStrong
    }

    fun borderWidth(focused: Boolean, hasError: Boolean): Dp = if (focused || hasError) {
        AppBorder.focus
    } else {
        AppBorder.thin
    }
}
