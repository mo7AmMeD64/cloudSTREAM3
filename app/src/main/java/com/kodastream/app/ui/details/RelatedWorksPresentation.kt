package com.kodastream.app.ui.details

import com.lagradost.cloudstream3.SearchResponse
import java.util.Locale
import java.text.Normalizer

/** Metadata is loaded through the same source that supplied the recommendation. */
internal data class RelatedCandidate(
    val item: SearchResponse,
    val tags: List<String>,
    val verifiedUpcoming: Boolean = false,
)

private fun relatedIdentity(item: SearchResponse, fallbackApiName: String): String =
    (item.apiName.ifBlank { fallbackApiName }) + "|" + normalizeUrlKey(item.url)


/**
 * Same-franchise releases are identified from their title and do not need a
 * provider load just to prove what the title already says. Keep the small metadata
 * budget for non-franchise candidates, whose genre tags are needed to verify that
 * they are genuinely similar. Previously franchise matches consumed this budget
 * and could starve every related-by-genre result on some providers.
 */
internal const val RELATED_ENRICH_LIMIT = 4

/**
 * Search is a continuation-discovery operation, not a generic fallback for an
 * empty related list. Require a positive franchise signal (an explicit part
 * marker or an already-confirmed same-franchise recommendation), then stop once
 * the bounded franchise lane is full. Genre suggestions are verified only from
 * provider-supplied recommendations and never justify an exact-title search.
 */
internal fun shouldSearchRelatedCandidates(
    franchiseRecommendationCount: Int,
    hasExplicitPartMarker: Boolean,
    hasSubtitleFranchisePrefix: Boolean = false,
    allowRootTitleSearch: Boolean = false,
): Boolean {
    // Root-title discovery is explicitly enabled only for movies and anime by
    // the caller. Search results still pass the strict franchise matcher before
    // they can enter the rail, so a broad provider response cannot become a relation.
    val hasFranchiseSignal = hasExplicitPartMarker || hasSubtitleFranchisePrefix ||
        franchiseRecommendationCount > 0 || allowRootTitleSearch
    return hasFranchiseSignal && franchiseRecommendationCount < FRANCHISE_EXTRA_LIMIT
}

/**
 * Extract a franchise name from a title that has a clear subtitle delimiter.
 * Example: "Pirates of the Caribbean: Dead Man's Chest" -> "Pirates of the Caribbean".
 * This is deliberately not applied to ordinary spaces or hyphens (which are common
 * inside real titles), and short/generic prefixes are rejected to avoid broad searches.
 */
internal fun franchisePrefixQuery(title: String): String? {
    val delimiter = Regex("\\s*[:：–—]\\s*")
    val match = delimiter.find(title) ?: return null
    val prefix = title.substring(0, match.range.first).trim().trim('\'', '"', '،', ',', '-', '–', '—')
    val meaningfulTokens = titleTokenSequence(prefix)
    if (meaningfulTokens.size < 2) return null
    if (meaningfulTokens.joinToString(" ").length < 8) return null
    return prefix.takeIf { it.isNotBlank() }
}

/** No metadata loads can verify genre similarity without canonical current tags. */
internal fun hasComparableGenreTags(currentTags: List<String>): Boolean =
    currentTags.any { normalizeGenreTags(it).isNotEmpty() }

internal fun candidatesRequiringRelatedMetadata(
    currentTitle: String,
    candidates: List<SearchResponse>,
    limit: Int = RELATED_ENRICH_LIMIT,
): List<SearchResponse> = candidates.asSequence()
    .filterNot { looksLikeSameFranchise(currentTitle, it.name) }
    .take(limit.coerceAtLeast(0))
    .toList()

/**
 * Split the bounded metadata budget between provider recommendations and fallback
 * search results. Without this, twelve untagged recommendations could occupy every
 * slot and prevent any later search result from ever receiving genre metadata.
 * If one lane is short, the other lane fills the unused slots. Identity is source+URL.
 */
