package com.lagradost.cloudstream3.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.annotation.DrawableRes
import coil.load
import coil.request.ImageRequest
import com.lagradost.cloudstream3.USER_AGENT

/**
 * The original app's Coil-3 ImageLoader, trimmed to the overloads the
 * ported player's View layer uses and running on KodaStream's Coil 2:
 * Int resources load synchronously (as the original does), everything
 * else goes through Coil with the engine's default headers.
 */
@Suppress("unused")
object ImageLoader {

    private fun ImageView.loadImageInternal(
        imageData: Any?,
        headers: Map<String, String>? = null,
        builder: ImageRequest.Builder.() -> Unit = {},
    ) {
        // clear image to avoid loading & flickering issues at fast scrolling
        setImageDrawable(null)
        if (imageData == null) return
        if (imageData is Int) {
            this.setImageResource(imageData)
            return
        }
        coil.load(imageData) {
            addHeader("User-Agent", USER_AGENT)
            headers?.forEach { (key, value) -> addHeader(key, value) }
            builder()
        }
    }

    fun ImageView.loadImage(
        imageData: String?,
        headers: Map<String, String>? = null,
        builder: ImageRequest.Builder.() -> Unit = {},
    ) = loadImageInternal(imageData, headers, builder)

    fun ImageView.loadImage(
        imageData: Uri?,
        headers: Map<String, String>? = null,
        builder: ImageRequest.Builder.() -> Unit = {},
    ) = loadImageInternal(imageData, headers, builder)

    fun ImageView.loadImage(
        imageData: Drawable?,
        builder: ImageRequest.Builder.() -> Unit = {},
    ) = loadImageInternal(imageData, null, builder)

    fun ImageView.loadImage(
        imageData: Bitmap?,
        builder: ImageRequest.Builder.() -> Unit = {},
    ) = loadImageInternal(imageData, null, builder)

    fun ImageView.loadImage(
        @DrawableRes imageData: Int?,
        builder: ImageRequest.Builder.() -> Unit = {},
    ) = loadImageInternal(imageData, null, builder)
}
