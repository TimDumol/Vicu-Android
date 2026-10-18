package com.rendyhd.vicu.ui

import androidx.compose.runtime.Composable

@Composable
expect fun rememberImagePicker(onImagePicked: (String) -> Unit): () -> Unit

@Composable
expect fun rememberFilePicker(onFilePicked: (String) -> Unit): () -> Unit

/**
 * Picks a sound file for the completion sound and keeps access to it across restarts. The result
 * is a string the platform's sound player understands (a content URI on Android).
 */
@Composable
expect fun rememberCompletionSoundPicker(onSoundPicked: (String) -> Unit): () -> Unit
