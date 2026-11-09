package com.roywatson.android_voice_whisperer.domain.model

sealed class AudioRecordingState {
    data object Idle : AudioRecordingState()
    data class Recording(
        val durationMs: Long,
        val currentAmplitude: Float,
        val isSpeechDetected: Boolean
    ) : AudioRecordingState()
    data class Paused(val durationMs: Long) : AudioRecordingState()
    data class Transcribing(val progressPercent: Int) : AudioRecordingState()
    data class Error(val message: String) : AudioRecordingState()
}
