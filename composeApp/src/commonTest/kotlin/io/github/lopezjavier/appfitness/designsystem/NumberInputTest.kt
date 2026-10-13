package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NumberInputTest {
    private val c = KineticDarkColors
    private val typography = appTypography(FontFamily.Default)

    private fun sanitize(input: String, decimals: Int, integers: Int = 4) =
        NumberInputDefaults.sanitize(input, decimals, integers)

    private fun colors(focused: Boolean, readOnly: Boolean = false) =
        NumberInputDefaults.colors(focused = focused, readOnly = readOnly, appColors = c)

    @Test
    fun sanitize_empty_isValid() = assertEquals("", sanitize("", 2))

    @Test
    fun sanitize_integer_kept() {
        assertEquals("8", sanitize("8", 0))
        assertEquals("87", sanitize("87", 0))
    }

    @Test
    fun sanitize_dot_normalizedToComma() = assertEquals("87,5", sanitize("87.5", 2))

    @Test
    fun sanitize_comma_kept() = assertEquals("87,5", sanitize("87,5", 1))

    @Test
    fun sanitize_maxDecimals_boundary() {
        assertEquals("87,25", sanitize("87,25", 2))
        assertNull(sanitize("87,255", 2))
        assertEquals("87,5", sanitize("87,5", 1))
        assertNull(sanitize("87,55", 1))
    }

    @Test
    fun sanitize_zeroDecimals_rejectsSeparator() {
        assertNull(sanitize("87,5", 0))
        assertNull(sanitize("87.", 0))
    }

    @Test
    fun sanitize_trailingSeparator_allowedWhileTyping() = assertEquals("87,", sanitize("87,", 1))

    @Test
    fun sanitize_leadingSeparator_prefixedWithZero() {
        assertEquals("0,5", sanitize(",5", 1))
        assertEquals("0,", sanitize(".", 2))
    }

    @Test
    fun sanitize_twoSeparators_rejected() {
        assertNull(sanitize("1,2,3", 2))
        assertNull(sanitize("1.2,3", 2))
    }

    @Test
    fun sanitize_maxIntegerDigits_boundary() {
        assertEquals("1234,5", sanitize("1234,5", 1, 4))
        assertNull(sanitize("12345", 0, 4))
        assertEquals("999", sanitize("999", 0, 3))
        assertNull(sanitize("1000", 0, 3))
    }

    @Test
    fun sanitize_leadingZeros_collapsed() {
        assertEquals("7", sanitize("007", 0))
        assertEquals("0", sanitize("00", 0))
        assertEquals("0", sanitize("0", 0))
        assertEquals("0,5", sanitize("00,5", 1))
        assertEquals("0,5", sanitize("0,5", 1))
    }

    @Test
    fun sanitize_nonDigits_rejected() {
        listOf("-5", "5a", " 5", "1e3", "+5", "8a").forEach { assertNull(sanitize(it, 2), it) }
    }

    @Test
    fun sanitize_nonAsciiDigits_rejected() = assertNull(sanitize("٥", 0))

    @Test
    fun sanitize_negativeLimits_coercedToZero() {
        assertEquals("5", sanitize("5", -1))
        assertNull(sanitize("5,5", -1))
    }

    @Test
    fun parse_values() {
        assertEquals(87.5, NumberInputDefaults.parse("87,5"))
        assertEquals(87.25, NumberInputDefaults.parse("87,25"))
        assertEquals(87.0, NumberInputDefaults.parse("87"))
        assertEquals(87.0, NumberInputDefaults.parse("87,"))
        assertEquals(0.5, NumberInputDefaults.parse("0,5"))
        assertEquals(87.5, NumberInputDefaults.parse("87.5"))
    }

    @Test
    fun parse_invalid_isNull() {
        listOf("", ",", "abc", "1,2,3").forEach { assertNull(NumberInputDefaults.parse(it), it) }
    }

    private fun edit(text: String, cursor: Int, decimals: Int = 2) =
        NumberInputDefaults.applyEdit(TextFieldValue(text, TextRange(cursor)), decimals, 4)

    @Test
    fun applyEdit_separatorFirst_prefixedAndCursorAfterIt() {
        assertEquals(TextFieldValue("0,", TextRange(2)), edit(",", 1))
        assertEquals(TextFieldValue("0,", TextRange(2)), edit(".", 1))
    }

    @Test
    fun applyEdit_separatorThenDigit_givesZeroCommaDigit() {
        val afterSeparator = edit(",", 1)!!
        // The user types "5" at the cursor, which sits after "0,".
        val typed = TextFieldValue("0,5", TextRange(3))
        assertEquals(2, afterSeparator.selection.end)
        assertEquals(typed, NumberInputDefaults.applyEdit(typed, 2, 4))
    }

    @Test
    fun applyEdit_zeroThenDigit_collapsesLeadingZero() =
        assertEquals(TextFieldValue("7", TextRange(1)), edit("07", 2, decimals = 0))

    @Test
    fun applyEdit_midTextEdit_cursorStaysWhereTyped() {
        // "1,5" with "0" inserted at the start: "01,5" -> "1,5", cursor 1 -> 0 + 1 delta -1.
        assertEquals(TextFieldValue("1,5", TextRange(0)), edit("01,5", 1))
        // Unchanged text keeps the raw value and its cursor.
        assertEquals(TextFieldValue("12,5", TextRange(1)), edit("12,5", 1))
    }

    @Test
    fun applyEdit_invalid_isNull() = assertNull(edit("1a", 2))

    @Test
    fun keyboardType_byDecimals() {
        assertEquals(KeyboardType.Number, NumberInputDefaults.keyboardType(0))
        assertEquals(KeyboardType.Decimal, NumberInputDefaults.keyboardType(1))
        assertEquals(KeyboardType.Decimal, NumberInputDefaults.keyboardType(2))
    }

    @Test
    fun colors_unfocused_borderStrong_focused_neonLime() {
        assertEquals(c.borderStrong, colors(focused = false).border)
        assertEquals(c.neonLime, colors(focused = true).border)
    }

    @Test
    fun colors_readOnly_neverFocusBorder() =
        assertEquals(c.borderStrong, colors(focused = true, readOnly = true).border)

    @Test
    fun colors_placeholder_isGhostData_text_isTextPrimary() {
        val colors = colors(focused = false)
        assertEquals(c.ghostData, colors.placeholder)
        assertEquals(c.textPrimary, colors.text)
        assertEquals(c.surfaceOverlay, colors.container)
        assertEquals(c.textSecondary, colors.label)
        assertEquals(c.neonLime, colors.cursor)
    }

    @Test
    fun borderWidth_focused1_5_unfocused1_readOnly1() {
        assertEquals(1.5.dp, NumberInputDefaults.borderWidth(focused = true, readOnly = false))
        assertEquals(1.dp, NumberInputDefaults.borderWidth(focused = false, readOnly = false))
        assertEquals(1.dp, NumberInputDefaults.borderWidth(focused = true, readOnly = true))
    }

    @Test
    fun minHeight_atLeast48() = assertTrue(NumberInputDefaults.MinHeight >= 48.dp)

    @Test
    fun shape_isBase() = assertEquals(AppShapes.base, NumberInputDefaults.Shape)

    @Test
    fun textStyle_isLabelNumeric_centered_tabular() {
        val style = NumberInputDefaults.textStyle(typography)
        assertEquals(typography.labelNumeric.fontSize, style.fontSize)
        assertEquals(TextAlign.Center, style.textAlign)
        assertEquals(NUMERIC_FONT_FEATURES, style.fontFeatureSettings)
    }

    @Test
    fun labelStyle_isLabelTechnical() =
        assertEquals(typography.labelTechnical, NumberInputDefaults.labelStyle(typography))
}
