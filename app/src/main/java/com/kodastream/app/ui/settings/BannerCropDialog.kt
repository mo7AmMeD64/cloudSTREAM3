package com.kodastream.app.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import coil.imageLoader
import coil.request.ImageRequest
import com.kodastream.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_BITMAP_PX = 2048
private const val MAX_OUTPUT_WIDTH = 1920

/**
 * Full-screen crop for the banner photo. The frame has the banner's aspect, so
 * whatever sits inside it is exactly what the profile card will show.
 */
@Composable
internal fun BannerCropDialog(
    uri: Uri,
    aspect: Float,
    onCancel: () -> Unit,
    onCropped: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    val frameSize = remember { mutableStateOf(IntSize.Zero) }
    val zoom = remember { mutableFloatStateOf(1f) }
    val pan = remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(uri) {
        bitmap = loadCropBitmap(context, uri)
        loading = false
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            // The account page is FLAG_SECURE; the dialog is its own window.
            securePolicy = SecureFlagPolicy.SecureOn,
        ),
    ) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.cancel), color = Color.White)
                    }
                    Text(
                        text = stringResource(R.string.account_crop_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    val canCrop = bitmap != null && frameSize.value != IntSize.Zero
                    TextButton(
                        onClick = {
                            val source = bitmap
                            val size = frameSize.value
                            if (source != null && size != IntSize.Zero) {
                                onCropped(
                                    cropBanner(
                                        source,
                                        cropRect(
                                            frameWidth = size.width,
                                            frameHeight = size.height,
                                            bitmapWidth = source.width,
                                            bitmapHeight = source.height,
                                            zoom = zoom.floatValue,
                                            panX = pan.value.x,
                                            panY = pan.value.y,
                                        ),
                                    ),
                                )
                            }
                        },
                        enabled = canCrop,
                    ) {
                        Text(
                            text = stringResource(R.string.account_crop_done),
                            color = if (canCrop) {
                                Color.White
                            } else {
                                Color.White.copy(alpha = 0.4f)
                            },
                        )
                    }
                }
                CropArea(
                    bitmap = bitmap,
                    loading = loading,
                    aspect = aspect,
                    zoom = zoom,
                    pan = pan,
                    frameSize = frameSize,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CropArea(
    bitmap: Bitmap?,
    loading: Boolean,
    aspect: Float,
    zoom: MutableFloatState,
    pan: MutableState<Offset>,
    frameSize: MutableState<IntSize>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val safeAspect = if (aspect > 0f) aspect else 3f
        val frameHeight = (maxWidth * 0.92f / safeAspect).coerceAtMost(maxHeight * 0.6f)
        val frameWidth = frameHeight * safeAspect
        val dim = Color.Black.copy(alpha = 0.62f)
        // BoxScope shadows the constraints receiver inside the Box below.
        val areaWidth = maxWidth
        val areaHeight = maxHeight
        var areaSize by remember { mutableStateOf(IntSize.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { areaSize = it }
                .pointerInput(bitmap, frameSize.value, areaSize) {
                    val source = bitmap ?: return@pointerInput
                    val size = frameSize.value
                    if (size.width == 0 || size.height == 0) return@pointerInput
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val currentZoom = zoom.floatValue
                        val nextZoom = (currentZoom * zoomChange).coerceIn(1f, 5f)
                        val ratio = nextZoom / currentZoom
                        // The frame is centered in the area, so the area's center is its center.
                        val center = Offset(areaSize.width / 2f, areaSize.height / 2f)
                        val anchor = centroid - center
                        val limit = panLimit(
                            frameWidth = size.width,
                            frameHeight = size.height,
                            bitmapWidth = source.width,
                            bitmapHeight = source.height,
                            zoom = nextZoom,
                        )
                        val next = anchor - (anchor - pan.value) * ratio + panChange
                        pan.value = Offset(
                            next.x.coerceIn(-limit.x, limit.x),
                            next.y.coerceIn(-limit.y, limit.y),
                        )
                        zoom.floatValue = nextZoom
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(frameWidth, frameHeight)
                    .onSizeChanged { frameSize.value = it }
                    .clipToBounds()
                    .border(1.dp, Color.White.copy(alpha = 0.85f)),
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom.floatValue
                                scaleY = zoom.floatValue
                                translationX = pan.value.x
                                translationY = pan.value.y
                            },
                    )
                }
            }
            // Dims the overflow outside the frame; the zoomed image shows through.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(((areaHeight - frameHeight) / 2).coerceAtLeast(0.dp))
                    .background(dim),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(((areaHeight - frameHeight) / 2).coerceAtLeast(0.dp))
                    .background(dim),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(((areaWidth - frameWidth) / 2).coerceAtLeast(0.dp))
                    .height(frameHeight)
                    .background(dim),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(((areaWidth - frameWidth) / 2).coerceAtLeast(0.dp))
                    .height(frameHeight)
                    .background(dim),
            )
            if (loading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else if (bitmap == null) {
                Text(
                    text = stringResource(R.string.account_crop_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

internal data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int)

/** How far the bitmap can move before an edge would enter the frame. */
internal fun panLimit(
    frameWidth: Int,
    frameHeight: Int,
    bitmapWidth: Int,
    bitmapHeight: Int,
    zoom: Float,
): Offset {
    if (frameWidth <= 0 || frameHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
        return Offset.Zero
    }
    val cover = max(
        frameWidth.toFloat() / bitmapWidth,
        frameHeight.toFloat() / bitmapHeight,
    )
    val total = (cover * zoom).coerceAtLeast(0.0001f)
    return Offset(
        x = ((bitmapWidth * total - frameWidth) / 2f).coerceAtLeast(0f),
        y = ((bitmapHeight * total - frameHeight) / 2f).coerceAtLeast(0f),
    )
}

/** The part of the bitmap visible inside the frame for the current transform. */
internal fun cropRect(
    frameWidth: Int,
    frameHeight: Int,
    bitmapWidth: Int,
    bitmapHeight: Int,
    zoom: Float,
    panX: Float,
    panY: Float,
): CropRect {
    if (frameWidth <= 0 || frameHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
        return CropRect(0, 0, bitmapWidth.coerceAtLeast(1), bitmapHeight.coerceAtLeast(1))
    }
    val cover = max(
        frameWidth.toFloat() / bitmapWidth,
        frameHeight.toFloat() / bitmapHeight,
    )
    val total = (cover * zoom).coerceAtLeast(0.0001f)
    val limit = panLimit(frameWidth, frameHeight, bitmapWidth, bitmapHeight, zoom)
    val left = (frameWidth - bitmapWidth * total) / 2f + panX.coerceIn(-limit.x, limit.x)
    val top = (frameHeight - bitmapHeight * total) / 2f + panY.coerceIn(-limit.y, limit.y)
    val sx = (-left / total).roundToInt().coerceIn(0, bitmapWidth - 1)
    val sy = (-top / total).roundToInt().coerceIn(0, bitmapHeight - 1)
    val sw = (frameWidth / total).roundToInt().coerceIn(1, bitmapWidth - sx)
    val sh = (frameHeight / total).roundToInt().coerceIn(1, bitmapHeight - sy)
    return CropRect(sx, sy, sw, sh)
}

private fun cropBanner(source: Bitmap, rect: CropRect): Bitmap {
    val cropped = Bitmap.createBitmap(source, rect.left, rect.top, rect.width, rect.height)
    if (cropped.width <= MAX_OUTPUT_WIDTH) return cropped
    val height = (cropped.height * MAX_OUTPUT_WIDTH.toFloat() / cropped.width)
        .roundToInt()
        .coerceAtLeast(1)
    return Bitmap.createScaledBitmap(cropped, MAX_OUTPUT_WIDTH, height, true)
}

private suspend fun loadCropBitmap(context: Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false)
                .size(MAX_BITMAP_PX)
                .build()
            (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
        }.getOrNull()
    }
