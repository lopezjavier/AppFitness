package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
        assertColor(0xA9B3C4, colors.textSecondary, name = "textSecondary")
        assertColor(0x8C97A9, colors.textTertiary, name = "textTertiary")
        assertColor(0xFFFFFF, colors.ghostData, alpha = 0.36f, name = "ghostData")
        assertColor(0x000000, colors.scrim, alpha = 0.65f, name = "scrim")
    }

    @Test
    fun round0Tokens_matchSection2_1a() {
        assertColor(0x242E3B, colors.surfaceControl, name = "surfaceControl")
        assertColor(0xFFFFFF, colors.borderControl, alpha = 0.36f, name = "borderControl")
        assertColor(0x354925, colors.selectedContainer, name = "selectedContainer")
        assertColor(0xFFFFFF, colors.trackInactive, alpha = 0.16f, name = "trackInactive")
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
        assertColor(0xF472B6, colors.macroFat, name = "macroFat")
        assertEquals(colors.neonLime, colors.macroProtein)
        assertEquals(colors.athleticCyan, colors.macroCarbs)
        assertNotEquals(colors.energyAmber, colors.macroFat)
    }

    @Test
    fun shadowTokens_matchSpec() {
        assertColor(0x000000, colors.shadowSheet, alpha = 0.70f, name = "shadowSheet")
    }
}
