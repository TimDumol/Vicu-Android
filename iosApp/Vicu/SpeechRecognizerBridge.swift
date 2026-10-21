import AVFoundation
import Speech
import UIKit
import shared

/// Holding the + button to say a task: the device's speech recognizer, fed by the microphone
/// through AVAudioEngine, with partial results so the words show while they are spoken. The
/// shared code (`HoldToTalk`) decides what to do with them; this only listens.
///
/// Asks for speech recognition and the microphone on the first hold, never at start.
///
/// The shared code calls in on the main thread, and every callback goes back on it. The audio
/// session and engine are started and stopped on [audioQueue]: activating the session can take
/// a while (seconds in the simulator), and the + must stay responsive meanwhile.
final class SpeechRecognizerBridge: NSObject, AppleSpeechBridge {
    // Main thread only.
    private var listener: SpeechListener?
    /// The words of the utterance so far: kept when the recognizer ends with an error.
    private var lastPartial = ""
    /// Each start gets a new number; results of an older one are ignored.
    private var generation = 0

    // Audio queue only.
    private let audioQueue = DispatchQueue(label: "com.rendyhd.vicu.speech")
    private let audioEngine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    func availability() -> Int32 {
        guard let recognizer = SFSpeechRecognizer(), recognizer.isAvailable else { return 3 }
        let speech = SFSpeechRecognizer.authorizationStatus()
        let microphone = AVAudioApplication.shared.recordPermission
        if speech == .denied || speech == .restricted || microphone == .denied { return 2 }
        if speech == .notDetermined || microphone == .undetermined { return 1 }
        return 0
    }

    func requestPermission(completion: @escaping (KotlinBoolean) -> Void) {
        SFSpeechRecognizer.requestAuthorization { status in
            guard status == .authorized else {
                DispatchQueue.main.async { completion(KotlinBoolean(value: false)) }
                return
            }
            AVAudioApplication.requestRecordPermission { granted in
                DispatchQueue.main.async { completion(KotlinBoolean(value: granted)) }
            }
        }
    }

    func start(listener: SpeechListener) {
        generation += 1
        let run = generation
        self.listener = listener
        lastPartial = ""
        audioQueue.async { [weak self] in self?.startAudio(run: run) }
    }

    func stop() {
        // The recognizer gives its final words once the audio has ended.
        audioQueue.async { [weak self] in
            self?.stopAudio()
            self?.request?.endAudio()
        }
    }

    func cancel() {
        listener = nil
        generation += 1
        audioQueue.async { [weak self] in self?.endRecognition() }
    }

    func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }

    // MARK: Main thread

    private func handle(run: Int, result: SFSpeechRecognitionResult?, error: Error?) {
        guard run == generation, listener != nil else { return }
        if let result {
            let text = result.bestTranscription.formattedString
            if result.isFinal {
                finish()
                if text.isEmpty { deliverError(.noMatch) } else { deliverFinal(text) }
                return
            }
            if !text.isEmpty {
                lastPartial = text
                listener?.onPartial(text: text)
            }
        }
        if error != nil {
            finish()
            // "No speech detected" and the like: what was heard before still counts.
            if lastPartial.isEmpty { deliverError(.noMatch) } else { deliverFinal(lastPartial) }
        }
    }

    private func failed(run: Int) {
        guard run == generation else { return }
        deliverError(.other)
    }

    /// The utterance is over: the audio stops, nothing more comes from this run.
    private func finish() {
        generation += 1
        audioQueue.async { [weak self] in self?.endRecognition() }
    }

    private func deliverFinal(_ text: String) {
        let current = listener
        listener = nil
        current?.onFinal(text: text)
    }

    private func deliverError(_ error: SpeechError) {
        let current = listener
        listener = nil
        current?.onError(error: error)
    }

    // MARK: Audio queue

    private func startAudio(run: Int) {
        endRecognition()
        guard let recognizer = SFSpeechRecognizer(), recognizer.isAvailable else {
            DispatchQueue.main.async { [weak self] in self?.failed(run: run) }
            return
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            // A task is a phrase, not a sentence: no full stop for the parser to trip over.
            request.addsPunctuation = false
            self.request = request

            let input = audioEngine.inputNode
            let format = input.outputFormat(forBus: 0)
            guard format.sampleRate > 0 else { throw SpeechBridgeError.noInput }
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
                request.append(buffer)
                let level = Self.level(of: buffer)
                DispatchQueue.main.async {
                    guard let self, run == self.generation else { return }
                    self.listener?.onLevel(level: level)
                }
            }
            audioEngine.prepare()
            try audioEngine.start()

            task = recognizer.recognitionTask(with: request) { [weak self] result, error in
                DispatchQueue.main.async { self?.handle(run: run, result: result, error: error) }
            }
        } catch {
            NSLog("Vicu: could not start listening: \(error.localizedDescription)")
            endRecognition()
            DispatchQueue.main.async { [weak self] in self?.failed(run: run) }
        }
    }

    private func stopAudio() {
        if audioEngine.isRunning {
            audioEngine.stop()
        }
        audioEngine.inputNode.removeTap(onBus: 0)
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func endRecognition() {
        stopAudio()
        task?.cancel()
        task = nil
        request = nil
    }

    /// The loudness of a buffer, 0 to 1, for the microphone's pulse.
    private static func level(of buffer: AVAudioPCMBuffer) -> Float {
        guard let samples = buffer.floatChannelData?[0], buffer.frameLength > 0 else { return 0 }
        let count = Int(buffer.frameLength)
        var sum: Float = 0
        for index in 0..<count {
            sum += samples[index] * samples[index]
        }
        let rms = (sum / Float(count)).squareRoot()
        let decibels = 20 * log10(max(rms, 0.000_01))
        return min(max((decibels + 50) / 40, 0), 1)
    }
}

private enum SpeechBridgeError: Error {
    /// No microphone input (the simulator without the Mac's microphone).
    case noInput
}
