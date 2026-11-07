package com.ahmed.rotrun.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream

object ProfilePhoto {
    fun jpeg(resolver: ContentResolver, uri: Uri): ByteArray? {
        val decoded = decode(resolver, uri) ?: return null
        val side = minOf(decoded.width, decoded.height).coerceAtLeast(1)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val scaled = if (square.width == 256) square else Bitmap.createScaledBitmap(square, 256, 256, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (scaled !== decoded) scaled.recycle()
        if (square !== decoded && square !== scaled) square.recycle()
        decoded.recycle()
        return out.toByteArray()
    }

    private fun decode(resolver: ContentResolver, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            return runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }
}
