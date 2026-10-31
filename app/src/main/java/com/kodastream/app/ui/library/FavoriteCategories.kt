package com.kodastream.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.data.FavoriteCategory
import com.kodastream.app.data.FavoriteCategoryStore

/**
 * One dialog creates and renames categories. It owns the text field and the
 * inline error so both entry points behave the same.
 */
@Composable
fun CategoryNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    categories: List<FavoriteCategory>,
    editingId: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var touched by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val cleaned = name.trim()
    val error = when {
        cleaned.isEmpty() -> R.string.favorites_category_name_blank
        cleaned.length > FavoriteCategoryStore.MAX_NAME_LENGTH -> R.string.favorites_category_name_long
        categories.any { it.id != editingId && it.name.equals(cleaned, ignoreCase = true) } ->
            R.string.favorites_category_name_exists
        else -> null
    }

    fun submit() {
        touched = true
        if (error == null) onConfirm(cleaned)
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.favorites_category_name)) },
                    singleLine = true,
                    isError = touched && error != null,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = if (touched && error != null) stringResource(error) else "",
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text("${name.length}/${FavoriteCategoryStore.MAX_NAME_LENGTH}")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** The one "Move to" sheet, shared by the library grid and the details screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToSheet(
    categories: List<FavoriteCategory>,
    selectedCategoryIds: Set<String?>,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit,
    onCreateAndMove: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.favorites_move_to),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(categories, key = { it.id }) { category ->
                    MoveRow(
                        label = category.name,
                        selected = category.id in selectedCategoryIds,
                        onClick = { onMove(category.id) },
                    )
                }
                item(key = "uncategorized") {
                    MoveRow(
                        label = stringResource(R.string.favorites_category_uncategorized),
                        selected = null in selectedCategoryIds,
                        onClick = { onMove(null) },
                    )
                }
                if (categories.size < FavoriteCategoryStore.MAX_CATEGORIES) {
                    item(key = "new") {
                        MoveRow(
                            label = "+ " + stringResource(R.string.favorites_category_new),
                            selected = false,
                            onClick = { creating = true },
                        )
                    }
                } else {
                    item(key = "limit") {
                        Text(
                            text = stringResource(R.string.favorites_category_limit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        CategoryNameDialog(
            title = stringResource(R.string.favorites_category_new),
            initialName = "",
            confirmLabel = stringResource(R.string.favorites_category_create),
            categories = categories,
            editingId = null,
            onConfirm = { name ->
                creating = false
                onCreateAndMove(name)
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun MoveRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.cd_category_current),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Spacer(Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
