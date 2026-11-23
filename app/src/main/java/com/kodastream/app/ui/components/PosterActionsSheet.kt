package com.kodastream.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.data.CategoryResult
import com.kodastream.app.data.FavoriteCategoryStore
import com.kodastream.app.data.LibraryStore
import com.kodastream.app.ui.library.MoveToSheet
import com.lagradost.cloudstream3.SearchResponse

/**
 * The actions a long-press opens on a poster. Library state is read from the
 * stores and updates in place, so the sheet can stay open while the user
 * favorites the title and then picks its category.
 */
@Composable
fun PosterActionsSheet(
    item: SearchResponse,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val revision by LibraryStore.revision.collectAsState()
    val favorite = remember(revision, item.apiName, item.url) {
        LibraryStore.isFavorite(context, item.apiName, item.url)
    }
    val watchlisted = remember(revision, item.apiName, item.url) {
        LibraryStore.isWatchlisted(context, item.apiName, item.url)
    }
    var choosingCategory by remember(item) { mutableStateOf(false) }

    if (choosingCategory) {
        MoveToSheet(
            categories = FavoriteCategoryStore.categories(context),
            selectedCategoryIds = setOf(
                FavoriteCategoryStore.categoryOf(context, item.apiName, item.url)
            ),
            onDismiss = onDismiss,
            onMove = { categoryId ->
                FavoriteCategoryStore.move(context, listOf(item.key()), categoryId)
                onDismiss()
            },
            onCreateAndMove = { name ->
                val result = FavoriteCategoryStore.create(context, name)
                if (result is CategoryResult.Created) {
                    FavoriteCategoryStore.move(context, listOf(item.key()), result.category.id)
                    onDismiss()
                }
            },
        )
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            Header(item)
            ActionRow(
                icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = stringResource(
                    if (favorite) {
                        R.string.poster_action_remove_favorite
                    } else {
                        R.string.poster_action_add_favorite
                    }
                ),
                active = favorite,
                onClick = { LibraryStore.toggleFavorite(context, item) },
            )
            if (favorite) {
                ActionRow(
                    icon = Icons.Rounded.Folder,
                    label = stringResource(R.string.favorites_choose_category),
                    onClick = { choosingCategory = true },
                )
            }
            ActionRow(
                icon = if (watchlisted) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                label = stringResource(
                    if (watchlisted) {
                        R.string.poster_action_remove_watchlist
                    } else {
                        R.string.poster_action_add_watchlist
                    }
                ),
                active = watchlisted,
                onClick = { LibraryStore.toggleWatchlist(context, item) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            ActionRow(
                icon = Icons.Rounded.Share,
                label = stringResource(R.string.action_share),
                onClick = {
                    share(context, item)
                    onDismiss()
                },
            )
            ActionRow(
                icon = Icons.Rounded.ContentCopy,
                label = stringResource(R.string.copy_link),
                onClick = {
                    copyLink(context, item)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun Header(item: SearchResponse) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = item.posterUrl,
            headers = item.posterHeaders,
            contentDescription = null,
            modifier = Modifier
                .width(56.dp)
                .height(84.dp)
                .clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.apiName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun SearchResponse.key(): String = LibraryStore.storageKey(apiName, url)

private fun share(context: Context, item: SearchResponse) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, item.name, item.url))
    runCatching {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
    }
}

private fun copyLink(context: Context, item: SearchResponse) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(item.name, item.url))
    // Android 13 shows its own clipboard confirmation.
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        Toast.makeText(context, R.string.copy_link_toast, Toast.LENGTH_SHORT).show()
    }
}
