package okhttp3.brotli

import okhttp3.CompressionInterceptor
import okio.BufferedSource
import okio.Source
import okio.source
import org.brotli.dec.BrotliInputStream

object Brotli : CompressionInterceptor.DecompressionAlgorithm {
    override val encoding: String get() = "br"

    override fun decompress(compressedSource: BufferedSource): Source =
        BrotliInputStream(compressedSource.inputStream()).source()
}
