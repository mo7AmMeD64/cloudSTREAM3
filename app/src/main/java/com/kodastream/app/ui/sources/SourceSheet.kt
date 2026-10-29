package com.kodastream.app.ui.sources

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.engine.Engine
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Activity-scoped source state, shared by Home, Search and the navigation
 * shell's source button, and the one owner of the Home selection.
 *
 * [home] is the effective provider Home loads; [selectHome] is the only write.
 * The resolver never persists a fallback, so the saved choice survives a
 * provider that loads late or disappears.
 */
class SourceViewModel(application: Application) : AndroidViewModel(application) {
    private val _providers = MutableStateFlow(Engine.providers())
    val providers: StateFlow<List<MainAPI>> = _providers.asStateFlow()

    /** The user's persisted choice. Never overwritten by a fallback. */
    private var saved = AppPrefs.selectedProvider(application)

    /** Set only by a tap in this session, so a restart always trusts [saved]. */
    private var picked: String? = null

    private val _home = MutableStateFlow<String?>(null)
    val home: StateFlow<String?> = _home.asStateFlow()

    /** null means every provider, the same meaning the Search chips had. */
    private val _search = MutableStateFlow<String?>(null)
    val search: StateFlow<String?> = _search.asStateFlow()

    init {
        resolveHome()
        viewModelScope.launch {
            PluginManager.revision.collect {
                val all = Engine.providers()
                _providers.value = all
                val search = _search.value
                if (search != null && all.none { it.name == search }) _search.value = null
                resolveHome()
            }
        }
    }

    private fun resolveHome() {
        val available = _providers.value.filter { it.hasMainPage }.map { it.name }
        val next = resolveHomeSource(
            saved = saved,
            current = _home.value,
            userPicked = picked != null && picked == _home.value,
            available = available,
            startupDone = PluginManager.startupDone.value,
        )
        if (next != _home.value) _home.value = next
    }

    /** A tap in the Home sheet; the only place that changes the saved source. */
    fun selectHome(name: String) {
        val api = Engine.provider(name) ?: return
        if (!api.hasMainPage) return
        picked = name
        saved = name
        AppPrefs.setSelectedProvider(getApplication(), name)
        if (_home.value != name) _home.value = name
    }

    fun selectSearch(name: String?) {
        _search.value = name
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSheet(
    providers: List<MainAPI>,
    selected: String?,
    showAllSources: Boolean,
    onSelect: (String?) -> Unit,
    onOpenExtensions: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.sources),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            if (providers.isEmpty()) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenExtensions) {
                        Text(stringResource(R.string.open_extensions))
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    if (showAllSources) {
                        item(key = "all") {
                            SourceRow(
                                name = stringResource(R.string.search_scope_all),
                                selected = selected == null,
                                onClick = { onSelect(null) },
                            )
                        }
                    }
                    items(providers, key = { it.name }) { api ->
                        // Search can use every provider; Home needs a main page.
                        val canFillHome = showAllSources || api.hasMainPage
                        SourceRow(
                            name = api.name,
                            selected = selected == api.name,
                            supporting = if (canFillHome) null else stringResource(R.string.source_no_home),
                            enabled = canFillHome,
                            onClick = { onSelect(api.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    name: String,
    selected: Boolean,
    supporting: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        contentColor = when {
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .heightIn(min = 56.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null)
            }
        }
    }
}
