package com.roywatson.android_voice_whisperer.vad

import com.roywatson.android_voice_whisperer.domain.model.VadSegment
import com.roywatson.android_voice_whisperer.domain.repository.VoiceActivityDetector
import kotlin.math.sqrt

class EnergyVadDetector(
    private val frameSize: Int = 512, // 32ms at 16kHz
    private val energyThresholdRatio: Double = 2.5
) : VoiceActivityDetector {

    private var noiseFloor: Double = 100.0

    override fun isReady(): Boolean = true

    override suspend fun initialize(): Boolean = true

    override fun processFrame(samples: ShortArray, sampleRate: Int): Float {
        if (samples.isEmpty()) return 0.0f

        var sumSq = 0.0
        var zeroCrossings = 0
        for (i in samples.indices) {
            val s = samples[i].toDouble()
            sumSq += s * s
            if (i > 0 && ((samples[i] >= 0 && samples[i - 1] < 0) || (samples[i] < 0 && samples[i - 1] >= 0))) {
                zeroCrossings++
            }
        }

        val rms = sqrt(sumSq / samples.size)
        // Adaptively update noise floor during quiet periods
        if (rms < noiseFloor * 1.5) {
            noiseFloor = noiseFloor * 0.95 + rms * 0.05
        }

        val threshold = (noiseFloor * energyThresholdRatio).coerceAtLeast(120.0)
        val isSpeech = rms > threshold
        val prob = ((rms - threshold) / (threshold * 2.0)).toFloat().coerceIn(0.0f, 1.0f)
        return if (isSpeech) prob.coerceAtLeast(0.6f) else prob
    }

    override fun segmentAudio(samples: ShortArray, sampleRate: Int): List<VadSegment> {
        val segments = mutableListOf<VadSegment>()
        if (samples.isEmpty()) return segments

        val samplesPerMs = sampleRate / 1000
        var cursor = 0

        while (cursor + frameSize <= samples.size) {
            val frame = ShortArray(frameSize)
            System.arraycopy(samples, cursor, frame, 0, frameSize)
            val prob = processFrame(frame, sampleRate)
            val isSpeech = prob >= 0.5f

            val startMs = (cursor / samplesPerMs).toLong()
            val endMs = ((cursor + frameSize) / samplesPerMs).toLong()

            segments.add(
                VadSegment(
                    startSample = cursor,
                    endSample = cursor + frameSize,
                    startMs = startMs,
                    endMs = endMs,
                    isSpeech = isSpeech,
                    speechProbability = prob
                )
            )
            cursor += frameSize
        }
        return segments
    }

    override fun reset() {
        noiseFloor = 300.0
    }

    override fun release() {}
}
