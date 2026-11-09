package com.roywatson.android_voice_whisperer.vad

import android.content.Context
import com.roywatson.android_voice_whisperer.domain.model.VadSegment
import com.roywatson.android_voice_whisperer.domain.repository.VoiceActivityDetector
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class SileroVadDetector(
    private val context: Context,
    private val modelFile: File? = null
) : VoiceActivityDetector {

    private var interpreter: Interpreter? = null
    private val energyFallback = EnergyVadDetector()

    // Silero VAD state buffers [2, 1, 64]
    private var state = Array(2) { Array(1) { FloatArray(64) } }

    override fun isReady(): Boolean = interpreter != null || energyFallback.isReady()

    override suspend fun initialize(): Boolean {
        val targetFile = modelFile ?: File(context.filesDir, "models/silero_vad.tflite")
        if (targetFile.exists() && targetFile.length() > 100_000L) {
            try {
                val byteBuffer = loadModelFile(targetFile)
                val options = Interpreter.Options().apply {
                    setNumThreads(2)
                }
                interpreter = Interpreter(byteBuffer, options)
                reset()
                return true
            } catch (e: Exception) {
                interpreter = null
            }
        }
        return energyFallback.initialize()
    }

    override fun processFrame(samples: ShortArray, sampleRate: Int): Float {
        val tflite = interpreter
        if (tflite == null) {
            return energyFallback.processFrame(samples, sampleRate)
        }

        try {
            // Silero expects 512 float samples for 16kHz
            val inputFloats = Array(1) { FloatArray(512) }
            val count = minOf(samples.size, 512)
            for (i in 0 until count) {
                inputFloats[0][i] = samples[i] / 32768.0f
            }

            val inputs = arrayOf<Any>(
                inputFloats,
                state,
                longArrayOf(sampleRate.toLong())
            )

            val outputProb = Array(1) { FloatArray(1) }
            val outputState = Array(2) { Array(1) { FloatArray(64) } }

            val outputs = mutableMapOf<Int, Any>(
                0 to outputProb,
                1 to outputState
            )

            tflite.runForMultipleInputsOutputs(inputs, outputs)
            state = outputState
            return outputProb[0][0].coerceIn(0.0f, 1.0f)
        } catch (e: Exception) {
            return energyFallback.processFrame(samples, sampleRate)
        }
    }

    override fun segmentAudio(samples: ShortArray, sampleRate: Int): List<VadSegment> {
        val tflite = interpreter
        if (tflite == null) {
            return energyFallback.segmentAudio(samples, sampleRate)
        }

        val segments = mutableListOf<VadSegment>()
        val frameSize = 512
        val samplesPerMs = sampleRate / 1000
        var cursor = 0
        reset()

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
        state = Array(2) { Array(1) { FloatArray(64) } }
        energyFallback.reset()
    }

    override fun release() {
        interpreter?.close()
        interpreter = null
        energyFallback.release()
    }

    private fun loadModelFile(file: File): ByteBuffer {
        FileInputStream(file).use { fis ->
            val channel = fis.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()).apply {
                order(ByteOrder.nativeOrder())
            }
        }
    }
}
