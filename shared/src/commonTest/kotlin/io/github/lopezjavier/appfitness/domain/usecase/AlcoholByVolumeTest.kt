package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class AlcoholByVolumeTest {
    @Test
    fun twelvePercentIsNinePointFiveGrams() = assertEquals(9.5, AlcoholByVolume.toGramsPer100(12.0))

    @Test
    fun fivePercentRoundsHalfUpToThreePointNine() = assertEquals(3.9, AlcoholByVolume.toGramsPer100(5.0))

    @Test
    fun zeroIsZero() = assertEquals(0.0, AlcoholByVolume.toGramsPer100(0.0))

    @Test
    fun hundredPercentIsSeventyEightPointNine() = assertEquals(78.9, AlcoholByVolume.toGramsPer100(100.0))

    @Test
    fun fractionalAbvIsConverted() = assertEquals(3.6, AlcoholByVolume.toGramsPer100(4.5))

    @Test
    fun halfBoundaryRoundsUp() = assertEquals(0.4, AlcoholByVolume.toGramsPer100(0.5)) // 0.3945 -> 0.4
}
