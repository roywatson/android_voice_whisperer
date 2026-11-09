package com.roywatson.android_voice_whisperer.whisper

data class WhisperNativeResult(
    val isSuccess: Boolean,
    val errorMessage: String,
    val segments: Array<WhisperNativeSegment>?,
    val inferenceTimeMs: Long,
    val realTimeFactor: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WhisperNativeResult

        if (isSuccess != other.isSuccess) return false
        if (errorMessage != other.errorMessage) return false
        if (segments != null) {
            if (other.segments == null) return false
            if (!segments.contentEquals(other.segments)) return false
        } else if (other.segments != null) return false
        if (inferenceTimeMs != other.inferenceTimeMs) return false
        if (realTimeFactor != other.realTimeFactor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isSuccess.hashCode()
        result = 31 * result + errorMessage.hashCode()
        result = 31 * result + (segments?.contentHashCode() ?: 0)
        result = 31 * result + inferenceTimeMs.hashCode()
        result = 31 * result + realTimeFactor.hashCode()
        return result
    }
}
