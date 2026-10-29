package com.kodastream.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kodastream.app.R

/**
 * Hosts a full-width list whose content is centered at most [maxContentWidth]
 * wide. The gutter is added to the list's content padding, so the list still
 * scrolls from anywhere while rows keep a readable length.
 */
@Composable
internal fun CenteredListBox(
    maxContentWidth: Dp = 720.dp,
    minGutter: Dp = 16.dp,
    content: @Composable (gutter: Dp) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        content(((maxWidth - maxContentWidth) / 2).coerceAtLeast(minGutter))
    }
}

@Composable
internal fun SettingsHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

/** A titled group of segmented rows: one card, 2dp gaps, rounded ends. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsGroup(
    title: String,
    rows: @Composable ColumnScope.() -> Unit,
) {
    Column {
        SettingsHeading(
            text = title,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            content = rows,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun settingsRowColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceBright,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
)

@Composable
internal fun RowIcon(icon: ImageVector) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(24.dp))
    }
}

/** A row that opens something else; its chevron mirrors in RTL. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NavigationRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = settingsRowColors(),
        leadingContent = { RowIcon(icon) },
        supportingContent = {
            Text(supporting, maxLines = 2)
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

/** The whole row toggles; the switch only shows the state. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = settingsRowColors(),
        leadingContent = { RowIcon(icon) },
        supportingContent = {
            Text(supporting, maxLines = 2)
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

/** A row with no action, for values such as the app version. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InfoRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    supporting: String,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = settingsRowColors(),
        leadingContent = { RowIcon(icon) },
        supportingContent = {
            Text(supporting, maxLines = 2)
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

/** A row whose choices sit in a connected button group under the title. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> ChoiceRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = settingsRowColors(),
        verticalAlignment = Alignment.Top,
        leadingContent = { RowIcon(icon) },
        supportingContent = {
            ConnectedChoiceGroup(
                options = options,
                selected = selected,
                label = label,
                onSelect = onSelect,
                modifier = Modifier.padding(top = 10.dp),
            )
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * A single-choice connected button group: the options share one row, the
 * outer ends are rounded and the checked option morphs to a pill.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> ConnectedChoiceGroup(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showCheck = options.size <= 3
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                icon = if (showCheck && checked) {
                    { Icon(Icons.Rounded.Check, contentDescription = null) }
                } else {
                    null
                },
                contentPadding = PaddingValues(horizontal = if (showCheck) 12.dp else 4.dp),
                modifier = Modifier
                    .weight(1f)
                    // ToggleButton's default height is below the 48dp touch target.
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton },
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RadioRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    selected: Boolean,
    supporting: String? = null,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = settingsRowColors(),
        leadingContent = { RowIcon(icon) },
        supportingContent = if (supporting != null) {
            { Text(supporting, maxLines = 2) }
        } else {
            null
        },
        trailingContent = {
            RadioButton(selected = selected, onClick = null)
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
    }
}

/** A dialog with one radio row per option; used for style and language. */
@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    icon: ImageVector,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    description: (@Composable (T) -> String)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                itemsIndexed(options) { index, option ->
                    RadioRow(
                        index = index,
                        count = options.size,
                        icon = icon,
                        title = label(option),
                        selected = option == selected,
                        supporting = description?.invoke(option),
                        onClick = { onSelect(option) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
