package com.example.messenger

import kotlin.math.roundToInt

// --- Фото в чаті: обмеження й геометрія ---
//
// Фото НЕ вантажаться у Firebase Storage (з лютого 2026 він вимагає платного
// плану Blaze із прив'язаною карткою). Натомість стиснене зображення лежить
// у самій Realtime Database, окремим вузлом:
//
//   rooms/{код}/photos/{id}/data  — JPEG, закодований у base64
//   rooms/{код}/messages/{id}/photo = { id, w, h, bytes }  — лише розміри
//
// Так стрічка чату завантажується легко (метадані), а саме зображення
// підвантажується тоді, коли бульбашка з'являється на екрані.
// Правила бази (database.rules.json) перевіряють ті самі ліміти.

/** Довша сторона зображення, яке зберігаємо (більше для телефона не потрібно) */
const val PHOTO_MAX_DIMENSION = 1280

/** Менша сторона, до якої ще можна зменшувати фото, якщо не влізає в ліміт */
const val PHOTO_MIN_DIMENSION = 240

/** Скільки символів base64 може займати одне фото в базі (400 000 ≈ 300 КБ JPEG) */
const val PHOTO_MAX_BASE64_CHARS = 400_000

/** Розмір JPEG, з яким пробуємо вкластися: base64 від нього ще проходить правило бази */
const val PHOTO_TARGET_JPEG_BYTES = 290_000

/** Розмір сторін і якість JPEG для одного кроку стиснення */
data class CompressionStep(val maxSide: Int, val quality: Int)

/** Скільки пікселів по довшій стороні та як сильно стискати — у результаті */
data class PhotoSize(val width: Int, val height: Int) {
    val longestSide: Int get() = maxOf(width, height)
    val isValid: Boolean get() = width > 0 && height > 0
}

/**
 * Метадані фото, які лежать у повідомленні.
 * Самі байти — в окремому вузлі `photos/{id}/data`.
 */
data class PhotoRef(
    val id: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val bytes: Int = 0
) {
    val isValid: Boolean get() = id.isNotEmpty() && width > 0 && height > 0

    /** Співвідношення сторін, обмежене, щоб екстремальні панорами не ламали бульбашку */
    val aspectRatio: Float
        get() = if (height <= 0) 1f else (width.toFloat() / height).coerceIn(0.4f, 2.5f)
}

/**
 * Вписати зображення [width]×[height] у квадрат [max]×[max], зберігши пропорції.
 * Ніколи не збільшує зображення. Некоректні вхідні дані → порожній розмір.
 */
fun fitInside(width: Int, height: Int, max: Int): PhotoSize {
    if (width <= 0 || height <= 0 || max <= 0) return PhotoSize(0, 0)
    val longest = maxOf(width, height)
    if (longest <= max) return PhotoSize(width, height)
    val scale = max.toDouble() / longest
    return PhotoSize(
        width = (width * scale).roundToInt().coerceIn(1, max),
        height = (height * scale).roundToInt().coerceIn(1, max)
    )
}

/** Довжина base64-рядка (з padding) для [bytes] байтів — саме стільки рахує правило бази */
fun base64Length(bytes: Int): Int = if (bytes <= 0) 0 else 4 * ((bytes + 2) / 3)

/** Чи влізе JPEG такого розміру в базу (див. PHOTO_MAX_BASE64_CHARS) */
fun photoFitsDatabase(jpegBytes: Int): Boolean = base64Length(jpegBytes) <= PHOTO_MAX_BASE64_CHARS

/**
 * План стиснення: спочатку втрачаємо якість (80 → 50), потім роздільність
 * (1280 → 1024 → 819 → 655 → 524 → 419 → 335 → 268 → 240).
 * Останній крок завжди на [PHOTO_MIN_DIMENSION]: якщо навіть він не вліз,
 * фото вважаємо непридатним і кажемо про це користувачу.
 * Використовується перший крок, який влізає в ліміт бази.
 */
fun compressionPlan(): List<CompressionStep> {
    val steps = mutableListOf<CompressionStep>()
    var side = PHOTO_MAX_DIMENSION
    while (side >= PHOTO_MIN_DIMENSION) {
        for (quality in intArrayOf(80, 70, 60, 50)) steps += CompressionStep(side, quality)
        side = (side * 4) / 5
    }
    if (steps.lastOrNull()?.maxSide != PHOTO_MIN_DIMENSION) {
        steps += CompressionStep(PHOTO_MIN_DIMENSION, 50)
    }
    return steps
}

/** Розмір для показу в інтерфейсі: "≈ 118 КБ" */
fun formatPhotoSize(bytes: Int): String {
    if (bytes <= 0) return ""
    return if (bytes < 1024) "≈ $bytes Б"
    else "≈ ${(bytes / 1024.0).roundToInt()} КБ"
}
