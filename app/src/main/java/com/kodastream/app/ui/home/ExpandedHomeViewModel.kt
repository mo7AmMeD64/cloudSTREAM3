package com.kodastream.app.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.engine.Engine
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun sectionKey(provider: String, rowKey: String): String = "$provider|$rowKey"

@Immutable
data class ExpandedSection(
    val key: String,
    val providerName: String,
    val title: String,
    val pageIndex: Int,
    val page: Int,
    val hasNext: Boolean,
    val horizontal: Boolean,
    val items: List<SearchResponse>,
    val loading: Boolean = false,
    /** The last page failed; offers a retry row instead of "No more items". */
    val failed: Boolean = false,
)

/**
 * Paged copies of Home sections, kept in memory and shared by Home and the
 * expanded section screen. An activity-scoped instance survives rotation and
 * returning to Home; it is dropped on a provider change or a Home refresh.
 */
class ExpandedHomeViewModel : ViewModel() {
    private val _sections = MutableStateFlow<Map<String, ExpandedSection>>(emptyMap())
    val sections: StateFlow<Map<String, ExpandedSection>> = _sections.asStateFlow()

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private var provider: String? = null

    /** A Home refresh replaces the source lists, so every page is stale. */
    fun reset() {
        _sections.value = emptyMap()
    }

    fun setProvider(name: String?) {
        if (name == provider) return
        provider = name
        reset()
    }

    fun open(providerName: String, row: HomeRow) {
        val key = sectionKey(providerName, row.key)
        _sections.update { sections ->
            if (sections.containsKey(key)) {
                sections
            } else {
                sections + (key to ExpandedSection(
                    key = key,
                    providerName = providerName,
                    title = row.title,
                    pageIndex = row.pageIndex,
                    page = row.page,
                    hasNext = row.hasNext,
                    horizontal = row.horizontal,
                    items = row.items.distinctBy { it.url },
                ))
            }
        }
        _selected.value = key
    }

    /**
     * Fetches the next page. One request at a time; [retry] is the only way
     * past a failed page, because a failure also turns [ExpandedSection.hasNext] off.
     */
    fun loadNext(retry: Boolean = false) {
        val key = _selected.value ?: return
        val current = _sections.value[key] ?: return
        if (current.loading) return
        if (!current.hasNext && !(retry && current.failed)) return
        val api = Engine.provider(current.providerName) ?: return finish(key)
        val page = api.mainPage.getOrNull(current.pageIndex) ?: return finish(key)

        update(key) { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val result = Engine.mainPage(api, page, current.page + 1)
            val response = result.getOrNull()
            val merged = mergeSectionPage(
                current = current.items,
                lists = response?.lists,
                responseHasNext = response?.hasNext == true,
                sectionName = current.title,
            )
            update(key) { latest ->
                if (!latest.loading) {
                    latest
                } else {
                    latest.copy(
                        items = merged.items,
                        page = if (response == null) latest.page else latest.page + 1,
                        hasNext = merged.hasNext,
                        loading = false,
                        failed = response == null,
                    )
                }
            }
        }
    }

    private fun finish(key: String) {
        update(key) { it.copy(hasNext = false, loading = false) }
    }

    private fun update(key: String, block: (ExpandedSection) -> ExpandedSection) {
        _sections.update { sections ->
            sections[key]?.let { sections + (key to block(it)) } ?: sections
        }
    }
}
