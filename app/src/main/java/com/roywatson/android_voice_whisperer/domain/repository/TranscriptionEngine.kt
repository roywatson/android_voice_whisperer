package com.roywatson.android_voice_whisperer.domain.repository

import com.roywatson.android_voice_whisperer.domain.model.TranscriptSegment
import java.io.File

data class TranscriptionResult(
    val isSuccess: Boolean,
    val fullTranscript: String,
    val segments: List<TranscriptSegment>,
    val inferenceTimeMs: Long,
    val realTimeFactor: Float,
    val errorMessage: String? = null
)

interface TranscriptionEngine {
    suspend fun initialize(modelFile: File): Boolean
    fun isReady(): Boolean
    suspend fun transcribeAudio(
        audioFile: File,
        language: String = "en",
        numThreads: Int = 4
    ): TranscriptionResult
    fun getSystemInfo(): String
    fun release()
}
