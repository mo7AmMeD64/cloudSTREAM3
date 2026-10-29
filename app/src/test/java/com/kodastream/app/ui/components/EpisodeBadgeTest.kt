package com.kodastream.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeBadgeTest {
    @Test
    fun moviesWithZeroesGetNoBadge() {
        assertNull(episodeBadge(0, 0))
    }

    @Test
    fun missingValuesGetNoBadge() {
        assertNull(episodeBadge(null, null))
    }

    @Test
    fun onlyAnEpisodeShowsJustTheEpisode() {
        assertEquals(EpisodeBadge.EpisodeOnly(5), episodeBadge(null, 5))
        assertEquals(EpisodeBadge.EpisodeOnly(5), episodeBadge(0, 5))
    }

    @Test
    fun seasonAndEpisodeShowBoth() {
        assertEquals(EpisodeBadge.SeasonEpisode(2, 5), episodeBadge(2, 5))
    }

    @Test
    fun negativesCountAsMissing() {
        assertNull(episodeBadge(-1, -1))
        assertEquals(EpisodeBadge.EpisodeOnly(5), episodeBadge(-1, 5))
        assertNull(episodeBadge(2, -1))
    }

    @Test
    fun aSeasonAloneGetsNoBadge() {
        assertNull(episodeBadge(2, null))
        assertNull(episodeBadge(2, 0))
    }
}
