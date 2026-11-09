package com.roywatson.android_voice_whisperer.domain.model

import java.util.Locale

data class TranscriptSegment(
    val id: Long = 0,
    val noteId: Long = 0,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val speakerTag: String? = null,
    val confidence: Float = 1.0f
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)

    fun formattedTimeRange(): String {
        return "${formatTimestamp(startMs)} - ${formatTimestamp(endMs)}"
    }

    private fun formatTimestamp(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
