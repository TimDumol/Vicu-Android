package com.rendyhd.vicu.util

import com.rendyhd.vicu.data.local.BehaviorPrefsStore
import kotlinx.coroutines.flow.first
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

/**
 * Plays the completion sound: the file the user picked, or the sound bundled with the app (iOS
 * has no API for the user's notification sound). The ambient audio session keeps it quiet when
 * the silent switch is on and lets other audio play on. Failures are logged and ignored.
 */
class IosCompletionSoundPlayer(private val prefs: BehaviorPrefsStore) {

    private var player: AVAudioPlayer? = null

    suspend fun play() {
        val current = prefs.getPrefs().first()
        if (!current.completionSoundEnabled) return
        val custom = current.completionSoundUri
            ?.takeIf { it.isNotBlank() && NSFileManager.defaultManager.fileExistsAtPath(it) }
        val url = custom?.let { NSURL.fileURLWithPath(it) }
            ?: NSBundle.mainBundle.URLForResource("complete", withExtension = "wav")
            ?: return
        try {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, error = null)
            AVAudioSession.sharedInstance().setActive(true, error = null)
            player = AVAudioPlayer(contentsOfURL = url, error = null).also { it.play() }
        } catch (e: Exception) {
            Logger.w(TAG, "Could not play the completion sound: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "CompletionSound"
    }
}
