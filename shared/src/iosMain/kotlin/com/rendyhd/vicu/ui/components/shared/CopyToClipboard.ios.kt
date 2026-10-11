package com.rendyhd.vicu.ui.components.shared

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

// iOS keeps no label with a pasteboard item; only the text is copied.
@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun Clipboard.copyPlainText(label: String, text: String) {
    setClipEntry(ClipEntry.withPlainText(text))
}
