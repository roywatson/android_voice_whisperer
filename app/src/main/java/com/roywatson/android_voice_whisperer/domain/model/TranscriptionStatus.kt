package com.roywatson.android_voice_whisperer.domain.model

enum class TranscriptionStatus {
    IDLE,
    RECORDING,
    TRANSCRIBING,
    COMPLETED,
    FAILED
}
