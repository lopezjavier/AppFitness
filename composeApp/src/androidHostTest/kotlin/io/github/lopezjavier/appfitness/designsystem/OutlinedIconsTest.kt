package io.github.lopezjavier.appfitness.designsystem

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** V-3: static glyphs are Outlined; filled is reserved for state cues (active tab, favourite). */
class OutlinedIconsTest {
    private val drawableDir = File("src/commonMain/composeResources/drawable")

    @Test
    fun legacyFilledIcons_areNowOutlined() {
        LEGACY_ICONS.forEach { name ->
            val file = File(drawableDir, "$name.xml")
            assertTrue(file.exists(), "$name.xml is missing")
            val text = file.readText(Charsets.UTF_8)
            assertTrue("Material Symbols Outlined" in text, "$name is not an Outlined glyph")
            assertTrue("Material Icons (filled" !in text, "$name still declares the filled set")
        }
    }

    private companion object {
        val LEGACY_ICONS = listOf(
            "ic_info",
            "ic_photo_camera",
            "ic_photo_library",
            "ic_label",
            "ic_auto_awesome",
            "ic_nav_trainer",
            "ic_health_connect",
        )
    }
}
