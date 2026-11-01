package com.kodastream.app.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import java.util.Locale

/**
 * The extension language picker shared by the Extensions filter sheet and Settings.
 * "All languages" wins over the individual ticks, like Mihon's extension filter.
 */
@Composable
fun MangaLanguageDialog(
    languages: List<String>,
    selected: Set<String>,
    allLanguages: Boolean,
    onDismiss: () -> Unit,
    onSave: (languages: Set<String>, allLanguages: Boolean) -> Unit,
) {
    var all by remember { mutableStateOf(allLanguages) }
    var checked by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manga_extension_languages)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                item(key = "all") {
                    LanguageRow(
                        label = stringResource(R.string.manga_language_all),
                        checked = all,
                        enabled = true,
                        onClick = { all = !all },
                    )
                }
                items(languages, key = { it }) { code ->
                    LanguageRow(
                        label = mangaLanguageName(code),
                        checked = code in checked,
                        enabled = !all,
                        onClick = {
                            all = false
                            checked = if (code in checked) checked - code else checked + code
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = all || checked.isNotEmpty(),
                onClick = {
                    onSave(checked, all)
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

@Composable
private fun LanguageRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The language code as a name in the app's own language ("ar" -> "العربية"). */
@Composable
fun mangaLanguageName(code: String): String {
    if (code == "all") return stringResource(R.string.manga_multi_language)
    if (code == "other") return stringResource(R.string.manga_language_other)
    val locale = LocalConfiguration.current.locales[0]
    return remember(code, locale) {
        Locale.forLanguageTag(code).getDisplayName(locale).ifBlank { code.uppercase(locale) }
    }
}

/** Sort key for language chips and the language list. */
fun mangaLanguageSortKey(code: String, locale: Locale): String =
    Locale.forLanguageTag(code).getDisplayName(locale).ifBlank { code }

/** The real source languages in a store list, without Mihon's "all" marker. */
fun mangaSourceLanguages(languages: Collection<String>): List<String> =
    languages.filter { it.isNotBlank() && it != "all" && it != "other" }
        .distinct()
        .sorted()
