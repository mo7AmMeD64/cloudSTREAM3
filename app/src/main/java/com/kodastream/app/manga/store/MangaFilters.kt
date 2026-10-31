package com.kodastream.app.manga.store

/** Pure filters used by the Extensions screen and by tests. */
object MangaFilters {

    fun filterLanguages(
        extensions: List<AvailableExtension>,
        languages: Set<String>,
        allLanguages: Boolean,
    ): List<AvailableExtension> {
        if (allLanguages || languages.isEmpty()) return extensions
        return extensions.filter { it.lang == "all" || it.lang in languages }
    }

    fun filterContentWarning(
        extensions: List<AvailableExtension>,
        showNsfw: Boolean,
    ): List<AvailableExtension> {
        return if (showNsfw) extensions else extensions.filter { it.contentWarning != ContentWarning.NSFW }
    }

    fun search(extensions: List<AvailableExtension>, query: String): List<AvailableExtension> {
        val needle = query.trim()
        if (needle.isEmpty()) return extensions
        return extensions.filter {
            it.name.contains(needle, ignoreCase = true) ||
                it.pkgName.contains(needle, ignoreCase = true) ||
                it.sources.any { source -> source.name.contains(needle, ignoreCase = true) }
        }
    }

    fun filter(
        extensions: List<AvailableExtension>,
        languages: Set<String>,
        allLanguages: Boolean,
        showNsfw: Boolean,
        query: String,
    ): List<AvailableExtension> = search(
        filterContentWarning(
            filterLanguages(extensions, languages, allLanguages),
            showNsfw,
        ),
        query,
    )

    /**
     * The newest listing of [pkgName] from a store whose signing key signed the installed
     * APK. Listings from any other store cannot replace the installed file.
     */
    fun findListing(
        pkgName: String,
        available: List<AvailableExtension>,
        signatures: Collection<String>,
    ): AvailableExtension? {
        return available
            .filter { it.pkgName == pkgName && signatures.isNotEmpty() && it.storeSigningKey in signatures }
            .maxWithOrNull(compareBy<AvailableExtension> { it.versionCode }.thenBy { it.libVersion ?: 0.0 })
    }

    fun findAnyListing(pkgName: String, available: List<AvailableExtension>): AvailableExtension? {
        return available
            .filter { it.pkgName == pkgName }
            .maxWithOrNull(compareBy<AvailableExtension> { it.versionCode }.thenBy { it.libVersion ?: 0.0 })
    }

    fun hasUpdate(
        installedVersionCode: Long,
        installedLibVersion: Double?,
        listing: AvailableExtension?,
    ): Boolean {
        if (listing == null) return false
        return listing.versionCode > installedVersionCode ||
            (installedLibVersion != null && (listing.libVersion ?: 0.0) > installedLibVersion)
    }

    fun isInstalled(pkgName: String, installed: Collection<String>): Boolean =
        pkgName in installed
}
