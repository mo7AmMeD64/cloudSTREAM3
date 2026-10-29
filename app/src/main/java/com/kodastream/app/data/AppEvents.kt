package com.kodastream.app.data

import kotlinx.coroutines.flow.MutableStateFlow

/** One-way messages from the Activity to the UI. */
object AppEvents {
    /** Ask the Library screen to open on this section ("DOWNLOADS"). */
    val pendingLibrarySection = MutableStateFlow<String?>(null)

    /** A repository link (cloudstreamrepo://...) that was opened from outside the app. */
    val pendingRepoUrl = MutableStateFlow<String?>(null)
}
