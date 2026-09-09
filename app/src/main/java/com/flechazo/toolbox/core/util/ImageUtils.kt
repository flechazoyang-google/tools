package com.flechazo.toolbox.core.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Shared helpers for loading an image (with downsampling + EXIF correction) and
 * saving bitmaps/bytes to the gallery.
 */
object ImageUtils {

    /** 预览/取色用图的默认最大边，兼顾清晰度与内存（4096px ARGB 约 64 MB）。 */
    const val PREVIEW_MAX_SIDE = 1600

    /**
     * Decode a [Uri] into a Bitmap, downscaled so the largest side is at most [maxSide],
     * and rotated according to its EXIF orientation. Returns null on any failure.
     */
    suspend fun loadScaled(
        context: Context,
        uri: Uri,
        maxSide: Int = PREVIEW_MAX_SIDE,
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // 注意：inJustDecodeBounds = true 时 decodeStream 必然返回 null（只填 bounds），
            // 所以这里绝不能用它的返回值判断成败，必须看 openInputStream 是否为 null。
            val boundsStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            }
            val decodeStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val decoded = decodeStream.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null
            applyExifOrientation(context, uri, decoded)
        }.getOrNull()
    }

    /** 只读取图片尺寸，不分配像素内存。 */
    suspend fun readBounds(context: Context, uri: Uri): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
        }.getOrNull()
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it != bitmap) bitmap.recycle() }
        }.getOrDefault(bitmap)
    }

    /**
     * JPEG 不支持透明通道，直接压缩会把透明区域变成黑色，先合成到白底。
     */
    fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return out
    }

    /** Save a bitmap into MediaStore (Pictures/Toolbox). Returns the new Uri, or null on failure. */
    suspend fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        mime: String = "image/png",
        quality: Int = 90,
    ): Uri? = withContext(Dispatchers.IO) {
        val format = if (mime == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val source = if (format == Bitmap.CompressFormat.JPEG) flattenOnWhite(bitmap) else bitmap
        val bytes = ByteArrayOutputStream().use { out ->
            if (!source.compress(format, quality, out)) return@withContext null
            out.toByteArray()
        }
        saveBytes(context, bytes, mime)
    }

    /** Save raw bytes as an image into MediaStore (Pictures/Toolbox). Returns null on failure. */
    suspend fun saveBytes(
        context: Context,
        bytes: ByteArray,
        mime: String = "image/png",
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val isQ = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val name = "toolbox_${System.currentTimeMillis()}.$ext"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (isQ) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Toolbox")
                // 写入期间标记 pending，避免相册里出现半成品条目
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return@withContext null

        val written = runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        }.getOrDefault(false)

        if (!written) {
            // 清理占位行，避免相册里留下 0 字节的坏条目
            runCatching { resolver.delete(uri, null, null) }
            return@withContext null
        }
        if (isQ) {
            runCatching {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
        }
        uri
    }

    /** Compress and return bytes without saving. Returns null if the encoder fails. */
    suspend fun compressToBytes(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int,
    ): ByteArray? = withContext(Dispatchers.Default) {
        val source = if (format == Bitmap.CompressFormat.JPEG) flattenOnWhite(bitmap) else bitmap
        ByteArrayOutputStream().use { out ->
            if (!source.compress(format, quality, out)) null else out.toByteArray()
        }
    }
}
