package eu.kanade.tachiyomi.network

import okhttp3.CookieJar
import okhttp3.brotli.BrotliInterceptor
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class NetworkHelperClientTest {
    @Test
    fun defaultClientHasNoBrotliInterceptor() {
        val directory = File(
            System.getProperty("java.io.tmpdir"),
            "kodastream-network-${System.nanoTime()}",
        )
        try {
            val client = mihonClientBuilder(
                cacheDirectory = directory,
                cookieJar = CookieJar.NO_COOKIES,
                userAgent = { "KodaStream test" },
            ).build()

            assertFalse(client.interceptors.any { it is BrotliInterceptor })
        } finally {
            directory.deleteRecursively()
        }
    }
}
