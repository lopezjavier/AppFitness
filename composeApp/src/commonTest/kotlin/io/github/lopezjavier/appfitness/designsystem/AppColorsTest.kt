package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class AppColorsTest {
    private val colors = KineticDarkColors

    @Test
    fun surfaceAndTextTokens_matchSpec() {
        assertColor(0x0B0F14, colors.surfaceBase, name = "surfaceBase")
        assertColor(0x121820, colors.surfaceRaised, name = "surfaceRaised")
        assertColor(0x1A222D, colors.surfaceOverlay, name = "surfaceOverlay")
        assertColor(0xFFFFFF, colors.borderSubtle, alpha = 0.08f, name = "borderSubtle")
        assertColor(0xFFFFFF, colors.borderStrong, alpha = 0.12f, name = "borderStrong")
        assertColor(0xFFFFFF, colors.divider, alpha = 0.04f, name = "divider")
        assertColor(0xE0E2EA, colors.textPrimary, name = "textPrimary")
        assertColor(0xC2CAB0, colors.textSecondary, name = "textSecondary")
        assertColor(0xFFFFFF, colors.ghostData, alpha = 0.36f, name = "ghostData")
        assertColor(0x000000, colors.scrim, alpha = 0.65f, name = "scrim")
    }

    @Test
    fun accentTokens_matchSpec() {
        assertColor(0xA3E635, colors.neonLime, name = "neonLime")
        assertColor(0x0B0F14, colors.onNeonLime, name = "onNeonLime")
        assertColor(0x38BDF8, colors.athleticCyan, name = "athleticCyan")
        assertColor(0xF97316, colors.energyAmber, name = "energyAmber")
        assertColor(0x22C55E, colors.electricGreen, name = "electricGreen")
        assertColor(0xFFB4AB, colors.error, name = "error")
        assertColor(0x93000A, colors.errorContainer, name = "errorContainer")
        assertColor(0x690005, colors.onError, name = "onError")
        assertColor(0xFFDAD6, colors.onErrorContainer, name = "onErrorContainer")
    }

    @Test
    fun macroTokens_matchSpec_andReuseAccents() {
        assertColor(0xA3E635, colors.macroProtein, name = "macroProtein")
        assertColor(0x38BDF8, colors.macroCarbs, name = "macroCarbs")
        assertColor(0xF97316, colors.macroFat, name = "macroFat")
        assertEquals(colors.neonLime, colors.macroProtein)
        assertEquals(colors.athleticCyan, colors.macroCarbs)
        assertEquals(colors.energyAmber, colors.macroFat)
    }

    @Test
    fun shadowTokens_matchSpec() {
        assertColor(0x000000, colors.shadowSheet, alpha = 0.70f, name = "shadowSheet")
        assertColor(0xA3E635, colors.glowLime, alpha = 0.15f, name = "glowLime")
    }
}
