package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetRowTest {
    private val c = KineticDarkColors

    @Test
    fun previousText_null_isDash() = assertEquals("—", SetRowDefaults.previousText(null))

    @Test
    fun previousText_blank_isDash() = assertEquals("—", SetRowDefaults.previousText("   "))

    @Test
    fun previousText_value_unchanged() = assertEquals("85 kg × 8", SetRowDefaults.previousText("85 kg × 8"))

    @Test
    fun childTag_withPrefix() =
        assertEquals("catalog_set_row_2_weight", SetRowDefaults.childTag("catalog_set_row_2", "weight"))

    @Test
    fun childTag_nullPrefix_isNull() = assertNull(SetRowDefaults.childTag(null, "weight"))

    @Test
    fun weightLimits_match04And03() {
        assertEquals(2, SetRowDefaults.WEIGHT_MAX_DECIMALS)
        assertEquals(4, SetRowDefaults.WEIGHT_MAX_INTEGER_DIGITS)
    }

    @Test
    fun repsLimits() {
        assertEquals(0, SetRowDefaults.REPS_MAX_DECIMALS)
        assertEquals(3, SetRowDefaults.REPS_MAX_INTEGER_DIGITS)
    }

    @Test
    fun weightLimits_acceptQuarterKg() {
        val result =
            NumberInputDefaults.sanitize(
                "102,25",
                SetRowDefaults.WEIGHT_MAX_DECIMALS,
                SetRowDefaults.WEIGHT_MAX_INTEGER_DIGITS,
            )
        assertEquals("102,25", result)
    }

    @Test
    fun repsLimits_rejectDecimal() {
        val result =
            NumberInputDefaults.sanitize(
                "8,5",
                SetRowDefaults.REPS_MAX_DECIMALS,
                SetRowDefaults.REPS_MAX_INTEGER_DIGITS,
            )
        assertNull(result)
    }

    @Test
    fun columnWeights_sumTo8_kgWiderThanPrevious() {
        val sum = SetRowDefaults.PREVIOUS_WEIGHT + SetRowDefaults.KG_WEIGHT + SetRowDefaults.REPS_WEIGHT
        assertEquals(8f, sum)
        assertTrue(SetRowDefaults.KG_WEIGHT > SetRowDefaults.PREVIOUS_WEIGHT)
        assertEquals(2.5f, SetRowDefaults.PREVIOUS_WEIGHT)
        assertEquals(3.5f, SetRowDefaults.KG_WEIGHT)
        assertEquals(2f, SetRowDefaults.REPS_WEIGHT)
    }

    @Test
    fun gaps_areCompactForNarrowColumns() {
        assertEquals(Spacing.xs, SetRowDefaults.ColumnGap)
        assertEquals(Spacing.sm, SetRowDefaults.RowGap)
    }

    @Test
    fun previousMaxLines_is2() = assertEquals(2, SetRowDefaults.PREVIOUS_MAX_LINES)

    @Test
    fun indexBadge24_onGrid() = assertEquals(24.dp, SetRowDefaults.IndexBadgeSize)

    @Test
    fun rowMinHeight_atLeast48() = assertTrue(SetRowDefaults.MinHeight >= 48.dp)

    @Test
    fun indexColors_overlayAndTextSecondary() {
        val (container, content) = SetRowDefaults.indexColors(c)
        assertEquals(c.surfaceOverlay, container)
        assertEquals(c.textSecondary, content)
    }

    @Test
    fun isPreviousClickable_onlyWhenPendingWithHandlerAndPrevious() {
        assertTrue(SetRowDefaults.isPreviousClickable(hasHandler = true, previous = "85 kg × 8", completed = false))
        assertFalse(SetRowDefaults.isPreviousClickable(hasHandler = false, previous = "85 kg × 8", completed = false))
        assertFalse(SetRowDefaults.isPreviousClickable(hasHandler = true, previous = null, completed = false))
        assertFalse(SetRowDefaults.isPreviousClickable(hasHandler = true, previous = " ", completed = false))
        assertFalse(SetRowDefaults.isPreviousClickable(hasHandler = true, previous = "85 kg × 8", completed = true))
    }
}
