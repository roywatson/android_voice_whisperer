package com.roywatson.android_voice_whisperer.export

import com.roywatson.android_voice_whisperer.domain.model.TranscriptSegment
import com.roywatson.android_voice_whisperer.domain.model.VoiceNote
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptExporterTest {

    private val testNote = VoiceNote(
        id = 42L,
        title = "Sprint Planning Meeting",
        createdAt = 1731110400000L, // Fixed epoch timestamp
        durationMs = 15000L,
        audioFilePath = "/data/recordings/test.wav",
        fullTranscript = "Hello team, welcome to sprint planning.",
        realTimeFactor = 0.035f,
        modelUsed = "whisper-tiny.en",
        summary = "Sprint goals aligned.",
        actionItems = listOf("Review PR #12", "Benchmark on S25 Ultra"),
        segments = listOf(
            TranscriptSegment(
                id = 1L,
                noteId = 42L,
                startMs = 500L,
                endMs = 4500L,
                text = "Hello team,",
                speakerTag = "Speaker 1"
            ),
            TranscriptSegment(
                id = 2L,
                noteId = 42L,
                startMs = 5000L,
                endMs = 14000L,
                text = "welcome to sprint planning.",
                speakerTag = "Speaker 2"
            )
        )
    )

    @Test
    fun testExportToPlainText() {
        val text = TranscriptExporter.exportToPlainText(testNote)

        assertTrue(text.contains("Sprint Planning Meeting"))
        assertTrue(text.contains("Speaker 1"))
        assertTrue(text.contains("Hello team,"))
        assertTrue(text.contains("welcome to sprint planning."))
        assertTrue(text.contains("Sprint goals aligned."))
        assertTrue(text.contains("• Review PR #12"))
        assertTrue(text.contains("• Benchmark on S25 Ultra"))
    }

    @Test
    fun testExportToMarkdown() {
        val md = TranscriptExporter.exportToMarkdown(testNote)

        assertTrue(md.contains("# Sprint Planning Meeting"))
        assertTrue(md.contains("- **Model:** `whisper-tiny.en`"))
        assertTrue(md.contains("**Speaker 1:** Hello team,"))
        assertTrue(md.contains("## Summary"))
        assertTrue(md.contains("## Action Items"))
        assertTrue(md.contains("- [ ] Review PR #12"))
        assertTrue(md.contains("- [ ] Benchmark on S25 Ultra"))
    }

    @Test
    fun testExportToSrt() {
        val srt = TranscriptExporter.exportToSrt(testNote)

        // Segment 1: 500ms -> 00:00:00,500 --> 00:00:04,500
        assertTrue(srt.contains("1\n00:00:00,500 --> 00:00:04,500"))
        assertTrue(srt.contains("<v Speaker 1>Hello team,"))

        // Segment 2: 5000ms -> 00:00:05,000 --> 00:00:14,000
        assertTrue(srt.contains("2\n00:00:05,000 --> 00:00:14,000"))
        assertTrue(srt.contains("<v Speaker 2>welcome to sprint planning."))
    }
}
