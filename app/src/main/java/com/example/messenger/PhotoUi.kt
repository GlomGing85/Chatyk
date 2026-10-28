package com.example.messenger

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Кеш розкодованих зображень у пам'яті.
 * Обмежений шостою частиною доступної пам'яті додатка: старі фото витісняються,
 * а не «з'їдають» телефон. Base64 лишається в ChatViewModel, тож повторно
 * декодувати з мережі не доведеться.
 */
private object PhotoBitmaps {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 6).toInt().coerceAtLeast(4 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
}

/** Розкодувати base64 у Bitmap. Виконується у фоновому потоці, не в головному. */
private fun decodePhoto(base64: String): Bitmap? = runCatching {
    val bytes = Base64.decode(base64, Base64.NO_WRAP)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_MAX_DIMENSION) sample *= 2
    BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }
    )
}.getOrNull()

/**
 * Bitmap для фото з кешу або з бази. Поки декодується — null
 * (інтерфейс показує індикатор завантаження).
 */
@Composable
fun rememberPhotoBitmap(cacheKey: String, base64: String?): Bitmap? {
    if (base64 == null) return null
    PhotoBitmaps.get(cacheKey)?.let { return it }
    val state = produceState<Bitmap?>(null, cacheKey, base64) {
        value = withContext(Dispatchers.Default) {
            decodePhoto(base64)?.also { PhotoBitmaps.put(cacheKey, it) }
        }
    }
    return state.value
}

/** Розмір «рамки» для фото в бульбашці: пропорції фото, але в межах екрана */
private fun photoBox(meta: PhotoRef, compact: Boolean): Pair<Dp, Dp> {
    val maxWidth = if (compact) 232.dp else 268.dp
    val maxHeight = if (compact) 240.dp else 300.dp
    val aspect = meta.aspectRatio
    var width = maxWidth
    var height = width / aspect
    if (height > maxHeight) {
        height = maxHeight
        width = height * aspect
    }
    return width to height
}

/**
 * Фото в бульбашці повідомлення.
 * Дані підвантажуються лише тоді, коли фото з'явилося на екрані (ліниво),
 * тому вхід у кімнату з сотнями фото лишається швидким.
 */
@Composable
fun MessagePhoto(vm: ChatViewModel, meta: PhotoRef, compact: Boolean, onOpen: () -> Unit) {
    LaunchedEffect(meta.id) { vm.ensurePhoto(meta) }
    val state = vm.photoStates[meta.id]
    val (width, height) = photoBox(meta, compact)

    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(if (compact) 14.dp else 18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is PhotoState.Ready -> {
                val bitmap = rememberPhotoBitmap(meta.id, state.base64)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Фото в повідомленні",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clickable { onOpen() }
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
            is PhotoState.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Фото не завантажилось", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { vm.retryPhoto(meta) }) { Text("Повторити") }
            }
            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Text("Фото…", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Прев'ю вибраного фото над полем введення (можна прибрати до відправки) */
@Composable
fun PendingPhotoRow(vm: ChatViewModel, compact: Boolean) {
    val pending = vm.pendingPhoto
    if (!vm.preparingPhoto && pending == null) return
    val side = if (compact) 48.dp else 56.dp
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = if (compact) 12.dp else 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pending != null) {
            val bitmap = rememberPhotoBitmap("pending", pending.base64)
            Box(
                modifier = Modifier.size(side).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Вибране фото",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Фото готове", style = MaterialTheme.typography.labelLarge)
                Text(
                    "${pending.width}×${pending.height} · ${formatPhotoSize(pending.jpegBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SymbolButton(R.drawable.symbol_delete, "Прибрати фото", vm::clearPendingPhoto, compact = compact)
        } else {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(12.dp))
            Text("Стискаю фото…", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Повноекранний перегляд фото: тап будь-де або «Назад» — закрити */
@Composable
fun PhotoViewer(meta: PhotoRef, base64: String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.97f)).clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            val bitmap = rememberPhotoBitmap(meta.id, base64)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Фото на весь екран",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
            } else {
                CircularProgressIndicator()
            }
            SymbolButton(
                R.drawable.symbol_close, "Закрити фото", onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).statusBarsPadding()
            )
        }
    }
}
