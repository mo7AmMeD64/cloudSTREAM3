package com.kodastream.app.ui.manga

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kodastream.app.R
import com.kodastream.app.manga.MangaRef
import com.kodastream.app.ui.components.EmptyBox

/** Manga Browse: the source picker and the poster grid land in section 2. */
@Composable
fun MangaBrowseScreen(
    contentPadding: PaddingValues,
    onOpenDetails: (MangaRef) -> Unit,
    onOpenExtensions: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        EmptyBox(
            title = stringResource(R.string.manga_browse_no_sources_title),
            hint = stringResource(R.string.manga_browse_no_sources_hint),
        ) {
            FilledTonalButton(onClick = onOpenExtensions) {
                Text(stringResource(R.string.manga_browse_open_extensions))
            }
        }
    }
}
