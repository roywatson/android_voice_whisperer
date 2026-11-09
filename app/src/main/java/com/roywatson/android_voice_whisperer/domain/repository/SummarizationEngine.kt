package com.roywatson.android_voice_whisperer.domain.repository

/**
 * Architectural Seam for Step B4: On-Device Summaries & Action Items.
 * Backed by interchangeable local LLM backends:
 * 1. Gemini Nano via ML Kit GenAI APIs (on supported hardware)
 * 2. Gemma via LiteRT-LM
 * 3. Quantized GGUF models via llama.cpp
 */
data class SummaryResult(
    val title: String,
    val summary: String,
    val actionItems: List<String>,
    val tokensPerSecond: Float = 0.0f,
    val timeToFirstTokenMs: Long = 0L
)

interface SummarizationEngine {
    val backendName: String
    suspend fun isSupported(): Boolean
    suspend fun generateSummary(transcript: String): Result<SummaryResult>
}
