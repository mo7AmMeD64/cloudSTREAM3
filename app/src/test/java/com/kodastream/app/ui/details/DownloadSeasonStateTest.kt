package com.kodastream.app.ui.details

import com.lagradost.cloudstream3.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSeasonStateTest {

    @Suppress("DEPRECATION_ERROR")
    private fun createEpisode(data: String, season: Int, episode: Int): Episode {
        return Episode(
            data = data,
            name = "Episode $episode",
            season = season,
            episode = episode,
        )
    }

    @Test
    fun seasonTargetHoldsAllEpisodesInOrder() {
        val episodes = listOf(
            createEpisode("ep1", season = 1, episode = 1),
            createEpisode("ep2", season = 1, episode = 2),
            createEpisode("ep3", season = 1, episode = 3),
        )
        val target = DownloadTarget.Season(episodes)
        assertEquals(3, target.episodes.size)
        assertEquals("ep1", target.episodes[0].data)
        assertEquals("ep2", target.episodes[1].data)
        assertEquals("ep3", target.episodes[2].data)
    }

    @Test
    fun filterEpisodesForSelectedSeason() {
        val allEpisodes = listOf(
            createEpisode("s1e1", season = 1, episode = 1),
            createEpisode("s1e2", season = 1, episode = 2),
            createEpisode("s2e1", season = 2, episode = 1),
            createEpisode("s2e2", season = 2, episode = 2),
        )

        val season1 = allEpisodes.filter { it.season == 1 }
        val season2 = allEpisodes.filter { it.season == 2 }

        assertEquals(2, season1.size)
        assertTrue(season1.all { it.season == 1 })
        assertEquals(2, season2.size)
        assertTrue(season2.all { it.season == 2 })
    }

    @Test
    fun skipAlreadyDownloadedEpisodes() {
        val episodes = listOf(
            createEpisode("s1e1", season = 1, episode = 1),
            createEpisode("s1e2", season = 1, episode = 2),
            createEpisode("s1e3", season = 1, episode = 3),
        )
        val completedIds = setOf("provider|url|1|2")

        val toQueue = episodes.filter { ep ->
            val id = "provider|url|${ep.season}|${ep.episode}"
            id !in completedIds
        }

        assertEquals(2, toQueue.size)
        assertEquals(1, toQueue[0].episode)
        assertEquals(3, toQueue[1].episode)
    }
}
