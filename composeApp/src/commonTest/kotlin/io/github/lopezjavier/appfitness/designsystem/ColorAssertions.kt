package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.assertTrue

// sRGB Color stores 8-bit channels, so a 0.08 alpha becomes 20/255 (0.0784).
private const val CHANNEL_TOLERANCE = 1f / 255f + 0.0001f
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF
private const val CHANNEL_MAX = 255f

/** Asserts [actual] equals the 24-bit [rgb] value with the given [alpha], within one 8-bit step. */
internal fun assertColor(rgb: Int, actual: Color, alpha: Float = 1f, name: String = "") {
    val expected =
        listOf(
            ((rgb shr RED_SHIFT) and CHANNEL_MASK) / CHANNEL_MAX,
            ((rgb shr GREEN_SHIFT) and CHANNEL_MASK) / CHANNEL_MAX,
            (rgb and CHANNEL_MASK) / CHANNEL_MAX,
            alpha,
        )
    val actualChannels = listOf(actual.red, actual.green, actual.blue, actual.alpha)
    val matches = expected.zip(actualChannels).all { (e, a) -> abs(e - a) <= CHANNEL_TOLERANCE }
    assertTrue(matches, "$name: expected rgb=${rgb.toString(radix = 16)} alpha=$alpha but was $actual")
}
