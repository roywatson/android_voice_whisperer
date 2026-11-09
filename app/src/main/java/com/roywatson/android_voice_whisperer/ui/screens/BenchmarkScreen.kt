package com.roywatson.android_voice_whisperer.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roywatson.android_voice_whisperer.VoiceWhispererApplication
import com.roywatson.android_voice_whisperer.domain.model.VoiceNote
import com.roywatson.android_voice_whisperer.whisper.WhisperModelType
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    notes: List<VoiceNote>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = LocalContext.current.applicationContext as VoiceWhispererApplication
    val whisperEngine = app.transcriptionEngine
    val modelManager = app.modelManager

    val systemInfo = remember { whisperEngine.getSystemInfo() }
    val isModelReady = remember { modelManager.isModelReady(WhisperModelType.TINY_EN_Q5_1.fileName) }

    // Aggregate RTF and transcription metrics
    val completedNotes = notes.filter { it.realTimeFactor > 0f }
    val avgRtf = if (completedNotes.isNotEmpty()) {
        completedNotes.map { it.realTimeFactor }.average().toFloat()
    } else {
        0.0f
    }

    val totalAudioSeconds = notes.sumOf { it.durationMs } / 1000f
    val totalInferenceSeconds = notes.sumOf { it.inferenceTimeMs } / 1000f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Benchmarks", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hardware Section
            item {
                BenchmarkCard(
                    title = "Device Hardware & Acceleration",
                    icon = Icons.Default.Memory
                ) {
                    val manufacturerFormatted = Build.MANUFACTURER.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                    }
                    BenchmarkRow("Device Model", "$manufacturerFormatted ${Build.MODEL}")
                    BenchmarkRow("Android OS", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    BenchmarkRow("Supported ABIs", Build.SUPPORTED_ABIS.joinToString(", "))
                    BenchmarkRow("CPU Cores", "${Runtime.getRuntime().availableProcessors()}")
                    BenchmarkRow("Native Diagnostics", systemInfo)
                }
            }

            // Model Section
            item {
                BenchmarkCard(
                    title = "Active Whisper & VAD Models",
                    icon = Icons.Default.Storage
                ) {
                    BenchmarkRow("Speech-to-Text Model", WhisperModelType.TINY_EN_Q5_1.modelName)
                    BenchmarkRow("Quantization", "Q5_1 (Integer + Scale Quantization)")
                    BenchmarkRow("Model Size", "~31 MB")
                    BenchmarkRow("Status", if (isModelReady) "Ready in local storage" else "Bundled / Initializing")
                    BenchmarkRow("VAD Model", "Silero VAD (LiteRT/TFLite) + Energy Fallback")
                }
            }

            // Performance Metrics Section
            item {
                BenchmarkCard(
                    title = "Measured Performance (B1 Baseline)",
                    icon = Icons.Default.Speed
                ) {
                    BenchmarkRow("Average RTF (Real-Time Factor)", if (avgRtf > 0f) String.format(Locale.US, "%.3fx", avgRtf) else "No recordings yet")
                    BenchmarkRow("RTF Definition", "Inference duration / Audio duration (Lower is faster)")
                    BenchmarkRow("Total Audio Transcribed", "${String.format(Locale.US, "%.1f", totalAudioSeconds)} seconds")
                    BenchmarkRow("Total On-Device Inference", "${String.format(Locale.US, "%.1f", totalInferenceSeconds)} seconds")
                    BenchmarkRow("Completed Notes Count", "${completedNotes.size}")
                }
            }

            // Benchmark Methodology Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Milestone B2 Benchmark Methodology",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To reproduce measurements across devices:\n" +
                                    "1. Put phone in Airplane Mode (ensuring 0 network activity).\n" +
                                    "2. Run test audio sets (LibriSpeech test-clean samples).\n" +
                                    "3. Record Real-Time Factor (RTF), Word Error Rate (WER), and peak memory (PSS).\n" +
                                    "4. Compare whisper-tiny.en vs whisper-base.en.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BenchmarkCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
fun BenchmarkRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.3f)
        )
    }
}
