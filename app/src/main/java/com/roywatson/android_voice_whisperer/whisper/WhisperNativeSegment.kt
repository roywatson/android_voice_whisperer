package com.roywatson.android_voice_whisperer.whisper

data class WhisperNativeSegment(
    val t0Ms: Long,
    val t1Ms: Long,
    val text: String,
    val confidence: Float
)
