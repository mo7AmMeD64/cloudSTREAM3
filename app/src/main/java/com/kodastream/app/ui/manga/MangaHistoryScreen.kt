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

/** Manga history: day groups and resume land in section 8. */
@Composable
fun MangaHistoryScreen(
    contentPadding: PaddingValues,
    onOpenDetails: (MangaRef) -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        EmptyBox(
            title = stringResource(R.string.manga_history_empty_title),
            hint = stringResource(R.string.manga_history_empty_hint),
        )
    }
}