internal fun candidatesForRelatedMetadata(
    currentTitle: String,
    recommendations: List<SearchResponse>,
    searchResults: List<SearchResponse>,
    limit: Int = RELATED_ENRICH_LIMIT,
): List<SearchResponse> {
    val cap = limit.coerceAtLeast(0)
    if (cap == 0) return emptyList()
    fun eligible(items: List<SearchResponse>) = items.asSequence()
        .filterNot { looksLikeSameFranchise(currentTitle, it.name) }
        .filter { it.url.isNotBlank() }
        .distinctBy { it.apiName to normalizeUrlKey(it.url) }
        .toList()

    val recommended = eligible(recommendations)
    val searched = eligible(searchResults).filterNot { candidate ->
        recommended.any { it.apiName == candidate.apiName && normalizeUrlKey(it.url) == normalizeUrlKey(candidate.url) }
    }
    if (searched.isEmpty()) return recommended.take(cap)
    if (recommended.isEmpty()) return searched.take(cap)

    val recommendationQuota = (cap + 1) / 2
    val first = recommended.take(recommendationQuota)
    val second = searched.take(cap - first.size)
    val remaining = cap - first.size - second.size
    return first + second + recommended.drop(first.size).take(remaining)
}

internal data class RelatedWorkRows(
    val franchise: List<SearchResponse> = emptyList(),
    val similarByTags: List<SearchResponse> = emptyList(),
)

private val titleNoise = setOf(
    "the", "a", "an", "and", "or", "of", "to", "for", "in", "on", "at",
    "movie", "film", "series", "season", "part", "chapter", "episode", "special", "ova", "anime",
    "remake", "reboot", "sequel", "prequel", "نسخة", "فيلم", "مسلسل", "موسم", "جزء", "الجزء", "خاص", "انمي",
)

