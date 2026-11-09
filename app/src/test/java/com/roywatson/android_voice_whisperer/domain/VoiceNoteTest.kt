package com.roywatson.android_voice_whisperer.domain

import com.roywatson.android_voice_whisperer.domain.model.TranscriptSegment
import com.roywatson.android_voice_whisperer.domain.model.VoiceNote
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceNoteTest {

    @Test
    fun testWordCountCalculation() {
        val note = VoiceNote(
            title = "Test Note",
            audioFilePath = "/test.wav",
            fullTranscript = "On-device speech recognition with OpenAI Whisper running on Android."
        )
        assertEquals(9, note.wordCount)
    }

    @Test
    fun testDurationFormatting() {
        val note = VoiceNote(
            title = "Test Note",
            audioFilePath = "/test.wav",
            durationMs = 125000L // 2 min 5 sec
        )
        assertEquals("02:05", note.formattedDuration())
    }

    @Test
    fun testTranscriptSegmentTimeRange() {
        val segment = TranscriptSegment(
            id = 1,
            startMs = 5000L,
            endMs = 12000L,
            text = "Hello world"
        )
        assertEquals(7000L, segment.durationMs)
        assertEquals("00:05 - 00:12", segment.formattedTimeRange())
    }
}
