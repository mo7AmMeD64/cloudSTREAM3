package com.kodastream.app.plugins

import com.lagradost.cloudstream3.plugins.RepositoryManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryUrlPolicyTest {

    @Test
    fun extensionDeliveryRequiresHttps() {
        assertTrue(RepositoryManager.isSecureUrl("https://example.com/repo.json"))
        assertTrue(RepositoryManager.isSecureUrl("  HTTPS://example.com/plugin.cs3 "))
        assertFalse(RepositoryManager.isSecureUrl("http://example.com/plugin.cs3"))
        assertFalse(RepositoryManager.isSecureUrl("ftp://example.com/plugin.cs3"))
    }

    @Test
    fun directHttpRepositoryUrlIsRejected() = runBlocking {
        assertNull(RepositoryManager.parseRepoUrl("http://example.com/repo.json"))
    }
}
