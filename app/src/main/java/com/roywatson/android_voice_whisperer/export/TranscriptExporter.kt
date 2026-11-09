package com.roywatson.android_voice_whisperer.export

import android.content.Context
import android.content.Intent
import com.roywatson.android_voice_whisperer.domain.model.VoiceNote
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TranscriptExporter {

    enum class ExportFormat(val extension: String, val mimeType: String) {
        TXT("txt", "text/plain"),
        MARKDOWN("md", "text/markdown"),
        SRT("srt", "application/x-subrip")
    }

    fun exportToPlainText(note: VoiceNote): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(note.createdAt))

        val sb = StringBuilder()
        sb.append(note.title).append("\n")
        sb.append("Date: ").append(formattedDate).append("\n")
        sb.append("Duration: ").append(note.formattedDuration()).append("\n")
        sb.append("Word Count: ").append(note.wordCount).append("\n")
        sb.append("Model: ").append(note.modelUsed).append("\n")
        sb.append("----------------------------------------\n\n")

        if (note.segments.isNotEmpty()) {
            for (seg in note.segments) {
                val speakerPrefix = if (!seg.speakerTag.isNullOrBlank()) "[${seg.speakerTag}] " else ""
                sb.append("[${seg.formattedTimeRange()}] ").append(speakerPrefix).append(seg.text).append("\n")
            }
        } else {
            sb.append(note.fullTranscript).append("\n")
        }

        if (!note.summary.isNullOrBlank()) {
            sb.append("\nSummary:\n").append(note.summary).append("\n")
        }

        if (note.actionItems.isNotEmpty()) {
            sb.append("\nAction Items:\n")
            for (item in note.actionItems) {
                sb.append("• ").append(item).append("\n")
            }
        }

        return sb.toString()
    }

    fun exportToMarkdown(note: VoiceNote): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(note.createdAt))

        val sb = StringBuilder()
        sb.append("# ").append(note.title).append("\n\n")
        sb.append("- **Date:** ").append(formattedDate).append("\n")
        sb.append("- **Duration:** ").append(note.formattedDuration()).append("\n")
        sb.append("- **Word Count:** ").append(note.wordCount).append("\n")
        sb.append("- **Model:** `").append(note.modelUsed).append("`\n")
        sb.append("- **Real-Time Factor (RTF):** `").append(String.format(Locale.US, "%.2f", note.realTimeFactor)).append("`\n\n")

        sb.append("## Transcript\n\n")
        if (note.segments.isNotEmpty()) {
            for (seg in note.segments) {
                val speakerPrefix = if (!seg.speakerTag.isNullOrBlank()) "**${seg.speakerTag}:** " else ""
                sb.append("> `").append(seg.formattedTimeRange()).append("`  \n")
                sb.append("> ").append(speakerPrefix).append(seg.text).append("\n\n")
            }
        } else {
            sb.append(note.fullTranscript).append("\n\n")
        }

        if (!note.summary.isNullOrBlank()) {
            sb.append("## Summary\n\n").append(note.summary).append("\n\n")
        }

        if (note.actionItems.isNotEmpty()) {
            sb.append("## Action Items\n\n")
            for (item in note.actionItems) {
                sb.append("- [ ] ").append(item).append("\n")
            }
            sb.append("\n")
        }

        return sb.toString()
    }

    fun exportToSrt(note: VoiceNote): String {
        val sb = StringBuilder()
        var index = 1
        for (seg in note.segments) {
            sb.append(index++).append("\n")
            sb.append(formatSrtTimestamp(seg.startMs)).append(" --> ").append(formatSrtTimestamp(seg.endMs)).append("\n")
            if (!seg.speakerTag.isNullOrBlank()) {
                sb.append("<v ").append(seg.speakerTag).append(">")
            }
            sb.append(seg.text).append("\n\n")
        }
        return sb.toString()
    }

    private fun formatSrtTimestamp(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun shareNote(context: Context, note: VoiceNote, format: ExportFormat) {
        val text = when (format) {
            ExportFormat.TXT -> exportToPlainText(note)
            ExportFormat.MARKDOWN -> exportToMarkdown(note)
            ExportFormat.SRT -> exportToSrt(note)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_SUBJECT, note.title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Voice Note via"))
    }
}
