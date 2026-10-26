package io.github.lopezjavier.appfitness.platform

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoCompressorTest {
    @Test
    fun targetSize_landscape4000x3000_longSideIs1600() {
        assertEquals(1600 to 1200, PhotoCompressor.targetSize(4000, 3000))
    }

    @Test
    fun targetSize_portrait3000x4000_longSideIs1600() {
        assertEquals(1200 to 1600, PhotoCompressor.targetSize(3000, 4000))
    }

    @Test
    fun targetSize_smallImage_isLeftUnscaled() {
        assertEquals(1000 to 800, PhotoCompressor.targetSize(1000, 800))
    }

    @Test
    fun sampleSize_isAPowerOfTwoThatKeepsTheDecodedLongSideAtLeastTheTarget() {
        assertEquals(2, PhotoCompressor.sampleSize(4000, 3000))
        assertEquals(1, PhotoCompressor.sampleSize(1000, 800))
        assertEquals(4, PhotoCompressor.sampleSize(8000, 6000))
    }

    @Test
    fun rotation_mapsExifOrientationToDegrees() {
        assertEquals(90, PhotoCompressor.rotationDegrees(6))
        assertEquals(180, PhotoCompressor.rotationDegrees(3))
        assertEquals(270, PhotoCompressor.rotationDegrees(8))
        assertEquals(0, PhotoCompressor.rotationDegrees(1))
        assertEquals(0, PhotoCompressor.rotationDegrees(0))
    }

    @Test
    fun encodeWithinLimit_stepsQualityDownUntilItFits() {
        val tried = mutableListOf<Int>()
        val result = PhotoCompressor.encodeWithinLimit { quality ->
            tried += quality
            ByteArray(quality * 20_000)
        }

        assertTrue(result.size <= PhotoCompressor.MAX_BYTES)
        assertEquals(listOf(90, 80, 70), tried)
    }

    @Test
    fun encodeWithinLimit_neverFits_returnsTheLowestQualityOutput() {
        val tried = mutableListOf<Int>()
        val result = PhotoCompressor.encodeWithinLimit { quality ->
            tried += quality
            ByteArray(PhotoCompressor.MAX_BYTES + quality)
        }

        assertEquals(PhotoCompressor.MIN_QUALITY, tried.last())
        assertEquals(PhotoCompressor.MAX_BYTES + PhotoCompressor.MIN_QUALITY, result.size)
    }
}
