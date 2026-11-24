package com.iyonzkasir.data.remote

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
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object ImageHelper {
    private const val DIR_NAME = "v2_photos"

    private fun photosDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /** Copy & resize gambar dari galeri ke internal storage (maks 1024px) agar ringan & ikut ter-backup. */
    suspend fun savePickedPhoto(
        context: Context,
        sourceUri: Uri,
        prefix: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeSampledBitmap(context, sourceUri, 1024) ?: return@withContext null
            val rotated = rotateIfNeeded(context, sourceUri, bitmap)
            val file = File(photosDir(context), "${prefix}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (rotated != bitmap) rotated.recycle()
            bitmap.recycle()
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /** Kompres thumbnail kecil (~15-28 KB) jadi Data URI untuk Firestore Spark gratis. */
    suspend fun compressForCloud(
        context: Context,
        pathOrUri: String?,
        maxDim: Int = 360,
        quality: Int = 72
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (pathOrUri.isNullOrBlank()) return@withContext null
        try {
            val rawBitmap = if (pathOrUri.startsWith("content://")) {
                decodeSampledBitmap(context, Uri.parse(pathOrUri), maxDim)
            } else {
                val f = File(pathOrUri)
                if (!f.exists()) null else decodeSampledFile(f, maxDim)
            } ?: return@withContext null

            val scaled = scaleDown(rawBitmap, maxDim)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val bytes = baos.toByteArray()
            if (scaled != rawBitmap) scaled.recycle()
            rawBitmap.recycle()

            val hash = MessageDigest.getInstance("MD5").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            dataUrl to hash
        } catch (_: Exception) {
            null
        }
    }

    fun deleteLocalPhoto(path: String?) {
        if (path.isNullOrBlank() || !path.contains(DIR_NAME)) return
        try { File(path).delete() } catch (_: Exception) {}
    }

    suspend fun cleanupOrphans(context: Context, usedPaths: Set<String>) = withContext(Dispatchers.IO) {
        try {
            photosDir(context).listFiles()?.forEach { f ->
                if (f.absolutePath !in usedPaths) f.delete()
            }
        } catch (_: Exception) {}
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, reqSize: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        opts.inSampleSize = calcSampleSize(opts.outWidth, opts.outHeight, reqSize)
        opts.inJustDecodeBounds = false
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    private fun decodeSampledFile(file: File, reqSize: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        opts.inSampleSize = calcSampleSize(opts.outWidth, opts.outHeight, reqSize)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun calcSampleSize(w: Int, h: Int, req: Int): Int {
        var sample = 1
        while (w / sample > req * 2 || h / sample > req * 2) sample *= 2
        return sample
    }

    private fun scaleDown(bmp: Bitmap, maxDim: Int): Bitmap {
        val ratio = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height)
        if (ratio >= 1f) return bmp
        return Bitmap.createScaledBitmap(
            bmp, (bmp.width * ratio).toInt().coerceAtLeast(1),
            (bmp.height * ratio).toInt().coerceAtLeast(1), true
        )
    }

    private fun rotateIfNeeded(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        return try {
            val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
            val deg = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (deg == 0f) bmp else Bitmap.createBitmap(
                bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true
            )
        } catch (_: Exception) { bmp }
    }
}
