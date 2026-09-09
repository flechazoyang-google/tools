package com.flechazo.toolbox.core.algorithm

import com.flechazo.toolbox.core.util.calculateInSampleSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSizingTest {

    @Test
    fun typicalPhonePhotoIsDownsampled() {
        // 旧实现在这个尺寸下返回 1（全分辨率 48.8 MB）
        assertEquals(2, calculateInSampleSize(4032, 3024, 2048))
        assertEquals(4, calculateInSampleSize(8064, 6048, 2048))
        assertEquals(8, calculateInSampleSize(16128, 12096, 2048))
    }

    @Test
    fun previewSizeForTools() {
        assertEquals(4, calculateInSampleSize(4032, 3024, 1600))
        assertEquals(4, calculateInSampleSize(4032, 3024, 1024))
    }

    @Test
    fun smallImagesAreNotUpscaled() {
        assertEquals(1, calculateInSampleSize(800, 600, 1600))
        assertEquals(1, calculateInSampleSize(1600, 1200, 1600))
    }

    @Test
    fun degenerateInputsReturnOne() {
        assertEquals(1, calculateInSampleSize(0, 100, 2048))
        assertEquals(1, calculateInSampleSize(100, 0, 2048))
        assertEquals(1, calculateInSampleSize(100, 100, 0))
    }

    @Test
    fun resultIsAlwaysPowerOfTwoAndWithinBound() {
        val width = 4321
        val height = 3210
        listOf(512, 1024, 1600, 2048, 4096).forEach { maxSide ->
            val sample = calculateInSampleSize(width, height, maxSide)
            assertEquals("must be a power of two", 0, sample and (sample - 1))
            assert(maxOf(width, height) / sample <= maxSide) {
                "sample=$sample still exceeds maxSide=$maxSide"
            }
        }
    }
}
