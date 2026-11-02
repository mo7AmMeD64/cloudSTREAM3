package com.kodastream.app.ui.manga

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kodastream.app.R
import com.kodastream.app.manga.MangaRef
import com.kodastream.app.ui.components.EmptyBox

/** Manga library: categories, sorting and update checks land in section 5. */
@Composable
fun MangaLibraryScreen(
    contentPadding: PaddingValues,
    onOpenDetails: (MangaRef) -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        EmptyBox(
            title = stringResource(R.string.manga_library_empty_title),
            hint = stringResource(R.string.manga_library_empty_hint),
        )
    }
}
