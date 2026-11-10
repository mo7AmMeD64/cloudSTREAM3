package eu.kanade.tachiyomi

/**
 * Values extensions read from the host app. [MangaEngine] fills them from the installed
 * package before any extension is loaded.
 */
@Suppress("UNUSED")
object AppInfo {
    @Volatile
    internal var versionCode: Int = 0

    @Volatile
    internal var versionName: String = ""

    /** @since extension-lib 1.3 */
    fun getVersionCode(): Int = versionCode

    /** @since extension-lib 1.3 */
    fun getVersionName(): String = versionName

    /** @since extension-lib 1.5 */
    fun getSupportedImageMimeTypes(): List<String> = IMAGE_MIME_TYPES

    // Same list and order as Mihon's ImageUtil.ImageType.
    private val IMAGE_MIME_TYPES = listOf(
        "image/avif",
        "image/gif",
        "image/heif",
        "image/jpeg",
        "image/jxl",
        "image/png",
        "image/webp",
        "image/jp2",
        "image/jpx",
    )
}
