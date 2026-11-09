package com.roywatson.android_voice_whisperer.domain.model

data class BenchmarkStats(
    val deviceModel: String,
    val androidVersion: String,
    val cpuArchitecture: String,
    val cpuCores: Int,
    val neonSupported: Boolean,
    val nativeSystemInfo: String,
    val modelName: String,
    val modelSizeBytes: Long,
    val averageRtf: Float,
    val totalTranscribedNotes: Int,
    val totalTranscribedSeconds: Float,
    val totalInferenceSeconds: Float
)
