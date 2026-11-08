package okhttp3

import okhttp3.brotli.Brotli
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.zstd.Zstd
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class CompressionInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun advertisesAllAlgorithms() {
        var seen: String? = null
        val client = captureClient { seen = it }

        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(request()).execute().use { it.body!!.string() }

        assertEquals("gzip, br, zstd", seen)
    }

    @Test
    fun keepsExistingAcceptEncoding() {
        var seen: String? = null
        val client = captureClient { seen = it }

        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(
            Request.Builder()
                .url(server.url("/"))
                .header("Accept-Encoding", "identity")
                .build(),
        ).execute().use { it.body!!.string() }

        assertEquals("identity", seen)
    }

    @Test
    fun emptyAlgorithmsAddNothing() {
        var seen: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(CompressionInterceptor())
            .addInterceptor { chain ->
                seen = chain.request().header("Accept-Encoding")
                chain.proceed(chain.request())
            }
            .build()

        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(request()).execute().use { it.body!!.string() }

        assertNull(seen)
    }

    @Test
    fun decompressesGzipAndDropsLengthHeaders() {
        val compressed = gzip("hello gzip".toByteArray())
        server.enqueue(
            MockResponse()
                .setHeader("Content-Encoding", "gzip")
                .setBody(Buffer().write(compressed)),
        )

        client().newCall(request()).execute().use { response ->
            assertEquals("hello gzip", response.body!!.string())
            assertNull(response.header("Content-Encoding"))
            assertNull(response.header("Content-Length"))
            assertEquals(-1L, response.body!!.contentLength())
        }
    }

    @Test
    fun decompressesBrotli() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Encoding", "br")
                .setBody(Buffer().write(BROTLI_FIXTURE)),
        )

        client().newCall(request()).execute().use { response ->
            assertEquals("hello brotli", response.body!!.string())
            assertNull(response.header("Content-Encoding"))
        }
    }

    @Test
    fun leavesUnknownEncodingUntouched() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Encoding", "xz")
                .setBody("plain"),
        )

        client().newCall(request()).execute().use { response ->
            assertEquals("plain", response.body!!.string())
            assertEquals("xz", response.header("Content-Encoding"))
        }
    }

    @Test
    fun leavesHeadUntouched() {
        server.enqueue(MockResponse().setHeader("Content-Encoding", "gzip"))

        client().newCall(request().newBuilder().head().build()).execute().use { response ->
            assertEquals("", response.body!!.string())
            assertEquals("gzip", response.header("Content-Encoding"))
        }
    }

    private fun captureClient(capture: (String?) -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(CompressionInterceptor(Gzip, Brotli, Zstd))
            .addInterceptor { chain ->
                capture(chain.request().header("Accept-Encoding"))
                chain.proceed(chain.request())
            }
            .build()

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(CompressionInterceptor(Gzip, Brotli, Zstd))
        .build()

    private fun request(): Request = Request.Builder().url(server.url("/")).build()

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    private companion object {
        val BROTLI_FIXTURE = byteArrayOf(
            0x8f.toByte(), 0x05, 0x80.toByte(), 0x68, 0x65, 0x6c, 0x6c, 0x6f,
            0x20, 0x62, 0x72, 0x6f, 0x74, 0x6c, 0x69, 0x03,
        )
    }
}
