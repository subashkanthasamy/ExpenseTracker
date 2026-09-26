import AVFoundation
import Foundation
import Shared
import Speech

/// Speech-to-text for voice expense entry — the iOS counterpart to Android's
/// SpeechRecognizer. Parsing is not reimplemented here: the recognised text goes through
/// `VoiceExpenseParser` from `shared/`, so both platforms interpret a phrase identically.
@Observable
final class VoiceInputService {

    enum State: Equatable {
        case idle
        case listening
        case denied(String)
        case failed(String)
    }

    var state: State = .idle
    var transcript = ""

    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en_IN"))
        ?? SFSpeechRecognizer()
    private let audioEngine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    var isListening: Bool { state == .listening }

    /// Amount and category hint parsed from whatever has been heard so far.
    var parsed: ParsedExpense? {
        guard !transcript.isEmpty else { return nil }
        return VoiceExpenseParser.shared.parse(text: transcript)
    }

    // MARK: - Permissions

    private func authorize() async -> Bool {
        let speech = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { continuation.resume(returning: $0) }
        }
        guard speech == .authorized else {
            state = .denied("Speech recognition is turned off for Expense Tracker. Turn it on in Settings to add expenses by voice.")
            return false
        }
        let mic = await AVAudioApplication.requestRecordPermission()
        guard mic else {
            state = .denied("Microphone access is turned off for Expense Tracker. Turn it on in Settings to add expenses by voice.")
            return false
        }
        return true
    }

    // MARK: - Control

    func start() async {
        guard !isListening else { return }
        guard await authorize() else { return }
        guard let recognizer, recognizer.isAvailable else {
            state = .failed("Voice entry isn't available right now. Try again later.")
            return
        }

        transcript = ""
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        self.request = request

        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let input = audioEngine.inputNode
            let format = input.outputFormat(forBus: 0)
            input.removeTap(onBus: 0)
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
                request.append(buffer)
            }
            audioEngine.prepare()
            try audioEngine.start()
        } catch {
            state = .failed("Couldn't start listening. Try again.")
            return
        }

        state = .listening
        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            guard let self else { return }
            Task { @MainActor in
                if let result {
                    self.transcript = result.bestTranscription.formattedString
                }
                if error != nil || result?.isFinal == true {
                    self.stop()
                }
            }
        }
    }

    func stop() {
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }
        request?.endAudio()
        task?.cancel()
        request = nil
        task = nil
        try? AVAudioSession.sharedInstance().setActive(false)
        if state == .listening { state = .idle }
    }
}
