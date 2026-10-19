package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppTextFieldTest {
    private val c = KineticDarkColors

    @Test
    fun limit_keepsTextWithinMaxLength() = assertEquals("abc", AppTextFieldDefaults.limit("abc", 3))

    @Test
    fun limit_cutsTextBeyondMaxLength() = assertEquals("abc", AppTextFieldDefaults.limit("abcdef", 3))

    @Test
    fun limit_keepsEmptyText() = assertEquals("", AppTextFieldDefaults.limit("", 3))

    @Test
    fun minHeight_isAtLeastTheTouchTarget() = assertTrue(AppTextFieldDefaults.MinHeight >= 48.dp)

    @Test
    fun borderColor_isCyanWhenFocused() =
        assertEquals(c.athleticCyan, AppTextFieldDefaults.borderColor(focused = true, hasError = false, appColors = c))

    @Test
    fun borderColor_isStrongWhenIdle() =
        assertEquals(c.borderStrong, AppTextFieldDefaults.borderColor(focused = false, hasError = false, appColors = c))

    @Test
    fun borderColor_isErrorWhenInvalidEvenIfFocused() {
        assertEquals(c.error, AppTextFieldDefaults.borderColor(focused = true, hasError = true, appColors = c))
        assertEquals(c.error, AppTextFieldDefaults.borderColor(focused = false, hasError = true, appColors = c))
    }

    @Test
    fun borderWidth_isThickerWhenFocusedOrInvalid() {
        assertEquals(AppBorder.focus, AppTextFieldDefaults.borderWidth(focused = true, hasError = false))
        assertEquals(AppBorder.focus, AppTextFieldDefaults.borderWidth(focused = false, hasError = true))
        assertEquals(AppBorder.thin, AppTextFieldDefaults.borderWidth(focused = false, hasError = false))
    }
}
