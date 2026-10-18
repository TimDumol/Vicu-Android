package com.rendyhd.vicu.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rendyhd.vicu.auth.AuthDebugLog
import com.rendyhd.vicu.ui.components.shared.copyPlainText
import com.rendyhd.vicu.util.AppDispatchers
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AuthDebugLogDialog(onDismiss: () -> Unit) {
    val dispatchers: AppDispatchers = koinInject()
    LogTextDialog(
        title = "Auth Debug Log",
        copyLabel = "Auth debug log",
        // The log is a file: read it off the main thread.
        read = { withContext(dispatchers.io) { AuthDebugLog.readLog() } },
        newestLast = true,
        onClear = { AuthDebugLog.clear() },
        onDismiss = onDismiss,
    )
}

/** When the platform last ran its background work (iOS), newest first. */
@Composable
internal fun BackgroundActivityDialog(onDismiss: () -> Unit) {
    val hooks: PlatformSettingsHooks = koinInject()
    LogTextDialog(
        title = "Background activity",
        copyLabel = "Background activity",
        read = { hooks.readBackgroundActivityLog() },
        newestLast = false,
        onClear = null,
        onDismiss = onDismiss,
    )
}

/** A read-only log in a dialog, with Copy, and Clear when [onClear] is given. */
@Composable
private fun LogTextDialog(
    title: String,
    copyLabel: String,
    read: suspend () -> String,
    newestLast: Boolean,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // Null while it loads.
    val logText by produceState<String?>(initialValue = null) { value = read() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f),
            ) {
                val loaded = logText
                if (loaded != null) {
                    // The newest lines in view: the end of a log that appends, the start of one that prepends.
                    val vScroll = rememberScrollState(if (newestLast) Int.MAX_VALUE else 0)
                    val hScroll = rememberScrollState()
                    Text(
                        text = loaded,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier
                            .verticalScroll(vScroll)
                            .horizontalScroll(hScroll),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = logText != null,
                onClick = {
                    scope.launch { clipboard.copyPlainText(copyLabel, logText.orEmpty()) }
                },
            ) {
                Text("Copy")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onClear != null) {
                    TextButton(onClick = {
                        onClear()
                        onDismiss()
                    }) {
                        Text("Clear")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
    )
}
