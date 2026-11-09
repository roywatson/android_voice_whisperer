package com.roywatson.android_voice_whisperer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun AudioPlayerBar(
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSeconds = durationMs / 1000
    val currentSeconds = currentPositionMs / 1000

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Slider
            Slider(
                value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                onValueChange = { fraction ->
                    onSeekTo((fraction * durationMs).toLong())
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Duration labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTime(totalSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Player controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Playback speed button
                TextButton(
                    onClick = {
                        val nextSpeed = when (playbackSpeed) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2.0f
                            else -> 1.0f
                        }
                        onSpeedChange(nextSpeed)
                    }
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%.2f", playbackSpeed).trimEnd('0').trimEnd('.')}x",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Rewind 5s
                IconButton(onClick = { onSeekTo((currentPositionMs - 5000L).coerceAtLeast(0L)) }) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind 5 seconds"
                    )
                }

                // Play / Pause FAB
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Forward 5s
                IconButton(onClick = { onSeekTo((currentPositionMs + 5000L).coerceAtMost(durationMs)) }) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward 5 seconds"
                    )
                }

                // Spacer to balance the speed button
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}
