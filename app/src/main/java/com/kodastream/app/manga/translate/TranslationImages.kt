package com.kodastream.app.manga.translate

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

/** The page as a JPEG data URL body, shared by both translators. */
internal fun jpegBase64(bitmap: Bitmap, quality: Int = 85): String {
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}
