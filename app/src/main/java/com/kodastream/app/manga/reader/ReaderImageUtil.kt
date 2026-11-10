package com.kodastream.app.manga.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import ca.mpreg.imagedecoder.ImageDecoder
import okio.Buffer
import okio.BufferedSource

/**
 * The image helpers Mihon's viewer uses before handing a page to the image
 * view: whether an animated format is supported, whether a page is a
 * double-page spread, and the split/rotate/merge operations that make one fit
 * the pager or a webtoon strip.
 *
 * Ported from `core/common/.../util/system/ImageUtil.kt`; only the reader's
 * slice is kept.
 */
object ReaderImageUtil {

    enum class ImageType {
        AVIF,
        GIF,
        HEIF,
        JPEG,
        JXL,
        PNG,
        WEBP,
        JP2,
        JPX,
    }

    enum class Side {
        RIGHT,
        LEFT,
    }

    fun isAnimatedAndSupported(source: BufferedSource): Boolean = try {
        when (findImageType(source.peek().inputStream())) {
            ImageType.GIF -> true
            ImageType.WEBP, ImageType.HEIF -> {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    ImageDecoder.open(source.peek().inputStream()).use { it.pages > 1 }
            }
            else -> false
        }
    } catch (_: Exception) {
        false
    }

    fun isWideImage(source: BufferedSource): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(source.peek().inputStream(), null, options)
        return options.outWidth > options.outHeight
    }

    fun splitInHalf(source: BufferedSource, side: Side): BufferedSource {
        val image = decode(source)
        val width = image.width
        val height = image.height
        val half = createBitmap(width / 2, height)
        val part = when (side) {
            Side.RIGHT -> Rect(width - width / 2, 0, width, height)
            Side.LEFT -> Rect(0, 0, width / 2, height)
        }
        half.applyCanvas {
            drawBitmap(image, part, Rect(0, 0, width / 2, height), null)
        }
        return compress(half)
    }

    fun rotateImage(source: BufferedSource, degrees: Float): BufferedSource {
        val image = decode(source)
        val rotated = Bitmap.createBitmap(
            image,
            0,
            0,
            image.width,
            image.height,
            Matrix().apply { postRotate(degrees) },
            true,
        )
        return compress(rotated)
    }

    private fun decode(source: BufferedSource): Bitmap =
        BitmapFactory.decodeStream(source.inputStream())
            ?: throw IllegalStateException("Unreadable page image")

    private fun compress(bitmap: Bitmap): BufferedSource {
        val output = Buffer()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output.outputStream())
        return output
    }

    private fun findImageType(stream: java.io.InputStream): ImageType? =
        ImageDecoder.open(stream).use { decoder ->
            when (decoder.format) {
                ImageDecoder.Format.JPEG -> ImageType.JPEG
                ImageDecoder.Format.PNG -> ImageType.PNG
                ImageDecoder.Format.WEBP -> ImageType.WEBP
                ImageDecoder.Format.GIF -> ImageType.GIF
                ImageDecoder.Format.AVIF -> ImageType.AVIF
                ImageDecoder.Format.HEIF -> ImageType.HEIF
                ImageDecoder.Format.JXL -> ImageType.JXL
                ImageDecoder.Format.JP2 -> ImageType.JP2
                else -> null
            }
        }
}
