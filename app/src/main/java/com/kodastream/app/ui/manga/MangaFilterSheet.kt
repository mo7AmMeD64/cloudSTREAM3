package com.kodastream.app.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.manga.FilterSnapshot
import com.kodastream.app.manga.resetFilters
import com.kodastream.app.manga.restoreFilters
import com.kodastream.app.manga.snapshotFilters
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

/**
 * The source's own filters, edited in place and restored on Cancel, so a
 * source that caches filter objects cannot leak half-applied state into the
 * next request. Apply hands back a snapshot the view model copies onto its
 * applied list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaFilterSheet(
    source: CatalogueSource,
    applied: FilterList,
    onApply: (List<FilterSnapshot>) -> Unit,
    onDismiss: () -> Unit,
) {
    // FilterList.equals is always false, so only the source may key this.
    val working = remember(source) {
        source.getFilterList().also { restoreFilters(it, snapshotFilters(applied)) }
    }
    val initial = remember { snapshotFilters(working) }
    var resetRevision by remember { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun cancel() {
        restoreFilters(working, initial)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = ::cancel, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.manga_filters),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    resetFilters(working)
                    resetRevision++
                }) {
                    Text(stringResource(R.string.manga_filter_reset))
                }
            }
            FilterRows(working, resetRevision)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TextButton(onClick = ::cancel) {
                    Text(stringResource(R.string.cancel))
                }
                Button(onClick = { onApply(snapshotFilters(working)) }) {
                    Text(stringResource(R.string.manga_filter_apply))
                }
            }
        }
    }
}

/** One column for every filter kind; groups recurse with a name and indent. */
@Composable
private fun FilterRows(
    filters: List<Filter<*>>,
    revision: Int,
    nested: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        filters.forEach { filter ->
            when (filter) {
                is Filter.Header -> Text(
                    text = filter.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        start = if (nested) 32.dp else 24.dp,
                        end = 24.dp,
                        top = 16.dp,
                        bottom = 4.dp,
                    ),
                )

                is Filter.Separator -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                is Filter.Text -> TextFilterRow(filter, revision, nested)
                is Filter.CheckBox -> CheckFilterRow(filter, revision, nested)
                is Filter.TriState -> TriFilterRow(filter, revision, nested)
                is Filter.Select<*> -> SelectFilterRow(filter, revision, nested)
                is Filter.Sort -> SortFilterRow(filter, revision, nested)
                is Filter.Group<*> -> {
                    if (filter.name.isNotBlank()) {
                        Text(
                            text = filter.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(
                                start = if (nested) 32.dp else 24.dp,
                                end = 24.dp,
                                top = 16.dp,
                            ),
                        )
                    }
                    FilterRows(filter.state.filterIsInstance<Filter<*>>(), revision, nested = true)
                }
            }
        }
    }
}

private val RowPadding = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)

@Composable
private fun TextFilterRow(filter: Filter.Text, revision: Int, nested: Boolean) {
    var value by remember(filter, revision) { mutableStateOf(filter.state) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            filter.state = it
        },
        label = { Text(filter.name) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (nested) 32.dp else 24.dp,
                end = 24.dp,
                top = 8.dp,
                bottom = 4.dp,
            ),
    )
}

@Composable
private fun CheckFilterRow(filter: Filter.CheckBox, revision: Int, nested: Boolean) {
    var checked by remember(filter, revision) { mutableStateOf(filter.state) }
    Row(
        modifier = RowPadding
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable {
                checked = !checked
                filter.state = checked
            }
            .padding(start = if (nested) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = filter.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriFilterRow(filter: Filter.TriState, revision: Int, nested: Boolean) {
    var state by remember(filter, revision) { mutableStateOf(filter.state) }
    val labels = listOf(
        stringResource(R.string.manga_filter_off),
        stringResource(R.string.manga_filter_include),
        stringResource(R.string.manga_filter_exclude),
    )
    Column(
        modifier = Modifier.padding(
            start = if (nested) 32.dp else 24.dp,
            end = 24.dp,
            top = 8.dp,
            bottom = 4.dp,
        ),
    ) {
        Text(filter.name, style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { index, label ->
                FilterChip(
                    selected = state == index,
                    onClick = {
                        state = index
                        filter.state = index
                    },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun SelectFilterRow(filter: Filter.Select<*>, revision: Int, nested: Boolean) {
    var index by remember(filter, revision) { mutableStateOf(filter.state) }
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.padding(
            start = if (nested) 32.dp else 24.dp,
            end = 24.dp,
            top = 8.dp,
            bottom = 4.dp,
        ),
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "${filter.name}: ${filter.values.getOrNull(index) ?: ""}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            filter.values.forEachIndexed { valueIndex, value ->
                DropdownMenuItem(
                    text = { Text(value.toString()) },
                    trailingIcon = if (valueIndex == index) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        index = valueIndex
                        filter.state = valueIndex
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SortFilterRow(filter: Filter.Sort, revision: Int, nested: Boolean) {
    var selection by remember(filter, revision) { mutableStateOf(filter.state) }
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(
            start = if (nested) 32.dp else 24.dp,
            end = 24.dp,
            top = 8.dp,
            bottom = 4.dp,
        ),
    ) {
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "${filter.name}: ${filter.values.getOrNull(selection?.index ?: -1) ?: ""}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                filter.values.forEachIndexed { index, value ->
                    DropdownMenuItem(
                        text = { Text(value) },
                        trailingIcon = if (index == selection?.index) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            selection = Filter.Sort.Selection(index, selection?.ascending ?: true)
                            filter.state = selection
                            expanded = false
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.manga_filter_sort_ascending),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = selection?.ascending ?: true,
                onCheckedChange = { ascending ->
                    val index = selection?.index
                    if (index != null) {
                        selection = Filter.Sort.Selection(index, ascending)
                        filter.state = selection
                    }
                },
            )
        }
    }
}
