package com.roywatson.android_voice_whisperer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roywatson.android_voice_whisperer.VoiceWhispererApplication
import com.roywatson.android_voice_whisperer.audio.AudioRecordingService
import com.roywatson.android_voice_whisperer.audio.WavAudioRecorder
import com.roywatson.android_voice_whisperer.domain.model.AudioRecordingState
import com.roywatson.android_voice_whisperer.domain.model.TranscriptionStatus
import com.roywatson.android_voice_whisperer.domain.model.VoiceNote
import com.roywatson.android_voice_whisperer.vad.AudioChunker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VoiceWhispererApplication
    private val repository = app.repository
    private val transcriptionEngine = app.transcriptionEngine
    private val recorder = WavAudioRecorder()

    private val _recordingState = MutableStateFlow<AudioRecordingState>(AudioRecordingState.Idle)
    val recordingState: StateFlow<AudioRecordingState> = _recordingState.asStateFlow()

    private var currentAudioFile: File? = null

    // Adaptive live speech detection synchronized with AudioChunker silence threshold
    private var noiseFloor = 0.004f
    private var lastSpeechTimestampMs = 0L
    private val speechHangoverMs = AudioChunker.DEFAULT_SILENCE_THRESHOLD_MS

    init {
        viewModelScope.launch {
            recorder.status.collect { status ->
                when (status) {
                    is WavAudioRecorder.RecordingStatus.Idle -> {
                        if (_recordingState.value !is AudioRecordingState.Transcribing) {
                            _recordingState.value = AudioRecordingState.Idle
                        }
                    }
                    is WavAudioRecorder.RecordingStatus.Recording -> {
                        val currentAmp = status.amplitude
                        // Adaptively track ambient noise floor
                        if (currentAmp < noiseFloor * 1.4f) {
                            noiseFloor = noiseFloor * 0.95f + currentAmp * 0.05f
                        }

                        // Sensitive threshold capturing soft consonants and numbers (e.g. 3, 6, 8)
                        val speechThreshold = maxOf(0.009f, noiseFloor * 1.5f)
                        if (currentAmp > speechThreshold) {
                            lastSpeechTimestampMs = System.currentTimeMillis()
                        }

                        // Unified hangover: stays active across pauses up to the exact chunking threshold
                        val isSpeech = (System.currentTimeMillis() - lastSpeechTimestampMs) < speechHangoverMs

                        _recordingState.value = AudioRecordingState.Recording(
                            durationMs = status.durationMs,
                            currentAmplitude = status.amplitude,
                            isSpeechDetected = isSpeech
                        )
                    }
                    is WavAudioRecorder.RecordingStatus.Paused -> {
                        _recordingState.value = AudioRecordingState.Paused(status.durationMs)
                    }
                    is WavAudioRecorder.RecordingStatus.Finished -> {
                        onRecordingFinished(status.outputFile, status.durationMs)
                    }
                    is WavAudioRecorder.RecordingStatus.Error -> {
                        _recordingState.value = AudioRecordingState.Error(status.message)
                    }
                }
            }
        }
    }

    fun startRecording() {
        noiseFloor = 0.008f
        lastSpeechTimestampMs = 0L

        val recordingsDir = File(app.filesDir, "recordings").apply {
            if (!exists()) mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outputFile = File(recordingsDir, "rec_$timestamp.wav")
        currentAudioFile = outputFile

        AudioRecordingService.start(app)
        val started = recorder.startRecording(outputFile, viewModelScope)
        if (!started) {
            AudioRecordingService.stop(app)
            _recordingState.value = AudioRecordingState.Error("Could not start audio recorder")
        }
    }

    fun pauseRecording() {
        recorder.pauseRecording()
    }

    fun resumeRecording() {
        recorder.resumeRecording()
    }

    fun stopRecording() {
        AudioRecordingService.stop(app)
        recorder.stopRecording()
    }

    fun cancelRecording() {
        AudioRecordingService.stop(app)
        val file = recorder.stopRecording()
        file?.delete()
        currentAudioFile?.delete()
        _recordingState.value = AudioRecordingState.Idle
    }

    private fun onRecordingFinished(audioFile: File, durationMs: Long) {
        viewModelScope.launch {
            _recordingState.value = AudioRecordingState.Transcribing(10)

            val dateFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
            val defaultTitle = "Voice Note - ${dateFormat.format(Date())}"

            // 1. Insert note with TRANSCRIBING status
            val initialNote = VoiceNote(
                title = defaultTitle,
                durationMs = durationMs,
                audioFilePath = audioFile.absolutePath,
                status = TranscriptionStatus.TRANSCRIBING
            )
            var noteId = 0L

            try {
                noteId = repository.insertNote(initialNote)
                _recordingState.value = AudioRecordingState.Transcribing(40)

                // 2. Perform on-device Whisper transcription
                val result = transcriptionEngine.transcribeAudio(audioFile)
                _recordingState.value = AudioRecordingState.Transcribing(85)

                // 3. Persist segments with noteId reference
                val segmentsWithId = result.segments.map { it.copy(noteId = noteId) }
                if (segmentsWithId.isNotEmpty()) {
                    repository.insertSegments(segmentsWithId)
                }

                // 4. Update note with final transcript, RTF and status
                val completedNote = initialNote.copy(
                    id = noteId,
                    status = if (result.isSuccess) TranscriptionStatus.COMPLETED else TranscriptionStatus.FAILED,
                    segments = segmentsWithId,
                    fullTranscript = result.fullTranscript,
                    realTimeFactor = result.realTimeFactor,
                    inferenceTimeMs = result.inferenceTimeMs,
                    errorMessage = result.errorMessage
                )
                repository.updateNote(completedNote)
            } catch (e: Exception) {
                if (noteId != 0L) {
                    val failedNote = initialNote.copy(
                        id = noteId,
                        status = TranscriptionStatus.FAILED,
                        errorMessage = e.message ?: "Transcription failed unexpectedly"
                    )
                    repository.updateNote(failedNote)
                }
            } finally {
                _recordingState.value = AudioRecordingState.Idle
            }
        }
    }
}