private fun normalizeTitleToken(token: String): String = Normalizer.normalize(token, Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
    .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي')
    .removePrefix("ال")

private fun titleTokenSequence(title: String): List<String> = title
    .lowercase(Locale.ROOT)
    .split(Regex("[^\\p{L}\\p{N}]+"))
    .map(::normalizeTitleToken)
    // Two-character words are retained only for phrase matching (e.g. Arabic
    // transliterations of Latin names); isolated short tokens never establish a franchise.
    .filter { it.length >= 2 && it !in titleNoise && it.any(Char::isLetter) }

private fun titleTokens(title: String): Set<String> = titleTokenSequence(title)
    .filter { it.length >= 3 }
    .toSet()

/** Preserve otherwise-discarded words for detecting explicit reissues/remakes. */
private fun rawTitleTokens(title: String): List<String> = title
    .lowercase(Locale.ROOT)
    .split(Regex("[^\\p{L}\\p{N}]+"))
    .map(::normalizeTitleToken)
    .filter(String::isNotBlank)

/**
 * One canonical key for a provider url. Providers hand out the same page with and
 * without a trailing slash, with different casing in the host, or with a tracking
 * fragment. Comparing raw strings made the app treat one title as two: the
 * duplicate-navigation guard silently swallowed the tap, and the same page could
 * appear twice in a rail.
 */
internal fun normalizeUrlKey(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return ""

    val trackingKeys = setOf(
        "fbclid", "gclid", "dclid", "yclid", "mc_cid", "mc_eid", "igshid", "_ga", "_gl",
        "ref_src", "ref_url", "si",
    )
    // Query parameters and route-like fragments can be content identity in provider
    // URLs (#/movie/123, #/title?id=7). Remove only common presentation anchors and
    // tracking fragments; blindly dropping every fragment can merge two works.
    val rawFragment = trimmed.substringAfter('#', missingDelimiterValue = "")
    val normalizedFragment = rawFragment.lowercase(Locale.ROOT)
    val presentationFragments = setOf("details", "overview", "top", "bottom", "info", "comments", "description")
    val identityFragment = rawFragment.takeIf {
        it.isNotBlank() && normalizedFragment !in presentationFragments &&
            !normalizedFragment.startsWith("utm_") && normalizedFragment !in trackingKeys
    }
    val withoutFragment = trimmed.substringBefore('#')
    val authorityAndRest = withoutFragment.substringAfter("://", withoutFragment)
    val authorityEnd = authorityAndRest.indexOfFirst { it == '/' || it == '?' }
        .let { if (it < 0) authorityAndRest.length else it }
    val authority = authorityAndRest.substring(0, authorityEnd)
    val pathAndQuery = authorityAndRest.substring(authorityEnd)
    val host = authority.lowercase(Locale.ROOT)
    val path = pathAndQuery.substringBefore('?').trimEnd('/')
    val rawQuery = pathAndQuery.substringAfter('?', missingDelimiterValue = "")

    // Strip known analytics/tracking parameters only. Retain every other parameter
    // (including repeated values and their order) because providers often encode the
    // content id in the query string. Parameter names are case-insensitive by convention.
    val query = rawQuery.split('&').filter { it.isNotBlank() }.filterNot { parameter ->
        val key = parameter.substringBefore('=').lowercase(Locale.ROOT)
        key.startsWith("utm_") || key in trackingKeys
    }.joinToString("&")

    val identity = buildString {
        append(host)
        append(path)
        if (query.isNotEmpty()) append('?').append(query)
        if (identityFragment != null) append('#').append(identityFragment)
    }
    // Keep odd/non-URL provider strings stable instead of throwing during rendering.
    return identity.ifEmpty { withoutFragment.trimEnd('/') }
}

/**
 * Words that introduce a numbered part of the same work. Providers split one
 * title into "Part 1 / Part 2", Arabic ordinal part labels, "Season 2",
 * "Release 3", "Cour 2" or "Vol. 2" and publish each part as its own page. Those
 * parts belong in the title's timeline exactly like seasons do, so they have to
 * be recognised as the same franchise instead of being treated as unrelated works.
 */
private val seasonMarkers = setOf("season", "s", "موسم")
private val courMarkers = setOf("cour", "كور")
private val partMarkers = setOf(
    "part", "pt", "volume", "vol", "release", "chapter", "book", "arc", "movie",
    "جزء", "اصدار", "بارت", "حلقه", "ملف", "فيلم", "كتاب", "قوس",
)
private val allTimelineMarkers = seasonMarkers + courMarkers + partMarkers

private val ordinalNumbers = mapOf(
    "اول" to 1, "اولي" to 1, "اولى" to 1,
    "ثاني" to 2, "ثانيه" to 2, "ثانية" to 2,
    "ثالث" to 3, "ثالثه" to 3, "ثالثة" to 3,
    "رابع" to 4, "رابعه" to 4, "رابعة" to 4,
    "خامس" to 5, "خامسه" to 5, "خامسة" to 5,
    "سادس" to 6, "سادسه" to 6, "سادسة" to 6,
    "سابع" to 7, "سابعه" to 7, "سابعة" to 7,
    "ثامن" to 8, "ثامنه" to 8, "ثامنة" to 8,
    "تاسع" to 9, "تاسعه" to 9, "تاسعة" to 9,
    "عاشر" to 10, "عاشره" to 10, "عاشرة" to 10,
    "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
    "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9, "tenth" to 10,
    "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
    "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
    "i" to 1, "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5,
    "vi" to 6, "vii" to 7, "viii" to 8, "ix" to 9, "x" to 10,
    "final" to 99, "last" to 99, "اخير" to 99, "اخيره" to 99, "اخيرة" to 99,
)

/** A title split into its base name and an optional numbered part marker. */
internal data class TitleParts(
    val base: List<String>,
    /** Right-most marker retained for older callers and diagnostics. */
    val markerKind: String? = null,
    val markerNumber: Int? = null,
    /** Season, part and cour are independent axes; never conflate their numbers. */
    val seasonNumber: Int? = null,
    val partNumber: Int? = null,
    val courNumber: Int? = null,
    /** Stable family key used to keep Part 1/2 inside the same titled season. */
    val partGroupKey: String? = null,
)

/** Parse every Unicode decimal digit supported by providers, including Arabic-Indic digits. */
private fun decimalNumberOf(token: String): Int? {
    if (token.isEmpty()) return null
    val ascii = buildString(token.length) {
        token.forEach { character ->
            val digit = character.digitToIntOrNull(10) ?: return null
            append(('0'.code + digit).toChar())
        }
    }
    return ascii.toIntOrNull()
}

private fun markerNumberOf(token: String): Int? =
    decimalNumberOf(token)
        ?: ordinalNumbers[token]
        ?: Regex("^(\\d{1,3})(?:st|nd|rd|th)$").matchEntire(token)?.groupValues?.get(1)?.toIntOrNull()

internal fun parseTitleParts(title: String): TitleParts {
    val raw = title.lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .map(::normalizeTitleToken)
        .filter { it.isNotEmpty() }
    if (raw.isEmpty()) return TitleParts(emptyList())

    var markerKind: String? = null
    var markerNumber: Int? = null
    var seasonNumber: Int? = null
    var partNumber: Int? = null
    var courNumber: Int? = null
    var cut = raw.size

    // Walk from right to left so compound labels such as "Season 3 Part 2" keep
    // both axes. The first marker found remains the legacy/right-most marker.
    for (index in raw.indices.reversed()) {
        if (index >= cut) continue
        val token = raw[index]
        val glued = Regex("^([\\p{L}]{1,})(\\d{1,3})$").find(token)
        val kind = when {
            glued != null -> glued.groupValues[1].takeIf { it in allTimelineMarkers }
            token in allTimelineMarkers -> token
            else -> null
        } ?: continue

        val number = glued?.groupValues?.get(2)?.let(::decimalNumberOf)
            ?: raw.getOrNull(index + 1)?.let(::markerNumberOf)
            ?: if (kind in seasonMarkers && index > 0) markerNumberOf(raw[index - 1]) else null
        if (number != null) {
            if (markerKind == null) {
                markerKind = kind
                markerNumber = number
            }
            when (kind) {
                in seasonMarkers -> if (seasonNumber == null) seasonNumber = number
                in courMarkers -> if (courNumber == null) courNumber = number
                else -> if (partNumber == null) partNumber = number
            }
            val markerStart = if (kind in seasonMarkers && index > 0 && markerNumberOf(raw[index - 1]) != null) {
                index - 1
            } else index
            cut = minOf(cut, markerStart)
        }
    }

    // A bare trailing number is useful as a low-confidence sibling-search hint
    // ("Dune 2"), but it is deliberately NOT a Part number: titles such as
    // "Catch-22" and "World War 2" must not be labelled as Part 22 / Part 2.
    if (markerKind == null && raw.size >= 2) {
        val last = raw.last()
        val isHyphenatedNumberTitle = Regex(".*[-–—]\\s*\\d{1,3}\\s*$").matches(title.trim())
        if (!isHyphenatedNumberTitle && last.length in 1..3 && last.all { it.digitToIntOrNull() != null }) {
            val number = decimalNumberOf(last)
            val base = raw.dropLast(1).filter { it !in allTimelineMarkers && it !in titleNoise }
            if (number != null && number > 0 && base.isNotEmpty()) {
                markerKind = "number"
                markerNumber = number
                cut = raw.size - 1
            }
        }
    }

    val base = raw.take(cut).filter { it !in allTimelineMarkers && it !in titleNoise }
        .ifEmpty { raw.take(cut) }
    val groupKey = base.joinToString(" ").takeIf { it.isNotBlank() }?.let { root ->
        buildString {
            append(root)
            seasonNumber?.let { append("|season=").append(it) }
            courNumber?.let { append("|cour=").append(it) }
        }
    }
    return TitleParts(
        base = base,
        markerKind = markerKind,
        markerNumber = markerNumber,
        seasonNumber = seasonNumber,
        partNumber = partNumber,
        courNumber = courNumber,
        partGroupKey = groupKey,
    )
}

/**
 * Sort key for numbered parts/releases. Explicit Part markers always win. A bare
 * trailing number is accepted only as a weak release order when the title root's
 * final token is distinctive (>= 4 characters): this supports Dune 2 / Dune 10
 * without interpreting generic titles such as World War 2 / 3 as a franchise.
 */
private fun hasDistinctiveNumericRoot(parts: TitleParts): Boolean =
    parts.base.lastOrNull()?.let { it.length >= 4 && it !in titleNoise } == true

internal fun franchiseOrder(title: String): Int {
    val parsed = parseTitleParts(title)
    parsed.partNumber?.let { return it }
    return parsed.markerNumber.takeIf {
        parsed.markerKind == "number" && hasDistinctiveNumericRoot(parsed)
    } ?: 0
}

internal fun franchiseSeasonOrder(title: String): Int? = parseTitleParts(title).seasonNumber

internal fun franchiseCourOrder(title: String): Int? = parseTitleParts(title).courNumber

internal fun franchisePartGroupKey(title: String): String? = parseTitleParts(title).partGroupKey

/**
 * Conservative franchise heuristic, only applied to recommendations from the same
 * provider. Two rules, in order of confidence:
 *
 * 1. Same base title with a numbered part marker on either side. "Dune" and
 *    "Dune Part Two" are the same work split into releases, and so are
 *    Arabic ordinal part labels. This is what makes provider parts appear in
 *    the title timeline.
 * 2. The candidate's base starts with the current base ("Naruto" ->
 *    "Naruto Shippuden"), or at least 60% of the current title's meaningful
 *    words reappear in the candidate. A single shared word is only accepted
 *    for a one-word root and a leading match ("Naruto" -> "Naruto Shippuden");
 *    it is rejected when both titles contain multiple meaningful words.
 */
internal fun looksLikeSameFranchise(currentTitle: String, candidateTitle: String): Boolean {
    val current = parseTitleParts(currentTitle)
    val candidate = parseTitleParts(candidateTitle)
    val currentBase = current.base
    val candidateBase = candidate.base
    if (currentBase.isEmpty() || candidateBase.isEmpty()) return false

    if (currentBase == candidateBase) {
        // Preserve an explicit reissue/remake as a separate related work even when
        // the source title normalizes to the same root (e.g. One Piece -> The One
        // Piece or One Piece Remake). This does not turn it into a season/part.
        val currentRaw = rawTitleTokens(currentTitle)
        val candidateRaw = rawTitleTokens(candidateTitle)
        val editionCues = setOf("remake", "reboot", "reimagining", "redux", "remaster")
        val currentHasEditionCue = currentRaw.any { it in editionCues }
        val candidateHasEditionCue = candidateRaw.any { it in editionCues }
        if (currentHasEditionCue != candidateHasEditionCue) return true
        val articles = setOf("the", "a", "an")
        if (currentRaw != candidateRaw &&
            currentRaw.dropWhile { it in articles } == candidateRaw.dropWhile { it in articles }
        ) return true

        // A bare trailing number is weak evidence. Accept it as a sequel family
        // only when the root ends in a distinctive token (length >= 4), avoiding
        // accidental franchise creation from generic phrases such as World War.
        if (current.markerKind == "number" || candidate.markerKind == "number") {
            return hasDistinctiveNumericRoot(current) && hasDistinctiveNumericRoot(candidate)
        }
        // Identical base: only an explicit season/part marker makes it a different
        // entry of the same work. Two identical titles are not a franchise.
        return current.markerKind != null || candidate.markerKind != null
    }

    // A trailing bare number is weak title evidence, not a franchise identifier.
    // Do not let a shortened base absorb a different title: "World War 2" must
    // not claim "World War Z" merely because both begin with "World War".
    // Explicit Part/Season markers and full subtitle prefixes are handled separately.
    if ((current.markerKind == "number" || candidate.markerKind == "number") &&
        currentBase != candidateBase
    ) return false

    // Many film franchises use a shared subtitle prefix rather than a numeric
    // suffix (e.g. "Pirates of the Caribbean: ..."). Compare that prefix as a
    // whole phrase before the looser token-overlap heuristic below.
    val currentSubtitleBase = franchisePrefixQuery(currentTitle)?.let(::titleTokenSequence).orEmpty()
    val candidateSubtitleBase = franchisePrefixQuery(candidateTitle)?.let(::titleTokenSequence).orEmpty()
    if (currentSubtitleBase.size >= 2 && candidateBase.take(currentSubtitleBase.size) == currentSubtitleBase) return true
    if (candidateSubtitleBase.size >= 2 && currentBase.take(candidateSubtitleBase.size) == candidateSubtitleBase) return true
    if (currentSubtitleBase.size >= 2 && currentSubtitleBase == candidateSubtitleBase) return true

    // A shorter base that prefixes the longer one is a sequel/prequel of the same
    // work. Require a real word (>= 4 characters) so "Up" cannot claim "Uploaded".
    val shorter = if (currentBase.size <= candidateBase.size) currentBase else candidateBase
    val longer = if (currentBase.size <= candidateBase.size) candidateBase else currentBase
    if (shorter.size >= 1 && longer.take(shorter.size) == shorter) {
        val key = shorter.joinToString(" ")
        if (key.length >= 4) return true
    }

    val currentTokens = currentBase.filter { it.length >= 3 }.toSet()
    val candidateTokens = candidateBase.filter { it.length >= 3 }.toSet()
    if (currentTokens.isEmpty() || candidateTokens.isEmpty()) return false
    val shared = currentTokens intersect candidateTokens
    if (shared.isEmpty()) return false

    // Coverage, not just overlap: at least 60% of this title's meaningful words
    // must come back. "One Piece" vs "One Punch Man" shares one of two words
    // (50%) and is correctly rejected, while "Attack on Titan" vs "Attack on
    // Titan Final Season" shares both (100%).
    val coverage = shared.size.toFloat() / currentTokens.size
    if (shared.size >= 2 && coverage >= 0.6f) return true

    // A single shared word is only evidence when it is the *leading* word of
    // both titles, and it is long enough to be a real name rather than a
    // particle. There used to be a shortcut here that accepted any one-word
    // title sharing its only word with the candidate anywhere in the string,
    // which is exactly how unrelated works that merely start with the same word
    // ended up in the "More" rail (one-word Arabic titles are extremely
    // common, so the shortcut fired constantly).
    val onlyShared = shared.singleOrNull() ?: return false
    val isLeadingBoth = currentBase.firstOrNull() == onlyShared && candidateBase.firstOrNull() == onlyShared
    // A single shared leading word may connect a one-word root to its sequel
    // ("Naruto" -> "Naruto Shippuden"), but it is not enough when both titles
    // contain multiple meaningful words. This prevents "Pirates of the Caribbean"
    // from absorbing "Pirates of Silicon Valley" solely because both start with
    // the same first word.
    val oneTitleIsSingleWord = currentBase.size == 1 || candidateBase.size == 1
    return isLeadingBoth && onlyShared.length >= 5 && oneTitleIsSingleWord
}

private fun normalizeGenreText(raw: String): String = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
    .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي')
    .replace('_', ' ').replace('-', ' ')
    .replace(Regex("\\s+"), " ")

/**
 * Expands a source tag into canonical genres. A provider sometimes combines tags
 * (for example "Psychological / Mystery"), so a single raw tag may yield more than
 * one canonical value. Unknown, specific tags are retained for exact matching;
 * format labels such as "Anime" are deliberately ignored to avoid false matches.
 */
internal fun normalizeGenreTags(raw: String): Set<String> {
    val tag = normalizeGenreText(raw)
    if (tag.isBlank()) return emptySet()

    val result = linkedSetOf<String>()
    // Match complete words/phrases, not arbitrary substrings. Substring matching made
    // the Arabic word for "nature" accidentally match the short medical alias and polluted recommendations.
    fun words(value: String): List<String> = normalizeGenreText(value)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .split(' ')
        .filter(String::isNotBlank)
        // Arabic conjunctions are commonly attached to the next word ("and fantasy" written as one token).
        // Strip the conjunction before the definite article so compound tags normalize
        // identically whether the provider inserts spaces or punctuation.
        .map { token -> token.removePrefix("و").removePrefix("ال") }
    val tagWords = words(tag)
    fun hasAny(vararg values: String): Boolean = values.any { value ->
        val expected = words(value)
        expected.isNotEmpty() && tagWords.windowed(expected.size).any { it == expected }
    }

    // Specific genres are checked separately instead of using one broad fallback.
    val hasScienceFiction = hasAny("science fiction", "sci fi", "sci-fi", "scifi", "خيال علمي", "خيال-علمي")
    val hasExplicitFantasy = hasAny("fantasy", "fantastical", "فانتازيا")
    if (hasScienceFiction) result += "science-fiction"
    if (hasAny("shounen", "shonen", "شونن", "شونين")) result += "shounen"
    if (hasAny("shoujo", "shojo", "شوجو")) result += "shoujo"
    if (hasAny("seinen", "سينين")) result += "seinen"
    if (hasAny("josei", "جوسي")) result += "josei"
    if (hasAny("kodomomuke", "kodomo", "كودومو")) result += "kodomo"
    if (hasAny("supernatural", "ما وراء الطبيعة", "خارق للطبيعة", "خوارق الطبيعة")) result += "supernatural"
    if (hasAny("super power", "superpower", "super powers", "قوة خارقة", "قوى خارقة", "قدرات خارقة")) result += "superpower"
    if (hasAny("psychological", "mind game", "mind games", "mental", "نفسي", "نفسية", "عقلي", "العاب عقلية", "ألعاب عقلية")) result += "psychological"
    if (hasAny("detective", "investigation", "investigator", "crime solving", "police", "تحقيق", "تحري", "محقق", "بوليسي", "شرطة", "استقصاء")) result += "detective"
    if (hasAny("mystery", "mysterious", "whodunit", "غموض", "لغز", "ألغاز", "الغاز")) result += "mystery"
    if (hasAny("crime", "criminal", "criminals", "جرائم", "جريمة", "مجرم", "اجرام")) result += "crime"
    if (hasAny("adventure", "مغامرة", "مغامرات")) result += "adventure"
    if (hasAny("action", "action packed", "اكشن", "حركة", "قتال")) result += "action"
    if (hasAny("comedy", "comedies", "humor", "humour", "كوميدي", "مضحك", "هزلي", "كوميديا")) result += "comedy"
    if (hasAny("romance", "romantic", "love story", "رومانسي", "رومانسية", "عاطفي", "حب")) result += "romance"
    // Do not collapse an explicit combined tag such as "Science Fiction / Fantasy"
    // into science-fiction alone. The generic Arabic word for "fiction/imagination" is only fantasy
    // when it is not part of the more-specific Arabic "science fiction" phrase.
    if (hasExplicitFantasy || (hasAny("خيال") && !hasScienceFiction)) result += "fantasy"
    if (hasAny("mecha", "ميكا", "ميشا", "روبوتات عملاقة")) result += "mecha"
    if (hasAny("superhero", "super hero", "بطل خارق", "ابطال خارقون", "أبطال خارقون")) result += "superhero"
    val hasReverseHarem = hasAny("reverse harem", "حريم عكسي")
    if (hasReverseHarem) result += "reverse-harem" else if (hasAny("harem", "حريم")) result += "harem"
    if (hasAny("ecchi", "إيتشي", "ايتشي")) result += "ecchi"
    if (hasAny("parody", "محاكاة ساخرة", "سخرية")) result += "parody"
    if (hasAny("survival", "بقاء", "نجاة")) result += "survival"
    if (hasAny("tragedy", "مأساة", "مأساوي")) result += "tragedy"
    if (hasAny("samurai", "ساموراي")) result += "samurai"
    if (hasAny("ninja", "نينجا")) result += "ninja"
    if (hasAny("vampire", "مصاص دماء", "مصاصي الدماء")) result += "vampire"
    if (hasAny("demons", "demon", "شياطين", "شيطان")) result += "demon"
    if (hasAny("mythology", "اساطير", "أساطير", "ميثولوجيا")) result += "mythology"
    if (hasAny("space", "outer space", "فضاء", "فضائي")) result += "space"
    if (hasAny("medical", "medicine", "طبي", "طب")) result += "medical"
    if (hasAny("political", "politics", "سياسي", "سياسة")) result += "political"
    val hasMindGamePhrase = hasAny("mind game", "mind games", "العاب عقلية", "ألعاب عقلية")
    if (hasAny("video game", "video games", "لعبة", "العاب", "ألعاب") ||
        (hasAny("game", "games") && !hasMindGamePhrase)) result += "game"
    if (hasAny("idol", "ايدول", "آيدول")) result += "idol"
    if (hasAny("coming of age", "بلوغ", "النضج")) result += "coming-of-age"
    if (hasAny("food", "cooking", "culinary", "gourmet", "restaurant", "طعام", "طبخ", "اكل", "أكل", "مطبخ")) result += "food"
    if (hasAny("thriller", "suspense", "تشويق", "اثارة", "إثارة")) result += "thriller"
    if (hasAny("horror", "scary", "رعب", "مرعب")) result += "horror"
    if (hasAny("drama", "دراما", "درامي")) result += "drama"
    if (hasAny("sports", "sport", "رياضة", "رياضي")) result += "sports"
    if (hasAny("slice of life", "iyashikei", "شريحة من الحياة", "حياة يومية")) result += "slice-of-life"
    if (hasAny("historical", "history", "تاريخي", "تاريخ")) result += "historical"
    if (hasAny("military", "war", "حربي", "عسكري", "حرب")) result += "military"
    if (hasAny("music", "musical", "موسيقى", "موسيقي")) result += "music"
    if (hasAny("school", "مدرسي", "مدرسة", "مدارس")) result += "school"
    if (hasAny("martial arts", "فنون قتالية", "قتال يدوي")) result += "martial-arts"
    if (hasAny("isekai", "عالم اخر", "عالم آخر", "انتقال لعالم")) result += "isekai"

    if (result.isNotEmpty()) return result

    val cleaned = tag.replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
    if (cleaned in setOf(
            "anime", "animation", "انمي", "أنمي", "movie", "film", "series", "مسلسل", "فيلم",
            "tv", "ova", "ona", "special", "japan", "japanese", "korean", "chinese",
            "دبلجة", "مدبلج", "مترجم", "dub", "sub", "live action", "live-action"
        )) {
        return emptySet()
    }
    return if (cleaned.isBlank()) emptySet() else setOf(cleaned)
}

/** Backwards-compatible single-value helper for callers/tests that need one category. */
internal fun normalizeGenreTag(raw: String): String = normalizeGenreTags(raw).firstOrNull().orEmpty()

internal fun buildRelatedWorkRows(
    currentApiName: String,
    currentTitle: String,
    currentTags: List<String>,
    candidates: List<RelatedCandidate>,
    currentUrl: String? = null,
    /**
     * Metaproviders (TMDB-style) aggregate several real sources, so their
     * recommendations legitimately name a different provider. Everywhere else
     * the rows are strictly single-source: that is what stops another source's
     * catalogue from appearing under a title that belongs to one extension.
     */
    includeForeignSources: Boolean = false,
): RelatedWorkRows {
    val currentUrlKey = currentUrl?.let(::normalizeUrlKey).orEmpty()
    val distinct = candidates.asSequence()
        // One source only. An entry that declares a *different* provider is a
        // cross-source recommendation and must never reach this row: that is
        // what made the "More" rail show another source's catalogue. A blank name is
        // tolerated because extensions that build results directly leave it
        // empty — those entries came from this source's own search, so they
        // belong here, and the caller stamps them before navigation.
        .filter {
            it.item.apiName.isBlank() || it.item.apiName == currentApiName || includeForeignSources
        }
        .filter { it.item.url.isNotBlank() }
        .filter {
            currentUrlKey.isEmpty() || it.item.apiName != currentApiName ||
                normalizeUrlKey(it.item.url) != currentUrlKey
        }
        .distinctBy { relatedIdentity(it.item, currentApiName) }
        .toList()
    val franchise = distinct
        .filter { looksLikeSameFranchise(currentTitle, it.item.name) }
        // Parts of one work are ordered by their own number, so "Part 2" never
        // appears before "Part 1" just because its release year is missing.
        // Kotlin's stable sort keeps provider order when neither explicit part
        // numbering nor a release year can distinguish two entries. Alphabetical
        // title order is not chronology (notably for subtitle-based anime arcs).
        .sortedWith(
            compareBy<RelatedCandidate> { franchiseOrder(it.item.name).takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
                .thenBy { releaseYearOf(it.item) ?: Int.MAX_VALUE }
        )
        // The real seasons are never capped. Only the optional extra titles are:
        // one detail page must not turn into an endless catalogue because a source
        // returned hundreds of search matches.
        .take(FRANCHISE_EXTRA_LIMIT)
    val franchiseKeys = franchise.mapTo(HashSet()) { relatedIdentity(it.item, currentApiName) }
    val targetTags = currentTags.flatMap(::normalizeGenreTags).toSet()

    // Rank by overlap count first, then by overlap ratio. Stable source order is kept
    // for ties so recommendations do not jump around between recompositions.
    val taggedMatches = if (targetTags.isEmpty()) emptyList() else distinct.mapIndexedNotNull { index, candidate ->
        if (relatedIdentity(candidate.item, currentApiName) in franchiseKeys) return@mapIndexedNotNull null
        val candidateTags = candidate.tags.flatMap(::normalizeGenreTags).toSet()
        val matches = targetTags intersect candidateTags
        if (matches.isEmpty()) return@mapIndexedNotNull null
        val ratio = matches.size.toFloat() / (targetTags union candidateTags).size.coerceAtLeast(1)
        Triple(index, candidate.item, matches.size to ratio)
    }.sortedWith(
        compareByDescending<Triple<Int, SearchResponse, Pair<Int, Float>>> { it.third.first }
            .thenByDescending { it.third.second }
            .thenBy { it.first }
    ).map { it.second }

    // Only show a "related" badge when relation is actually verified by tag overlap.
    // Untagged provider-search neighbours may be useful search results, but they are
    // not evidence of a relationship and caused unrelated titles to leak into this lane.
    val similar = taggedMatches
        .distinctBy { relatedIdentity(it, currentApiName) }
        .take(RELATED_ROW_LIMIT)

    return RelatedWorkRows(franchise.map { it.item }, similar)
}

/** Hard caps apply only to optional extras; every provider-declared season stays visible. */
internal const val FRANCHISE_EXTRA_LIMIT = 24
internal const val RELATED_ROW_LIMIT = 6
