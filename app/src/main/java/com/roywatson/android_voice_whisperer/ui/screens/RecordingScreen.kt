package com.roywatson.android_voice_whisperer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roywatson.android_voice_whisperer.domain.model.AudioRecordingState
import com.roywatson.android_voice_whisperer.ui.components.WaveformVisualizer
import java.util.Locale

@Composable
fun RecordingScreen(
    state: AudioRecordingState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header / Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel recording"
                    )
                }
            }

            // Main recording visualization
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                when (state) {
                    is AudioRecordingState.Idle -> {
                        Text(
                            text = "Ready to record",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    is AudioRecordingState.Recording -> {
                        // Digital timer
                        Text(
                            text = formatDuration(state.durationMs),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // VAD speech status indicator
                        Surface(
                            color = if (state.isSpeechDetected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (state.isSpeechDetected) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (state.isSpeechDetected) "Speech Detected (VAD)" else "Silence",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (state.isSpeechDetected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Live Waveform
                        WaveformVisualizer(
                            amplitude = state.currentAmplitude,
                            isRecording = true
                        )
                    }
                    is AudioRecordingState.Paused -> {
                        Text(
                            text = formatDuration(state.durationMs),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Recording Paused",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        WaveformVisualizer(
                            amplitude = 0.05f,
                            isRecording = false
                        )
                    }
                    is AudioRecordingState.Transcribing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 5.dp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Transcribing on-device with Whisper...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "VAD audio chunking & JNI inference running",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is AudioRecordingState.Error -> {
                        Text(
                            text = "Error: ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Controls bottom bar
            if (state is AudioRecordingState.Recording || state is AudioRecordingState.Paused) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pause / Resume button
                    OutlinedIconButton(
                        onClick = {
                            if (state is AudioRecordingState.Recording) onPause() else onResume()
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (state is AudioRecordingState.Recording) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (state is AudioRecordingState.Recording) "Pause" else "Resume"
                        )
                    }

                    // Stop & Transcribe button (Primary)
                    FilledIconButton(
                        onClick = onStop,
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = "Stop and Transcribe",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
