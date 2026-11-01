package okhttp3

import okio.BufferedSource
import okio.GzipSource
import okio.Source

object Gzip : CompressionInterceptor.DecompressionAlgorithm {
    override val encoding: String get() = "gzip"

    override fun decompress(compressedSource: BufferedSource): Source = GzipSource(compressedSource)
}
