package com.example.messenger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-тести геометрії та лімітів фото. Firebase і Android тут не запускаються. */
class PhotoStoreTest {

    @Test fun landscapeIsScaledProportionally() {
        val size = fitInside(4000, 3000, 1280)
        assertEquals(1280, size.width)
        assertEquals(960, size.height)
    }

    @Test fun portraitIsScaledProportionally() {
        val size = fitInside(1200, 3600, 1280)
        assertEquals(427, size.width)
        assertEquals(1280, size.height)
    }

    @Test fun smallImageIsNotUpscaled() {
        val size = fitInside(640, 480, 1280)
        assertEquals(640, size.width)
        assertEquals(480, size.height)
    }

    @Test fun panoramaStaysWithinLimit() {
        val size = fitInside(10000, 500, 1280)
        assertEquals(1280, size.longestSide)
        assertTrue(size.height >= 1)
        assertEquals(64, size.height)
    }

    @Test fun invalidInputGivesEmptySize() {
        assertFalse(fitInside(0, 100, 1280).isValid)
        assertFalse(fitInside(100, 0, 1280).isValid)
        assertFalse(fitInside(100, 100, 0).isValid)
        assertFalse(fitInside(-5, -5, 1280).isValid)
    }

    @Test fun base64LengthMatchesEncoding() {
        assertEquals(0, base64Length(0))
        assertEquals(4, base64Length(1))
        assertEquals(4, base64Length(2))
        assertEquals(4, base64Length(3))
        assertEquals(8, base64Length(4))
        assertEquals(400_000, base64Length(300_000))
    }

    @Test fun databaseLimitIsRespected() {
        assertTrue(photoFitsDatabase(PHOTO_TARGET_JPEG_BYTES))
        assertTrue(photoFitsDatabase(300_000))   // рівно межа: 400 000 символів
        assertFalse(photoFitsDatabase(300_001))
        assertFalse(photoFitsDatabase(1_000_000))
    }

    @Test fun compressionPlanStartsLargeAndEndsSmall() {
        val plan = compressionPlan()
        assertEquals(CompressionStep(PHOTO_MAX_DIMENSION, 80), plan.first())
        assertEquals(PHOTO_MIN_DIMENSION, plan.last().maxSide)
        assertTrue(plan.size >= 8)
        // роздільність ніколи не зростає
        plan.zipWithNext().forEach { (a, b) -> assertTrue(b.maxSide <= a.maxSide) }
        // якість завжди в межах, які приймає JPEG-енкодер
        assertTrue(plan.all { it.quality in 30..95 })
        assertTrue(plan.all { it.maxSide >= PHOTO_MIN_DIMENSION })
    }

    @Test fun compressionPlanHasProgressivelyBetterChanceToFit() {
        val plan = compressionPlan()
        // на кожній стороні пробуємо чотири рівні якості
        assertEquals(4, plan.count { it.maxSide == PHOTO_MAX_DIMENSION })
        plan.groupBy { it.maxSide }.forEach { (_, steps) ->
            if (steps.size > 1) assertEquals(4, steps.size)
            assertEquals(steps.sortedByDescending { it.quality }, steps)
        }
        // останній крок — найменша дозволена сторона з найнижчою якістю
        assertEquals(CompressionStep(PHOTO_MIN_DIMENSION, 50), plan.last())
    }

    @Test fun photoRefGuardsExtremeAspectRatios() {
        val panorama = PhotoRef(id = "p1", width = 4000, height = 400, bytes = 1000)
        assertEquals(2.5f, panorama.aspectRatio, 0.001f)
        val tall = PhotoRef(id = "p2", width = 100, height = 4000, bytes = 1000)
        assertEquals(0.4f, tall.aspectRatio, 0.001f)
        assertFalse(PhotoRef().isValid)
        assertTrue(panorama.isValid)
    }

    @Test fun sizeLabelUsesKilobytes() {
        assertEquals("≈ 118 КБ", formatPhotoSize(120_832))
        assertEquals("≈ 512 Б", formatPhotoSize(512))
        assertEquals("", formatPhotoSize(0))
    }
}
