package com.roywatson.android_voice_whisperer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roywatson.android_voice_whisperer.audio.WavAudioPlayer
import com.roywatson.android_voice_whisperer.domain.model.VoiceNote
import com.roywatson.android_voice_whisperer.export.TranscriptExporter
import com.roywatson.android_voice_whisperer.ui.components.AudioPlayerBar
import com.roywatson.android_voice_whisperer.ui.components.TranscriptSegmentItem
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    note: VoiceNote,
    audioPlayer: WavAudioPlayer,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showExportMenu by remember { mutableStateOf(false) }

    val playbackState by audioPlayer.playbackState.collectAsState()

    val currentPositionMs = when (val s = playbackState) {
        is WavAudioPlayer.PlaybackState.Playing -> s.currentPositionMs
        is WavAudioPlayer.PlaybackState.Paused -> s.currentPositionMs
        else -> 0L
    }

    val isPlaying = playbackState is WavAudioPlayer.PlaybackState.Playing

    val playbackSpeed = when (val s = playbackState) {
        is WavAudioPlayer.PlaybackState.Playing -> s.speed
        else -> 1.0f
    }

    // Stop player when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = note.title,
                        maxLines = 1,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Share or Export")
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export as Plain Text (.txt)") },
                                onClick = {
                                    showExportMenu = false
                                    TranscriptExporter.shareNote(context, note, TranscriptExporter.ExportFormat.TXT)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export as Markdown (.md)") },
                                onClick = {
                                    showExportMenu = false
                                    TranscriptExporter.shareNote(context, note, TranscriptExporter.ExportFormat.MARKDOWN)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export as Subtitles (.srt)") },
                                onClick = {
                                    showExportMenu = false
                                    TranscriptExporter.shareNote(context, note, TranscriptExporter.ExportFormat.SRT)
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Audio Player Bar
            AudioPlayerBar(
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = note.durationMs,
                playbackSpeed = playbackSpeed,
                onPlayPause = {
                    val file = File(note.audioFilePath)
                    if (isPlaying) {
                        audioPlayer.pause()
                    } else if (playbackState is WavAudioPlayer.PlaybackState.Paused) {
                        audioPlayer.resume(coroutineScope)
                    } else if (file.exists()) {
                        audioPlayer.play(file, coroutineScope, currentPositionMs)
                    }
                },
                onSeekTo = { pos ->
                    val file = File(note.audioFilePath)
                    if (playbackState is WavAudioPlayer.PlaybackState.Idle && file.exists()) {
                        audioPlayer.play(file, coroutineScope, pos)
                    } else {
                        audioPlayer.seekTo(pos)
                    }
                },
                onSpeedChange = { speed ->
                    audioPlayer.setSpeed(speed)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Metadata chips row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("⏱ ${note.formattedDuration()}") }
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text("📝 ${note.wordCount} words") }
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text("⚡ RTF: ${String.format(Locale.US, "%.2f", note.realTimeFactor)}") }
                    )
                }
            }

            // Seam for B4: Summary & Action Items Card
            if (!note.summary.isNullOrBlank() || note.actionItems.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "On-Device AI Summary",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            note.summary?.let { summary ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = summary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Transcript Segments Section Header
            item {
                Text(
                    text = "Transcript (${note.segments.size} segments)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (note.segments.isEmpty()) {
                item {
                    if (note.fullTranscript.isNotBlank()) {
                        Text(
                            text = note.fullTranscript,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                        )
                    } else {
                        Text(
                            text = "No transcript segments available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(note.segments, key = { it.id }) { seg ->
                    val isSegmentActive = currentPositionMs in seg.startMs..seg.endMs
                    TranscriptSegmentItem(
                        segment = seg,
                        isActive = isSegmentActive,
                        onClick = {
                            val file = File(note.audioFilePath)
                            if (file.exists()) {
                                audioPlayer.play(file, coroutineScope, seg.startMs)
                            }
                        }
                    )
                }
            }

            // Extra space for the bottom player bar
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
