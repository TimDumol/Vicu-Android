package com.rendyhd.vicu.voice

/**
 * The speech recognizer on iOS, implemented in Swift (`iosApp/Vicu/SpeechRecognizerBridge.swift`:
 * `SFSpeechRecognizer` fed by `AVAudioEngine`), which Kotlin cannot reach on its own terms.
 */
interface AppleSpeechBridge {
    /** 0: ready, 1: not asked yet, 2: refused (microphone or speech recognition), 3: no recognizer. */
    fun availability(): Int

    /** Asks for speech recognition, then the microphone; [completion] runs once, on the main thread. */
    fun requestPermission(completion: (Boolean) -> Unit)

    fun start(listener: SpeechListener)

    fun stop()

    fun cancel()

    fun openSettings()
}

/** Set by IosApp.start once Swift hands the bridge over; null in tests and before that. */
class IosSpeechBridgeHolder {
    var bridge: AppleSpeechBridge? = null
}

/**
 * [SpeechInput] through the Swift bridge. Building it touches nothing (gotcha 19). iOS has no
 * system speech screen to fall back to: without a recognizer the hold opens quick add, where the
 * keyboard's microphone works.
 */
class IosSpeechInput(private val holder: IosSpeechBridgeHolder) : SpeechInput {
    override fun availability(): SpeechAvailability = when (holder.bridge?.availability()) {
        0 -> SpeechAvailability.READY
        1 -> SpeechAvailability.NEEDS_PERMISSION
        2 -> SpeechAvailability.DENIED
        else -> SpeechAvailability.UNAVAILABLE
    }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        val bridge = holder.bridge ?: return onResult(false)
        bridge.requestPermission(onResult)
    }

    override fun start(listener: SpeechListener) {
        val bridge = holder.bridge ?: return listener.onError(SpeechError.OTHER)
        bridge.start(listener)
    }

    override fun stop() {
        holder.bridge?.stop()
    }

    override fun cancel() {
        holder.bridge?.cancel()
    }

    override fun openSystemSpeech(): Boolean = false

    override fun openSettings() {
        holder.bridge?.openSettings()
    }
}
