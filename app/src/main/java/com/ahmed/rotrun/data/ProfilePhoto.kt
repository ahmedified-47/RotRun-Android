package com.ahmed.rotrun.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream

object ProfilePhoto {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun jpeg(resolver: ContentResolver, uri: Uri): ByteArray? {
        val decoded = decode(resolver, uri) ?: return null
        val bytes = compress(decoded)
        decoded.recycle()
        return bytes
    }

    /** Long edge starts at 512 and quality at 82, then steps down until the JPEG is under 2 MB. */
    internal fun nextAttempt(edge: Int, quality: Int): Pair<Int, Int>? {
        val softer = quality - 12
        if (softer >= 35) return edge to softer
        val smaller = edge / 2
        if (smaller >= 128) return smaller to 82
        return null
    }

    private fun compress(source: Bitmap): ByteArray? {
        var edge = 512
        var quality = 82
        while (true) {
            val scaled = scaled(source, edge)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (scaled !== source) scaled.recycle()
            if (out.size() <= MAX_BYTES) return out.toByteArray()
            val next = nextAttempt(edge, quality) ?: return null
            edge = next.first
            quality = next.second
        }
    }

    private fun scaled(source: Bitmap, longEdge: Int): Bitmap {
        val long = maxOf(source.width, source.height).coerceAtLeast(1)
        if (long <= longEdge) return source
        val scale = longEdge.toFloat() / long
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
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
