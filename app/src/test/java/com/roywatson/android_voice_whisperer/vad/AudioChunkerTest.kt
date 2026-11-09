package com.roywatson.android_voice_whisperer.vad

import com.roywatson.android_voice_whisperer.domain.model.VadSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioChunkerTest {

    @Test
    fun testChunkingWithSilenceSeparation() {
        val chunker = AudioChunker(
            preSpeechPaddingMs = 200L,
            silenceThresholdMs = 500L,
            maxChunkDurationMs = 10000L,
            sampleRate = 16000
        )

        // 5 seconds of mock audio (80,000 samples)
        val samples = FloatArray(16000 * 5) { 0.1f }

        // Speech between 1000ms - 2000ms, silence, then speech between 3500ms - 4500ms
        val vadSegments = listOf(
            VadSegment(0, 16000, 0, 1000, false),
            VadSegment(16000, 32000, 1000, 2000, true),
            VadSegment(32000, 56000, 2000, 3500, false), // 1500ms silence > 500ms threshold
            VadSegment(56000, 72000, 3500, 4500, true),
            VadSegment(72000, 80000, 4500, 5000, false)
        )

        val chunks = chunker.createChunks(samples, vadSegments)

        assertEquals(2, chunks.size)
        // First chunk starts around 800ms (1000 - 200ms pre-speech padding)
        assertTrue(chunks[0].startMs in 0..1000)
        // Second chunk corresponds to the second speech segment
        assertTrue(chunks[1].startMs >= 3000)
    }

    @Test
    fun testEmptyVadFallsBackToFullClip() {
        val chunker = AudioChunker()
        val samples = FloatArray(16000 * 2) { 0.2f } // 2 seconds

        val chunks = chunker.createChunks(samples, emptyList())
        assertEquals(1, chunks.size)
        assertEquals(2000L, chunks[0].durationMs)
    }
}
