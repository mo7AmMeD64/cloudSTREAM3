package com.kodastream.app.ui.player

import android.net.Uri
import com.kodastream.app.engine.Engine
import com.lagradost.cloudstream3.ui.player.ExtractorUri
import com.lagradost.cloudstream3.ui.player.PlayerSubtitleHelper
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.VideoGenerator
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

/** Feeds the ported CloudStream player links from Koda's provider engine. */
class KodaPlayerGenerator(
    episodes: List<ResultEpisode>,
    private val offlineSubtitles: List<SubtitleData> = emptyList(),
) : VideoGenerator<ResultEpisode>(episodes) {
    override val hasCache = false
    override val canSkipLoading = false
    override fun getId(index: Int): Int? = videos.getOrNull(index)?.id

    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean,
    ): Boolean {
        val episode = videos.getOrNull(offset) ?: return false
        offlineSubtitles.forEach(subtitleCallback)

        if (episode.data.startsWith("content://") || episode.data.startsWith("file://")) {
            callback(
                null to ExtractorUri(
                    uri = Uri.parse(episode.data),
                    name = episode.name ?: episode.headerName,
                    id = episode.id,
                    episode = episode.episode,
                    season = episode.season,
                )
            )
            return true
        }

        val provider = Engine.provider(episode.apiName)
            ?: throw IllegalStateException("Provider \"${episode.apiName}\" is not loaded")

        provider.loadLinks(
            episode.data,
            isCasting,
            { file -> subtitleCallback(PlayerSubtitleHelper.getSubtitleData(file)) },
            { link -> callback(link to null) },
        )
        return true
    }
}
