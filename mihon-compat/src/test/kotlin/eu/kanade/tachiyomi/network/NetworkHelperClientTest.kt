package eu.kanade.tachiyomi.network

import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.brotli.BrotliInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    private fun clientFor(dohProvider: Int): okhttp3.OkHttpClient {
        val directory = File(
            System.getProperty("java.io.tmpdir"),
            "kodastream-network-doh-${System.nanoTime()}",
        )
        try {
            return mihonClientBuilder(
                cacheDirectory = directory,
                cookieJar = CookieJar.NO_COOKIES,
                userAgent = { "KodaStream test" },
                dohProvider = dohProvider,
            ).build()
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun dnsIsTheSystemOneUnlessAProviderIsPicked() {
        assertSame(Dns.SYSTEM, clientFor(-1).dns)
        assertSame(Dns.SYSTEM, clientFor(999).dns)
    }

    @Test
    fun everyProviderReplacesTheDnsAndKeepsTheBaseInterceptors() {
        val providers = listOf(
            PREF_DOH_CLOUDFLARE, PREF_DOH_GOOGLE, PREF_DOH_ADGUARD, PREF_DOH_QUAD9,
            PREF_DOH_ALIDNS, PREF_DOH_DNSPOD, PREF_DOH_360, PREF_DOH_QUAD101,
            PREF_DOH_MULLVAD, PREF_DOH_CONTROLD, PREF_DOH_NJALLA, PREF_DOH_SHECAN,
        )
        assertEquals(12, providers.toSet().size)
        val base = clientFor(-1).interceptors.size
        for (id in providers) {
            val client = clientFor(id)
            assertNotSame("provider $id", Dns.SYSTEM, client.dns)
            assertTrue("provider $id", client.interceptors.size == base)
        }
    }
}
