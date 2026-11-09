package com.roywatson.android_voice_whisperer.whisper

enum class WhisperModelType(
    val modelName: String,
    val fileName: String,
    val approximateSizeBytes: Long,
    val recommendedThreads: Int,
    val description: String
) {
    TINY_EN_Q5_1(
        modelName = "Whisper Tiny.en (Q5_1)",
        fileName = "whisper-tiny-en-q5_1.bin",
        approximateSizeBytes = 31 * 1024 * 1024L,
        recommendedThreads = 4,
        description = "Fastest, lowest memory (~31MB). Ideal for real-time transcription on mobile."
    ),
    BASE_EN_Q5_1(
        modelName = "Whisper Base.en (Q5_1)",
        fileName = "whisper-base-en-q5_1.bin",
        approximateSizeBytes = 57 * 1024 * 1024L,
        recommendedThreads = 4,
        description = "Balanced speed and accuracy (~57MB)."
    ),
    SMALL_EN_Q5_1(
        modelName = "Whisper Small.en (Q5_1)",
        fileName = "whisper-small-en-q5_1.bin",
        approximateSizeBytes = 180 * 1024 * 1024L,
        recommendedThreads = 6,
        description = "High accuracy (~180MB). Best for foreground service post-processing."
    )
}
