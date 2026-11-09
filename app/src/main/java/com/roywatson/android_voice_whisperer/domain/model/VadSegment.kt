package com.roywatson.android_voice_whisperer.domain.model

data class VadSegment(
    val startSample: Int,
    val endSample: Int,
    val startMs: Long,
    val endMs: Long,
    val isSpeech: Boolean,
    val speechProbability: Float = 1.0f
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}
