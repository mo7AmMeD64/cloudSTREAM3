package com.kodastream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesBackupTest {

    private fun item(
        url: String = "https://example.com/item/1",
        name: String = "Test Movie",
        addedAt: Long = 1000L,
    ) = LibraryStore.LibraryItem(
        apiName = "TestProvider",
        url = url,
        name = name,
        posterUrl = "https://example.com/poster.jpg",
        posterHeaders = mapOf("Referer" to "https://example.com"),
        score10 = 8.5f,
        addedAt = addedAt,
    )

    @Test
    fun favoritesRoundTrip() {
        val items = listOf(item(), item(url = "https://example.com/item/2", addedAt = 2000L))

        val decoded = FavoritesBackup.decodeFavorites(FavoritesBackup.encodeFavorites(items))

        assertEquals(items, decoded)
    }

    @Test
    fun emptyListRoundTrip() {
        assertTrue(
            FavoritesBackup.decodeFavorites(FavoritesBackup.encodeFavorites(emptyList())).isEmpty(),
        )
    }
}
