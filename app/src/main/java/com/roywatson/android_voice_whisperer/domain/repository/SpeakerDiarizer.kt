package com.roywatson.android_voice_whisperer.domain.repository

import com.roywatson.android_voice_whisperer.domain.model.TranscriptSegment
import java.io.File

/**
 * Architectural Seam for Step B3: On-Device Speaker Labels & Diarization.
 * Implemented via on-device speaker embeddings (e.g., sherpa-onnx or LiteRT)
 * combined with clustering to label speakers ("Speaker 1", "Speaker 2").
 */
interface SpeakerDiarizer {
    suspend fun diarize(
        audioFile: File,
        segments: List<TranscriptSegment>
    ): List<TranscriptSegment>

    fun isReady(): Boolean
}
