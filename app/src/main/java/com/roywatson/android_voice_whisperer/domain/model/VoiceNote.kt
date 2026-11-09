package com.roywatson.android_voice_whisperer.domain.model

import java.util.Locale

data class VoiceNote(
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val audioFilePath: String,
    val status: TranscriptionStatus = TranscriptionStatus.IDLE,
    val segments: List<TranscriptSegment> = emptyList(),
    val fullTranscript: String = "",
    val realTimeFactor: Float = 0.0f,
    val inferenceTimeMs: Long = 0L,
    val modelUsed: String = "whisper-tiny.en",
    val summary: String? = null,
    val actionItems: List<String> = emptyList(),
    val errorMessage: String? = null
) {
    val wordCount: Int
        get() = if (fullTranscript.isBlank()) 0 else fullTranscript.trim().split("\\s+".toRegex()).size

    fun formattedDuration(): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
