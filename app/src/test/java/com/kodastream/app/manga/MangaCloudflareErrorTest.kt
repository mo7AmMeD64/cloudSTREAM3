package com.kodastream.app.manga

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaCloudflareErrorTest {
    @Test
    fun interceptorGivingUpIsABlock() {
        // The interceptor wraps its own exception in an IOException.
        val error = IOException("Could not pass the Cloudflare check", CloudflareBypassException())
        assertTrue(isCloudflareBlock(error))
    }

    @Test
    fun blockIsFoundDeepInTheCauseChain() {
        val error = IllegalStateException("outer", RuntimeException("mid", CloudflareBypassException()))
        assertTrue(isCloudflareBlock(error))
    }

    @Test
    fun forbiddenAndUnavailableAreBlocks() {
        // After a failed bypass the interceptor answers fast with the site's own 403.
        assertTrue(isCloudflareBlock(HttpException(403)))
        assertTrue(isCloudflareBlock(IOException("x", HttpException(503))))
    }

    @Test
    fun otherFailuresAreNot() {
        assertFalse(isCloudflareBlock(HttpException(404)))
        assertFalse(isCloudflareBlock(HttpException(500)))
        assertFalse(isCloudflareBlock(IOException("timeout")))
    }

    @Test
    fun aCauseLoopDoesNotHang() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        assertFalse(isCloudflareBlock(a))
    }
}
