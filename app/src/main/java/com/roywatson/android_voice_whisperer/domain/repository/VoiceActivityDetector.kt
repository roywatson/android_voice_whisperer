package com.roywatson.android_voice_whisperer.domain.repository

import com.roywatson.android_voice_whisperer.domain.model.VadSegment

interface VoiceActivityDetector {
    fun isReady(): Boolean
    suspend fun initialize(): Boolean
    fun processFrame(samples: ShortArray, sampleRate: Int = 16000): Float // Returns speech probability [0.0, 1.0]
    fun segmentAudio(samples: ShortArray, sampleRate: Int = 16000): List<VadSegment>
    fun reset()
    fun release()
}
