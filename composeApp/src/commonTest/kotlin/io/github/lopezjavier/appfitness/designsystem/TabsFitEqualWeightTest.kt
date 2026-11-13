package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabsFitEqualWeightTest {
    @Test
    fun wordsWellInsideTheShare_fit() = assertTrue(tabsFitEqualWeight(listOf(40, 50, 60), share = 120, padding = 24))

    @Test
    fun wordExactlyFillingTheShare_stillFits() = assertTrue(tabsFitEqualWeight(listOf(96), share = 120, padding = 24))

    @Test
    fun wordOnePixelOverTheShare_doesNotFit() = assertFalse(tabsFitEqualWeight(listOf(97), share = 120, padding = 24))

    @Test
    fun oneWideTab_makesTheRowScroll() = assertFalse(tabsFitEqualWeight(listOf(40, 120, 40), share = 120, padding = 24))
}
