package com.kodastream.app.ui.manga

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.widget.doAfterTextChanged
import androidx.preference.CheckBoxPreference
import androidx.preference.DialogPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import androidx.preference.getOnBindEditTextListener
import androidx.preference.setOnStateChangeListener
import com.kodastream.app.R
import com.kodastream.app.manga.extension.MangaSourceManager
import com.kodastream.app.manga.extension.SharedPreferencesDataStore
import com.kodastream.app.manga.extension.mangaSourcePreferences
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a [ConfigurableSource]'s `setupPreferenceScreen` tree with Compose. The
 * androidx.preference objects stay the model, so extensions keep the library's exact
 * behaviour, including validation listeners on their own EditText.
 */
@Composable
fun SourcePreferencesScreen(sourceId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    // The tree is recreated after a configuration change, so the open dialog is restored by key.
    var savedDialogKey by rememberSaveable { mutableStateOf<String?>(null) }

    val state by produceState<SourcePreferencesState?>(initialValue = null, sourceId) {
        val source = MangaSourceManager.get(context).getOrStub(sourceId)
        val preferences = withContext(Dispatchers.IO) {
            mangaSourcePreferences(context, sourceId).apply { all }
        }
        value = try {
            SourcePreferencesState(
                tree = SourcePreferenceTree(context, source, preferences, savedDialogKey),
            )
        } catch (e: Throwable) {
            SourcePreferencesState(error = e.message ?: e.javaClass.simpleName)
        }
    }

    val tree = state?.tree
    if (tree != null) {
        DisposableEffect(tree) {
            onDispose { tree.dispose() }
        }
        savedDialogKey = tree.dialogPreference?.key
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Text(
                text = tree?.title ?: stringResource(R.string.manga_source_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Spacer(Modifier.size(48.dp))
        }

        when {
            state == null -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            tree == null -> Text(
                text = state?.error ?: stringResource(R.string.manga_no_source_settings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            else -> {
                val items = remember(tree.version) { tree.items() }
                if (items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.manga_no_source_settings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                    ) {
                        items(items) { item ->
                            PreferenceRow(item) { item.preference.performClick() }
                        }
                    }
                }
            }
        }
    }

    if (tree != null) {
        val preference = tree.dialogPreference
        if (preference != null) {
            val title = (preference.dialogTitle?.takeIf { it.isNotEmpty() } ?: preference.title)
                ?.toString()
            val message = preference.dialogMessage?.toString()
            val dismiss = { tree.dialogPreference = null }
            when (preference) {
                is ListPreference -> ListPreferenceDialog(preference, title, message, dismiss)
                is MultiSelectListPreference ->
                    MultiSelectListPreferenceDialog(preference, title, message, dismiss)
                is EditTextPreference -> EditTextPreferenceDialog(preference, title, message, dismiss)
                else -> {
                    // A dialog type this screen does not draw: close it as the library would.
                    LaunchedEffect(preference) { dismiss() }
                }
            }
        }
    }
}

private data class SourcePreferencesState(
    val tree: SourcePreferenceTree? = null,
    val error: String? = null,
)

/**
 * The screen an extension fills in `setupPreferenceScreen`, with the library objects as
 * the model. [version] bumps on every change the library would rebind a view for.
 */
@Stable
private class SourcePreferenceTree(
    context: Context,
    source: Source,
    preferences: SharedPreferences,
    dialogKey: String?,
) {
    val title = source.name

    var version by mutableIntStateOf(0)
        private set

    var dialogPreference by mutableStateOf<DialogPreference?>(null)

    private val screen: PreferenceScreen

    init {
        val manager = PreferenceManager(context)
        manager.preferenceDataStore = SharedPreferencesDataStore(preferences)
        manager.setOnDisplayPreferenceDialogListener { preference ->
            dialogPreference = preference as? DialogPreference
        }
        screen = manager.createPreferenceScreen(context)
        (source as? ConfigurableSource)?.setupPreferenceScreen(screen)
        manager.setPreferences(screen)
        screen.onAttached()
        observe(screen)
        dialogPreference = dialogKey?.let { screen.findPreference<Preference>(it) as? DialogPreference }
    }

    private fun observe(preference: Preference) {
        preference.setOnStateChangeListener {
            // A group changing may mean preferences were added to it.
            if (it is PreferenceGroup) observe(it)
            version++
        }
        if (preference is PreferenceGroup) {
            repeat(preference.preferenceCount) { observe(preference.getPreference(it)) }
        }
    }

    fun items(): List<PreferenceItem> = buildList { addVisible(screen) }

    private fun MutableList<PreferenceItem>.addVisible(group: PreferenceGroup) {
        repeat(group.preferenceCount) {
            val preference = group.getPreference(it)
            if (!preference.isVisible) return@repeat
            add(PreferenceItem.of(preference))
            if (preference is PreferenceGroup) addVisible(preference)
        }
    }

    fun dispose() {
        screen.onDetached()
    }
}

private data class PreferenceItem(
    val preference: Preference,
    val title: String,
    val summary: String?,
    val enabled: Boolean,
    val checked: Boolean?,
    val isCheckBox: Boolean,
    val isGroup: Boolean,
    val selectable: Boolean,
) {
    companion object {
        fun of(preference: Preference): PreferenceItem {
            val summary = when {
                preference !is TwoStatePreference -> preference.summary
                preference.isChecked && !preference.summaryOn.isNullOrEmpty() -> preference.summaryOn
                !preference.isChecked && !preference.summaryOff.isNullOrEmpty() -> preference.summaryOff
                else -> preference.summary
            }
            return PreferenceItem(
                preference = preference,
                title = preference.title?.toString().orEmpty(),
                summary = summary?.toString()?.takeIf { it.isNotBlank() },
                enabled = preference.isEnabled,
                checked = (preference as? TwoStatePreference)?.isChecked,
                isCheckBox = preference is CheckBoxPreference,
                isGroup = preference is PreferenceGroup,
                selectable = preference.isSelectable,
            )
        }
    }
}

@Composable
private fun PreferenceRow(item: PreferenceItem, onClick: () -> Unit) {
    if (item.isGroup) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                .semantics { heading() },
        )
        return
    }

    ListItem(
        headlineContent = { Text(item.title) },
        supportingContent = item.summary?.let { summary -> { Text(summary) } },
        trailingContent = when {
            item.checked != null -> {
                {
                    if (item.isCheckBox) {
                        Checkbox(checked = item.checked, onCheckedChange = null, enabled = item.enabled)
                    } else {
                        Switch(checked = item.checked, onCheckedChange = null, enabled = item.enabled)
                    }
                }
            }
            item.selectable -> {
                {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = item.enabled && (item.selectable || item.checked != null)) {
                if (item.selectable) onClick()
            },
    )
}

@Composable
private fun ListPreferenceDialog(
    preference: ListPreference,
    title: String?,
    message: String?,
    onDismiss: () -> Unit,
) {
    val entries = entryMap(preference.entries, preference.entryValues)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = {
            Column {
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                entries.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                if (preference.callChangeListener(value)) preference.value = value
                                onDismiss()
                            }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = preference.value == value, onClick = null)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun MultiSelectListPreferenceDialog(
    preference: MultiSelectListPreference,
    title: String?,
    message: String?,
    onDismiss: () -> Unit,
) {
    val entries = entryMap(preference.entries, preference.entryValues)
    var selected by remember { mutableStateOf(preference.values.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = {
            Column {
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                entries.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                selected = if (value in selected) selected - value else selected + value
                            }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = value in selected, onCheckedChange = null)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val values = selected.toHashSet()
                    if (values != preference.values && preference.callChangeListener(values)) {
                        preference.values = values
                    }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * Extensions get a real EditText through [EditTextPreference.OnBindEditTextListener], and
 * some validate input by disabling the dialog's `android.R.id.button1` found from the
 * EditText's root view, so one stands in for this dialog's OK button.
 */
@Composable
private fun EditTextPreferenceDialog(
    preference: EditTextPreference,
    title: String?,
    message: String?,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(preference.key) { mutableStateOf(preference.text.orEmpty()) }
    var field by remember { mutableStateOf<EditText?>(null) }
    var confirmEnabled by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = {
            // Like the library's dialog, which shows the keyboard as it opens.
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE) }
            Column {
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { viewContext ->
                        LinearLayout(viewContext).apply {
                            val confirmButton = object : Button(viewContext) {
                                init {
                                    id = android.R.id.button1
                                    visibility = View.GONE
                                }

                                override fun setEnabled(enabled: Boolean) {
                                    super.setEnabled(enabled)
                                    confirmEnabled = enabled
                                }
                            }
                            addView(confirmButton)
                            val editText = EditText(viewContext).apply {
                                setText(draft)
                                setSelection(text.length)
                                preference.getOnBindEditTextListener()?.onBindEditText(this)
                                doAfterTextChanged { draft = it?.toString().orEmpty() }
                                post { requestFocus() }
                            }
                            addView(
                                editText,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            field = editText
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmEnabled,
                onClick = {
                    val value = field?.text?.toString()
                    if (value != null && preference.callChangeListener(value)) {
                        preference.text = value
                    }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun entryMap(
    entries: Array<CharSequence>?,
    entryValues: Array<CharSequence>?,
): Map<String, String> =
    entryValues.orEmpty().zip(entries.orEmpty()) { value, entry -> value.toString() to entry.toString() }
        .toMap()
