package com.kodastream.app.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.crash.CrashRecorder

/**
 * The one crash report surface: next-start dialog and the Settings row.
 * Copy, Share and Delete all act on the same local text; nothing is sent.
 */
@Composable
fun CrashReportDialog(
    text: String,
    onDismiss: () -> Unit,
    allowShare: Boolean,
    allowDelete: Boolean,
    onDeleted: () -> Unit = {},
) {
    val context = LocalContext.current

    fun copy() {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText("KodaStream crash", text))
        Toast.makeText(context, R.string.crash_copied, Toast.LENGTH_SHORT).show()
    }

    fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crash_dialog_title)) },
        text = {
            Column {
                if (allowShare) {
                    Text(
                        text = stringResource(R.string.crash_dialog_body),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.crash_dismiss))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = ::copy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.crash_copy))
                }
                if (allowShare) {
                    TextButton(onClick = ::share, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.crash_share))
                    }
                }
                if (allowDelete) {
                    TextButton(
                        onClick = {
                            CrashRecorder.deleteReports(context)
                            Toast.makeText(context, R.string.crash_deleted, Toast.LENGTH_SHORT)
                                .show()
                            onDeleted()
                            onDismiss()
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.crash_delete))
                    }
                }
            }
        },
    )
}
