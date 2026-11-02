package com.kodastream.app.ui.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kodastream.app.manga.MangaCover
import com.kodastream.app.manga.MangaExtensionIcon
import com.kodastream.app.manga.MangaImageLoader

/**
 * A cover through the source's own client. The request model carries the
 * source id, so the manga loader can pick the right headers and cookies and
 * key its disk cache by source and URL.
 */
@Composable
fun MangaCoverImage(
    sourceId: Long,
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val context = LocalContext.current
    val placeholder = remember(containerColor) { ColorPainter(containerColor) }
    Box(
        modifier = modifier.background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp),
            )
        } else {
            AsyncImage(
                model = MangaCover(sourceId, url),
                imageLoader = MangaImageLoader.get(context),
                contentDescription = contentDescription,
                contentScale = contentScale,
                placeholder = placeholder,
                error = placeholder,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** An extension's store icon, with the generic extension symbol as fallback. */
@Composable
fun MangaExtensionIconImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = MaterialTheme.colorScheme.surfaceContainerHighest
    val placeholder = remember(container) { ColorPainter(container) }
    Box(
        modifier = modifier.background(container),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                Icons.Outlined.Extension,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        } else {
            AsyncImage(
                model = MangaExtensionIcon(url),
                imageLoader = MangaImageLoader.get(context),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
