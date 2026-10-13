package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StateMessagesTest {
    private val c = KineticDarkColors
    private val t = appTypography(FontFamily.Default)

    @Test
    fun emptyColors_iconTextSecondary_titlePrimary_messageSecondary() {
        val colors = EmptyStateDefaults.colors(c)
        assertEquals(c.textSecondary, colors.icon)
        assertEquals(c.textPrimary, colors.title)
        assertEquals(c.textSecondary, colors.message)
    }

    @Test
    fun errorColors_iconError() {
        val colors = ErrorStateDefaults.colors(c)
        assertEquals(c.error, colors.icon)
        assertEquals(c.textPrimary, colors.title)
        assertEquals(c.textSecondary, colors.message)
    }

    @Test
    fun iconSize48_onGrid() {
        assertEquals(48.dp, EmptyStateDefaults.IconSize)
        assertEquals(EmptyStateDefaults.IconSize, ErrorStateDefaults.IconSize)
        assertEquals(0f, EmptyStateDefaults.IconSize.value % 4f)
    }

    @Test
    fun titleStyle_isTitleMedium() = assertEquals(t.titleMedium, EmptyStateDefaults.titleStyle(t))

    @Test
    fun messageStyle_isBodyMedium_centered() {
        val style = EmptyStateDefaults.messageStyle(t)
        assertEquals(t.bodyMedium.fontSize, style.fontSize)
        assertEquals(TextAlign.Center, style.textAlign)
    }

    @Test
    fun spacings_onGrid() {
        listOf(
            EmptyStateDefaults.ContentPadding,
            EmptyStateDefaults.ItemSpacing,
            EmptyStateDefaults.ActionTopSpacing,
            ErrorStateDefaults.ContentPadding,
            ErrorStateDefaults.ItemSpacing,
            ErrorStateDefaults.ActionTopSpacing,
        ).forEach { assertEquals(0f, it.value % 4f, "$it is off the 4 dp sub-grid") }
        // icon -> title -> message -> button rhythm is 12 / 12 / 24 dp (the last gap equals blockGap).
        assertEquals(Spacing.blockGap, EmptyStateDefaults.ItemSpacing + EmptyStateDefaults.ActionTopSpacing)
    }

    @Test
    fun maxContentWidth_keepsBlockCenteredOnTablets() {
        assertEquals(360.dp, EmptyStateDefaults.MaxContentWidth)
        assertTrue(EmptyStateDefaults.MaxContentWidth < 600.dp)
    }
}
