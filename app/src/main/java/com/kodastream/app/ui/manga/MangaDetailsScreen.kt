package com.kodastream.app.ui.manga

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kodastream.app.R
import com.kodastream.app.manga.MangaRef

/**
 * Manga details. Section 1 owns the route and the back behaviour; the hero,
 * chapters and library actions land in section 4.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailsScreen(
    ref: MangaRef,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        topBar = {
            TopAppBar(
                title = { Text(ref.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { }
}
