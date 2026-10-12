package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ColorSchemeTest {
    private val colors = KineticDarkColors
    private val scheme = kineticDarkColorScheme()

    @Test
    fun darkScheme_mapsSection2_4() {
        assertEquals(colors.neonLime, scheme.primary)
        assertEquals(colors.onNeonLime, scheme.onPrimary)
        assertEquals(colors.athleticCyan, scheme.secondary)
        assertEquals(colors.energyAmber, scheme.tertiary)
        assertEquals(colors.surfaceBase, scheme.background)
        assertEquals(colors.surfaceBase, scheme.surface)
        assertEquals(colors.surfaceRaised, scheme.surfaceContainerLow)
        assertEquals(colors.surfaceOverlay, scheme.surfaceContainer)
        assertEquals(colors.surfaceOverlay, scheme.surfaceContainerHigh)
        assertEquals(colors.textPrimary, scheme.onSurface)
        assertEquals(colors.textSecondary, scheme.onSurfaceVariant)
        assertEquals(colors.borderStrong, scheme.outline)
        assertEquals(colors.borderSubtle, scheme.outlineVariant)
        assertColor(0xFFB4AB, scheme.error, name = "error")
        assertColor(0x93000A, scheme.errorContainer, name = "errorContainer")
    }

    @Test
    fun darkScheme_derivedRoles_useKineticTokens() {
        assertEquals(colors.onNeonLime, scheme.onSecondary)
        assertEquals(colors.onNeonLime, scheme.onTertiary)
        assertEquals(colors.textPrimary, scheme.onBackground)
        assertEquals(colors.neonLime, scheme.primaryContainer)
        assertEquals(colors.onNeonLime, scheme.onPrimaryContainer)
        assertEquals(colors.surfaceOverlay, scheme.secondaryContainer)
        assertEquals(colors.textPrimary, scheme.onSecondaryContainer)
        assertEquals(colors.surfaceOverlay, scheme.tertiaryContainer)
        assertEquals(colors.textPrimary, scheme.onTertiaryContainer)
        assertEquals(colors.surfaceOverlay, scheme.surfaceVariant)
        assertEquals(colors.surfaceBase, scheme.surfaceContainerLowest)
        assertEquals(colors.surfaceBase, scheme.surfaceDim)
        assertEquals(colors.surfaceOverlay, scheme.surfaceContainerHighest)
        assertEquals(colors.surfaceOverlay, scheme.surfaceBright)
        assertEquals(Color.Transparent, scheme.surfaceTint)
        assertEquals(colors.scrim, scheme.scrim)
        assertEquals(colors.surfaceOverlay, scheme.inverseSurface)
        assertEquals(colors.textPrimary, scheme.inverseOnSurface)
        assertEquals(colors.neonLime, scheme.inversePrimary)
        assertEquals(colors.onError, scheme.onError)
        assertEquals(colors.onErrorContainer, scheme.onErrorContainer)
        assertEquals(colors.neonLime, scheme.primaryFixed)
        assertEquals(colors.neonLime, scheme.primaryFixedDim)
        assertEquals(colors.athleticCyan, scheme.secondaryFixed)
        assertEquals(colors.athleticCyan, scheme.secondaryFixedDim)
        assertEquals(colors.energyAmber, scheme.tertiaryFixed)
        assertEquals(colors.energyAmber, scheme.tertiaryFixedDim)
        listOf(
            scheme.onPrimaryFixed,
            scheme.onPrimaryFixedVariant,
            scheme.onSecondaryFixed,
            scheme.onSecondaryFixedVariant,
            scheme.onTertiaryFixed,
            scheme.onTertiaryFixedVariant,
        ).forEach { assertEquals(colors.onNeonLime, it) }
    }

    @Test
    fun darkScheme_noBaselineRoleLeaks() {
        // The error family is pinned to the 05 values (see the other tests), whatever the library
        // baseline is: androidx material3 1.4.0 uses #F2B8B5 for error, not #FFB4AB.
        val pinnedToSpec = setOf("error", "onError", "errorContainer", "onErrorContainer")
        val baseline = darkColorScheme().roles()
        scheme.roles()
            .filterKeys { it !in pinnedToSpec }
            .forEach { (role, value) ->
                assertNotEquals(baseline.getValue(role), value, "$role leaks the M3 baseline value")
            }
    }

    @Test
    fun darkScheme_errorFamily_pinnedToSpec() {
        assertColor(0xFFB4AB, scheme.error, name = "error")
        assertColor(0x690005, scheme.onError, name = "onError")
        assertColor(0x93000A, scheme.errorContainer, name = "errorContainer")
        assertColor(0xFFDAD6, scheme.onErrorContainer, name = "onErrorContainer")
    }

    private fun ColorScheme.roles(): Map<String, Color> = mapOf(
        "primary" to primary,
        "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer,
        "inversePrimary" to inversePrimary,
        "secondary" to secondary,
        "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary,
        "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer,
        "background" to background,
        "onBackground" to onBackground,
        "surface" to surface,
        "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant,
        "onSurfaceVariant" to onSurfaceVariant,
        "surfaceTint" to surfaceTint,
        "inverseSurface" to inverseSurface,
        "inverseOnSurface" to inverseOnSurface,
        "error" to error,
        "onError" to onError,
        "errorContainer" to errorContainer,
        "onErrorContainer" to onErrorContainer,
        "outline" to outline,
        "outlineVariant" to outlineVariant,
        "scrim" to scrim,
        "surfaceBright" to surfaceBright,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceDim" to surfaceDim,
        "primaryFixed" to primaryFixed,
        "primaryFixedDim" to primaryFixedDim,
        "onPrimaryFixed" to onPrimaryFixed,
        "onPrimaryFixedVariant" to onPrimaryFixedVariant,
        "secondaryFixed" to secondaryFixed,
        "secondaryFixedDim" to secondaryFixedDim,
        "onSecondaryFixed" to onSecondaryFixed,
        "onSecondaryFixedVariant" to onSecondaryFixedVariant,
        "tertiaryFixed" to tertiaryFixed,
        "tertiaryFixedDim" to tertiaryFixedDim,
        "onTertiaryFixed" to onTertiaryFixed,
        "onTertiaryFixedVariant" to onTertiaryFixedVariant,
    )
}
