package com.example.messenger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Фото, готове до відправки: base64 (те, що піде в базу) і розміри в пікселях.
 * Створюється у фоновому потоці, тому інтерфейс не «замерзає».
 */
data class PreparedPhoto(
    val base64: String,
    val width: Int,
    val height: Int,
    val jpegBytes: Int
)

/**
 * Стиснення фото перед відправкою.
 *
 * Порядок дій:
 *  1. читаємо розміри, не завантажуючи все зображення в пам'ять;
 *  2. декодуємо у зменшеному вигляді (inSampleSize), щоб не з'їсти пам'ять;
 *  3. повертаємо за EXIF (фото з камери часто «лежить на боці»);
 *  4. стискаємо за планом [compressionPlan], доки не вліземо в ліміт бази.
 */
object PhotoCompressor {

    /** Помилки, які показуємо користувачу зрозумілими словами */
    class PhotoException(message: String) : Exception(message)

    suspend fun prepare(context: Context, uri: Uri): Result<PreparedPhoto> =
        withContext(Dispatchers.IO) { runCatching { prepareBlocking(context, uri) } }

    private fun prepareBlocking(context: Context, uri: Uri): PreparedPhoto {
        val bounds = readBounds(context, uri)
            ?: throw PhotoException("PHOTO_UNREADABLE: не вдалося прочитати фото")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw PhotoException("PHOTO_UNREADABLE: не вдалося прочитати фото")
        }
        val rotation = readRotationDegrees(context, uri)

        for (step in compressionPlan()) {
            val bitmap = decodeScaled(context, uri, step.maxSide) ?: continue
            val upright = rotate(bitmap, rotation)
            // Фото без EXIF-повороту повертається тим самим об'єктом — не звільняємо його двічі.
            if (upright !== bitmap) bitmap.recycle()
            val jpeg = compress(upright, step.quality)
            val width = upright.width
            val height = upright.height
            upright.recycle()
            if (jpeg != null && photoFitsDatabase(jpeg.size)) {
                return PreparedPhoto(
                    base64 = Base64.encodeToString(jpeg, Base64.NO_WRAP),
                    width = width,
                    height = height,
                    jpegBytes = jpeg.size
                )
            }
        }
        throw PhotoException("PHOTO_TOO_LARGE: фото надто велике навіть після стиснення")
    }

    private fun readBounds(context: Context, uri: Uri): BitmapFactory.Options? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }
        return if (options.outWidth > 0 && options.outHeight > 0) options else null
    }

    /** Скільки градусів треба повернути фото, щоб воно стояло рівно */
    private fun readRotationDegrees(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        return runCatching {
            Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(degrees.toFloat()) }, true
            )
        }.getOrDefault(bitmap)
    }

    /** Декодувати так, щоб довша сторона була не більша за [maxSide] */
    private fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bounds = readBounds(context, uri) ?: return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull() ?: return null
        val target = fitInside(raw.width, raw.height, maxSide)
        if (!target.isValid || (target.width == raw.width && target.height == raw.height)) return raw
        return runCatching {
            Bitmap.createScaledBitmap(raw, target.width, target.height, true).also { scaled ->
                if (scaled !== raw) raw.recycle()
            }
        }.getOrDefault(raw)
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray? = runCatching {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }.getOrNull()

    /** Зрозумілий текст помилки для банера в чаті */
    fun errorText(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("PHOTO_UNREADABLE") -> "Не вдалося прочитати фото. Спробуй інше."
            message.contains("PHOTO_TOO_LARGE") -> "Фото надто велике навіть після стиснення."
            else -> "Не вдалося підготувати фото: ${message.ifEmpty { error.javaClass.simpleName }}"
        }
    }
}
