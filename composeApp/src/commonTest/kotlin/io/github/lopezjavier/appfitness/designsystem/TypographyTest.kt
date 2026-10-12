package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class TypographyTest {
    private data class Spec(
        val size: TextUnit,
        val lineHeight: TextUnit,
        val weight: FontWeight,
        val tracking: TextUnit,
    )

    // planning/05-design-system.md §3, phone sizes.
    private val phoneSpec =
        mapOf(
            "displayHero" to Spec(36.sp, 40.sp, FontWeight.W800, (-0.02).em),
            "headlineMetric" to Spec(26.sp, 30.sp, FontWeight.W700, (-0.01).em),
            "titleLarge" to Spec(20.sp, 26.sp, FontWeight.W600, (-0.01).em),
            "titleMedium" to Spec(16.sp, 22.sp, FontWeight.W600, 0.em),
            "bodyLarge" to Spec(16.sp, 24.sp, FontWeight.W400, 0.em),
            "bodyMedium" to Spec(14.sp, 20.sp, FontWeight.W400, 0.em),
            "bodySmall" to Spec(12.sp, 16.sp, FontWeight.W400, 0.em),
            "labelNumeric" to Spec(18.sp, 22.sp, FontWeight.W700, 0.02.em),
            "labelTechnical" to Spec(11.sp, 14.sp, FontWeight.W600, 0.06.em),
        )

    @Test
    fun phoneStyles_matchSection3() {
        val styles = appTypography(FontFamily.Default, TypeScale.Phone).byName()
        assertEquals(phoneSpec.keys, styles.keys)
        phoneSpec.forEach { (name, spec) -> assertSpec(name, spec, styles.getValue(name)) }
    }

    @Test
    fun tabletScale_changesOnlyHeroAndMetric() {
        val phone = appTypography(FontFamily.Default, TypeScale.Phone).byName()
        val tablet = appTypography(FontFamily.Default, TypeScale.Tablet).byName()
        assertSpec("displayHero", Spec(48.sp, 52.sp, FontWeight.W800, (-0.02).em), tablet.getValue("displayHero"))
        assertSpec("headlineMetric", Spec(32.sp, 36.sp, FontWeight.W700, (-0.01).em), tablet.getValue("headlineMetric"))
        (phone.keys - setOf("displayHero", "headlineMetric")).forEach { name ->
            assertEquals(phone.getValue(name), tablet.getValue(name), name)
        }
    }

    @Test
    fun allStyles_useTabularLiningFigures() {
        assertEquals("tnum, lnum", NUMERIC_FONT_FEATURES)
        TypeScale.entries.forEach { scale ->
            val typography = appTypography(FontFamily.Default, scale)
            typography.byName().forEach { (name, style) ->
                assertEquals(NUMERIC_FONT_FEATURES, style.fontFeatureSettings, "$scale $name")
            }
            typography.toMaterialTypography(FontFamily.Default).slots().forEach { (name, style) ->
                assertEquals(NUMERIC_FONT_FEATURES, style.fontFeatureSettings, "$scale M3 $name")
            }
        }
    }

    @Test
    fun allStyles_usePassedFontFamily() {
        TypeScale.entries.forEach { scale ->
            appTypography(FontFamily.Monospace, scale).byName().forEach { (name, style) ->
                assertEquals(FontFamily.Monospace, style.fontFamily, "$scale $name")
            }
        }
    }

    @Test
    fun materialTypography_allSlotsUseFontFamily_andSharedSlotsMatch() {
        val app = appTypography(FontFamily.Monospace)
        val material = app.toMaterialTypography(FontFamily.Monospace)
        val slots = material.slots()
        assertEquals(15, slots.size)
        slots.forEach { (name, style) -> assertEquals(FontFamily.Monospace, style.fontFamily, name) }
        assertEquals(app.titleLarge, material.titleLarge)
        assertEquals(app.titleMedium, material.titleMedium)
        assertEquals(app.bodyLarge, material.bodyLarge)
        assertEquals(app.bodyMedium, material.bodyMedium)
        assertEquals(app.bodySmall, material.bodySmall)
        // Slots without a Kinetic style keep the M3 metrics.
        val defaults = Typography()
        assertEquals(defaults.displayLarge.fontSize, material.displayLarge.fontSize)
        assertEquals(defaults.labelSmall.lineHeight, material.labelSmall.lineHeight)
    }

    private fun assertSpec(name: String, spec: Spec, style: TextStyle) {
        assertEquals(spec.size, style.fontSize, "$name fontSize")
        assertEquals(spec.lineHeight, style.lineHeight, "$name lineHeight")
        assertEquals(spec.weight, style.fontWeight, "$name fontWeight")
        assertEquals(spec.tracking, style.letterSpacing, "$name letterSpacing")
    }

    private fun AppTypography.byName(): Map<String, TextStyle> = mapOf(
        "displayHero" to displayHero,
        "headlineMetric" to headlineMetric,
        "titleLarge" to titleLarge,
        "titleMedium" to titleMedium,
        "bodyLarge" to bodyLarge,
        "bodyMedium" to bodyMedium,
        "bodySmall" to bodySmall,
        "labelNumeric" to labelNumeric,
        "labelTechnical" to labelTechnical,
    )

    private fun Typography.slots(): Map<String, TextStyle> = mapOf(
        "displayLarge" to displayLarge,
        "displayMedium" to displayMedium,
        "displaySmall" to displaySmall,
        "headlineLarge" to headlineLarge,
        "headlineMedium" to headlineMedium,
        "headlineSmall" to headlineSmall,
        "titleLarge" to titleLarge,
        "titleMedium" to titleMedium,
        "titleSmall" to titleSmall,
        "bodyLarge" to bodyLarge,
        "bodyMedium" to bodyMedium,
        "bodySmall" to bodySmall,
        "labelLarge" to labelLarge,
        "labelMedium" to labelMedium,
        "labelSmall" to labelSmall,
    )
}
