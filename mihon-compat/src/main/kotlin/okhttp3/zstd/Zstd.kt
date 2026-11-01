package okhttp3.zstd

import okhttp3.CompressionInterceptor
import okio.BufferedSource
import okio.Source
import java.io.IOException

/**
 * OkHttp 5 defines this class and extensions pass it to [CompressionInterceptor]. No
 * Android-safe pure-Java decoder was available (aircompressor needs `sun.misc.Unsafe`,
 * unzstd needs API 33 VarHandle), so it is never advertised and never decoded.
 */
object Zstd : CompressionInterceptor.DecompressionAlgorithm {
    override val encoding: String get() = "zstd"

    override val supported: Boolean get() = false

    override fun decompress(compressedSource: BufferedSource): Source =
        throw IOException("Zstandard decoding is not supported by this build")
}
