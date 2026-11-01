package okhttp3

import okhttp3.brotli.Brotli
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.zstd.Zstd
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
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
    fun advertisesOnlySupportedEncodings() {
        var seen: String? = null
        val client = captureClient { seen = it }

        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(request()).execute().use { it.body!!.string() }

        assertEquals("gzip, br", seen)
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
    fun doesNotAdvertiseWithRange() {
        var seen: String? = null
        val client = captureClient { seen = it }

        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(
            Request.Builder()
                .url(server.url("/"))
                .header("Range", "bytes=0-9")
                .build(),
        ).execute().use { it.body!!.string() }

        assertNull(seen)
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
    fun leavesUnsupportedZstdUntouched() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Encoding", "zstd")
                .setBody(Buffer().write(ZSTD_FIXTURE)),
        )

        client().newCall(request()).execute().use { response ->
            assertArrayEquals(ZSTD_FIXTURE, response.body!!.bytes())
            assertEquals("zstd", response.header("Content-Encoding"))
        }
    }

    @Test
    fun leavesEmptyBodyUntouched() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Encoding", "gzip")
                .setBody(""),
        )

        client().newCall(request()).execute().use { response ->
            assertEquals("", response.body!!.string())
            assertEquals("gzip", response.header("Content-Encoding"))
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

    @Test
    fun leaves204Untouched() {
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
                .setHeader("Content-Encoding", "gzip"),
        )

        client().newCall(request()).execute().use { response ->
            assertEquals(204, response.code)
            assertEquals("gzip", response.header("Content-Encoding"))
        }
    }

    @Test
    fun leaves304Untouched() {
        server.enqueue(
            MockResponse()
                .setResponseCode(304)
                .setHeader("Content-Encoding", "gzip"),
        )

        client().newCall(request()).execute().use { response ->
            assertEquals(304, response.code)
            assertEquals("gzip", response.header("Content-Encoding"))
        }
    }

    @Test
    fun closesOriginalBodyWhenDecoderFails() {
        var closed = false
        val failing = object : CompressionInterceptor.DecompressionAlgorithm {
            override val encoding: String get() = "boom"

            override fun decompress(compressedSource: BufferedSource): Source =
                throw IOException("cannot decode")
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(CompressionInterceptor(failing))
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val original = response.body!!
                val source = object : ForwardingSource(original.source()) {
                    override fun close() {
                        closed = true
                        super.close()
                    }
                }.buffer()
                val wrapped = object : ResponseBody() {
                    override fun contentType(): MediaType? = original.contentType()

                    override fun contentLength(): Long = original.contentLength()

                    override fun source(): BufferedSource = source
                }
                response.newBuilder().body(wrapped).build()
            }
            .build()

        server.enqueue(
            MockResponse()
                .setHeader("Content-Encoding", "boom")
                .setBody("body"),
        )

        try {
            client.newCall(request()).execute()
            fail("Expected an IOException")
        } catch (expected: IOException) {
            assertTrue(closed)
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
        val ZSTD_FIXTURE = byteArrayOf(
            0x28, 0xb5.toByte(), 0x2f, 0xfd.toByte(), 0x04, 0x58, 0x51, 0x00,
            0x00, 0x68, 0x65, 0x6c, 0x6c, 0x6f, 0x20, 0x7a, 0x73, 0x74,
            0x64, 0xcf.toByte(), 0xdb.toByte(), 0x60, 0x9c.toByte(),
        )
    }
}
