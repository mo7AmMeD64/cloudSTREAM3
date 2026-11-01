package okhttp3

import okio.BufferedSource
import okio.Source
import okio.buffer
import java.io.IOException

/**
 * Transparent compressed response support for the OkHttp 5 API that extensions link
 * against. OkHttp itself stays at 4.12.0.
 *
 * When the request has neither `Accept-Encoding` nor `Range`, the supported encodings are
 * advertised. A known `Content-Encoding` on a response with a body is decoded and the
 * length headers are removed, matching OkHttp 5.
 */
open class CompressionInterceptor(
    vararg val algorithms: DecompressionAlgorithm,
) : Interceptor {

    private val supported = algorithms.filter { it.supported }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val advertise = supported.isNotEmpty() &&
            request.header("Accept-Encoding") == null &&
            request.header("Range") == null
        val outgoing = if (advertise) {
            request.newBuilder()
                .header("Accept-Encoding", supported.joinToString(", ") { it.encoding })
                .build()
        } else {
            request
        }

        return decompress(chain.proceed(outgoing))
    }

    private fun decompress(response: Response): Response {
        if (!response.promisesBody()) return response
        val body = response.body ?: return response
        if (body.contentLength() == 0L) return response
        val encoding = response.header("Content-Encoding") ?: return response
        val algorithm = supported.firstOrNull {
            it.encoding.equals(encoding, ignoreCase = true)
        } ?: return response

        val decompressed = try {
            algorithm.decompress(body.source()).buffer()
        } catch (e: IOException) {
            body.close()
            throw e
        }

        return response.newBuilder()
            .removeHeader("Content-Encoding")
            .removeHeader("Content-Length")
            .body(DecompressedBody(body.contentType(), decompressed))
            .build()
    }

    private fun Response.promisesBody(): Boolean {
        if (request.method == "HEAD") return false
        return code !in 100..199 && code != 204 && code != 304
    }

    private class DecompressedBody(
        private val type: MediaType?,
        private val source: BufferedSource,
    ) : ResponseBody() {
        override fun contentType(): MediaType? = type

        override fun contentLength(): Long = -1L

        override fun source(): BufferedSource = source
    }

    interface DecompressionAlgorithm {
        val encoding: String

        /** False for encodings this build cannot decode; they are never advertised. */
        val supported: Boolean get() = true

        fun decompress(compressedSource: BufferedSource): Source
    }
}
