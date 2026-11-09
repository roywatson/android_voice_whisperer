package com.roywatson.android_voice_whisperer.vad

import com.roywatson.android_voice_whisperer.domain.model.VadSegment

data class AudioChunk(
    val startMs: Long,
    val endMs: Long,
    val samples: FloatArray
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}

class AudioChunker(
    private val preSpeechPaddingMs: Long = DEFAULT_PRE_SPEECH_PADDING_MS,
    private val silenceThresholdMs: Long = DEFAULT_SILENCE_THRESHOLD_MS,
    private val maxChunkDurationMs: Long = 28000L, // Keep under 30s Whisper window
    private val sampleRate: Int = 16000
) {
    companion object {
        const val DEFAULT_SILENCE_THRESHOLD_MS = 850L
        const val DEFAULT_PRE_SPEECH_PADDING_MS = 300L
    }

    /**
     * Segments continuous audio into discrete speech chunks separated by silence pauses.
     */
    fun createChunks(
        rawSamples: FloatArray,
        vadSegments: List<VadSegment>
    ): List<AudioChunk> {
        val chunks = mutableListOf<AudioChunk>()
        if (rawSamples.isEmpty() || vadSegments.isEmpty()) {
            if (rawSamples.isNotEmpty()) {
                val totalMs = (rawSamples.size * 1000L) / sampleRate
                chunks.add(AudioChunk(0, totalMs, rawSamples))
            }
            return chunks
        }

        val samplesPerMs = sampleRate / 1000.0f
        var inSpeech = false
        var chunkStartMs = 0L
        var lastSpeechEndMs = 0L

        for (seg in vadSegments) {
            if (seg.isSpeech) {
                if (!inSpeech) {
                    inSpeech = true
                    chunkStartMs = (seg.startMs - preSpeechPaddingMs).coerceAtLeast(0L)
                }
                lastSpeechEndMs = seg.endMs

                // If chunk exceeds max duration, split it
                if (seg.endMs - chunkStartMs >= maxChunkDurationMs) {
                    val endMs = seg.endMs
                    val startSample = (chunkStartMs * samplesPerMs).toInt().coerceIn(0, rawSamples.size)
                    val endSample = (endMs * samplesPerMs).toInt().coerceIn(0, rawSamples.size)

                    if (endSample > startSample) {
                        val slice = rawSamples.copyOfRange(startSample, endSample)
                        chunks.add(AudioChunk(chunkStartMs, endMs, slice))
                    }
                    chunkStartMs = seg.endMs
                }
            } else {
                if (inSpeech) {
                    val silenceDuration = seg.endMs - lastSpeechEndMs
                    if (silenceDuration >= silenceThresholdMs) {
                        val endMs = (lastSpeechEndMs + (silenceThresholdMs / 2)).coerceAtMost(seg.endMs)
                        val startSample = (chunkStartMs * samplesPerMs).toInt().coerceIn(0, rawSamples.size)
                        val endSample = (endMs * samplesPerMs).toInt().coerceIn(0, rawSamples.size)

                        if (endSample > startSample) {
                            val slice = rawSamples.copyOfRange(startSample, endSample)
                            chunks.add(AudioChunk(chunkStartMs, endMs, slice))
                        }
                        inSpeech = false
                    }
                }
            }
        }

        // Flush any trailing speech chunk
        if (inSpeech) {
            val totalAudioMs = (rawSamples.size * 1000L) / sampleRate
            val endMs = totalAudioMs
            val startSample = (chunkStartMs * samplesPerMs).toInt().coerceIn(0, rawSamples.size)
            val endSample = rawSamples.size
            if (endSample > startSample) {
                val slice = rawSamples.copyOfRange(startSample, endSample)
                chunks.add(AudioChunk(chunkStartMs, endMs, slice))
            }
        }

        // Fallback: If VAD flagged no speech but audio exists, transcribe entire clip
        if (chunks.isEmpty() && rawSamples.isNotEmpty()) {
            val totalMs = (rawSamples.size * 1000L) / sampleRate
            chunks.add(AudioChunk(0, totalMs, rawSamples))
        }

        return chunks
    }
}
