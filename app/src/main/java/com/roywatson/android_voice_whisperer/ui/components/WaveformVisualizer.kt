package com.roywatson.android_voice_whisperer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun WaveformVisualizer(
    amplitude: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    // Ring buffer of historical amplitudes for smooth wave movement
    val waveAmplitudes = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.1f) } } }

    LaunchedEffect(amplitude, isRecording) {
        if (isRecording) {
            waveAmplitudes.removeAt(0)
            val jitter = if (amplitude > 0.05f) (Random.nextFloat() * 0.15f) else 0.0f
            val value = (amplitude + jitter).coerceIn(0.08f, 1.0f)
            waveAmplitudes.add(value)
        } else {
            for (i in 0 until waveAmplitudes.size) {
                waveAmplitudes[i] = 0.08f
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val width = size.width
        val height = size.height
        val barWidth = (width / (barCount * 1.5f))
        val spacing = barWidth * 0.5f

        val startX = (width - ((barCount * barWidth) + ((barCount - 1) * spacing))) / 2.0f

        for (i in 0 until barCount) {
            val amp = waveAmplitudes.getOrElse(i) { 0.1f }
            val barHeight = (height * amp).coerceAtLeast(6.dp.toPx())
            val x = startX + i * (barWidth + spacing)
            val y = (height - barHeight) / 2.0f

            drawRoundRect(
                color = if (amp > 0.25f) primaryColor else secondaryColor.copy(alpha = 0.6f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2.0f, barWidth / 2.0f)
            )
        }
    }
}
