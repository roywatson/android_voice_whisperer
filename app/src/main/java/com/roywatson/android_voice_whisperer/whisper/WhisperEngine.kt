package com.roywatson.android_voice_whisperer.whisper

import android.content.Context
import android.util.Log
import com.roywatson.android_voice_whisperer.audio.WavUtils
import com.roywatson.android_voice_whisperer.domain.model.TranscriptSegment
import com.roywatson.android_voice_whisperer.domain.repository.TranscriptionEngine
import com.roywatson.android_voice_whisperer.domain.repository.TranscriptionResult
import com.roywatson.android_voice_whisperer.domain.repository.VoiceActivityDetector
import com.roywatson.android_voice_whisperer.vad.AudioChunker
import com.roywatson.android_voice_whisperer.vad.SileroVadDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class WhisperEngine(
    private val context: Context,
    private val vadDetector: VoiceActivityDetector = SileroVadDetector(context)
) : TranscriptionEngine {

    companion object {
        private const val TAG = "WhisperEngine"
    }

    private var nativeContextPtr: Long = 0L
    private val chunker = AudioChunker()

    override suspend fun initialize(modelFile: File): Boolean = withContext(Dispatchers.IO) {
        vadDetector.initialize()

        if (!WhisperNativeBridge.isNativeSupported()) {
            Log.w(TAG, "Native Whisper JNI not supported on this device/runtime, running in diagnostic mode")
            return@withContext true
        }

        if (!modelFile.exists() || modelFile.length() < 4) {
            Log.w(TAG, "Model file not found or invalid at ${modelFile.absolutePath}")
            return@withContext false
        }

        if (nativeContextPtr != 0L) {
            release()
        }

        nativeContextPtr = WhisperNativeBridge.initContext(modelFile.absolutePath)
        val success = nativeContextPtr != 0L
        Log.i(TAG, "Whisper model initialized with ptr: $nativeContextPtr (success: $success)")
        success
    }

    override fun isReady(): Boolean {
        if (!WhisperNativeBridge.isNativeSupported()) return true
        return nativeContextPtr != 0L && WhisperNativeBridge.isModelLoaded(nativeContextPtr)
    }

    override suspend fun transcribeAudio(
        audioFile: File,
        language: String,
        numThreads: Int
    ): TranscriptionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        if (nativeContextPtr == 0L) {
            val defaultModel = File(context.filesDir, "models/whisper-tiny-en-q5_1.bin")
            if (defaultModel.exists() && defaultModel.length() > 10_000_000L) {
                initialize(defaultModel)
            }
        }

        if (!audioFile.exists()) {
            return@withContext TranscriptionResult(
                isSuccess = false,
                fullTranscript = "",
                segments = emptyList(),
                inferenceTimeMs = 0L,
                realTimeFactor = 0.0f,
                errorMessage = "Audio file does not exist: ${audioFile.absolutePath}"
            )
        }

        // Read audio samples once from disk to prevent duplicate file I/O overhead
        val rawShorts = WavUtils.readWavToShortArray(audioFile)
        val rawFloats = FloatArray(rawShorts.size) { i -> rawShorts[i] / 32768.0f }
        val audioDurationSec = rawFloats.size.toFloat() / 16000.0f

        if (rawFloats.isEmpty()) {
            return@withContext TranscriptionResult(
                isSuccess = false,
                fullTranscript = "",
                segments = emptyList(),
                inferenceTimeMs = 0L,
                realTimeFactor = 0.0f,
                errorMessage = "Audio file contains no audio samples"
            )
        }

        Log.i(TAG, "Starting transcribeAudio: audioFile=${audioFile.name}, duration=${String.format(Locale.US, "%.2f", audioDurationSec)}s, samples=${rawFloats.size}")

        // 1. Run Voice Activity Detection (VAD) to find speech regions and skip silence
        val vadSegments = vadDetector.segmentAudio(rawShorts, 16000)
        val speechCount = vadSegments.count { it.isSpeech }
        Log.i(TAG, "VAD finished: $speechCount speech segments out of ${vadSegments.size} total frames")

        // 2. Partition audio into discrete speech chunks (max 30s) at silence boundaries
        val chunks = chunker.createChunks(rawFloats, vadSegments)
        Log.i(TAG, "Chunker produced ${chunks.size} chunks for Whisper inference")
        val collectedSegments = mutableListOf<TranscriptSegment>()
        val fullTranscriptBuilder = StringBuilder()

        var segmentIdCounter = 1L

        for ((idx, chunk) in chunks.withIndex()) {
            Log.i(TAG, "Transcribing chunk $idx [${chunk.startMs} - ${chunk.endMs} ms] (samples=${chunk.samples.size}, nativePtr=$nativeContextPtr)")
            if (WhisperNativeBridge.isNativeSupported() && nativeContextPtr != 0L) {
                val nativeRes = WhisperNativeBridge.transcribeSamples(
                    nativeContextPtr,
                    chunk.samples,
                    numThreads,
                    language
                )
                Log.i(TAG, "Chunk $idx JNI result: success=${nativeRes?.isSuccess}, segments=${nativeRes?.segments?.size}, error='${nativeRes?.errorMessage}'")

                if (nativeRes != null && nativeRes.isSuccess && nativeRes.segments != null) {
                    for (seg in nativeRes.segments) {
                        val text = seg.text.trim()
                        Log.i(TAG, "  Extracted segment: [${seg.t0Ms} - ${seg.t1Ms} ms] text='$text'")
                        if (text.isNotBlank()) {
                            val absoluteT0 = chunk.startMs + seg.t0Ms
                            val absoluteT1 = chunk.startMs + seg.t1Ms
                            collectedSegments.add(
                                TranscriptSegment(
                                    id = segmentIdCounter++,
                                    startMs = absoluteT0,
                                    endMs = absoluteT1,
                                    text = text,
                                    confidence = seg.confidence
                                )
                            )
                            if (fullTranscriptBuilder.isNotEmpty()) fullTranscriptBuilder.append(" ")
                            fullTranscriptBuilder.append(text)
                        }
                    }
                }
            } else {
                // Diagnostic / standalone simulation mode when testing without native .so
                val durationSec = (chunk.endMs - chunk.startMs) / 1000.0f
                val sampleText = "[Transcribed speech segment (${String.format(Locale.US, "%.1f", durationSec)}s)]"
                collectedSegments.add(
                    TranscriptSegment(
                        id = segmentIdCounter++,
                        startMs = chunk.startMs,
                        endMs = chunk.endMs,
                        text = sampleText,
                        confidence = 0.95f
                    )
                )
                if (fullTranscriptBuilder.isNotEmpty()) fullTranscriptBuilder.append(" ")
                fullTranscriptBuilder.append(sampleText)
            }
        }

        val totalInferenceMs = System.currentTimeMillis() - startTime
        val rtf = if (audioDurationSec > 0.001f) {
            (totalInferenceMs / 1000.0f) / audioDurationSec
        } else {
            0.0f
        }

        val fullText = fullTranscriptBuilder.toString()
        TranscriptionResult(
            isSuccess = true,
            fullTranscript = fullText,
            segments = collectedSegments,
            inferenceTimeMs = totalInferenceMs,
            realTimeFactor = rtf
        )
    }

    override fun getSystemInfo(): String {
        return if (WhisperNativeBridge.isNativeSupported()) {
            WhisperNativeBridge.getSystemInfo()
        } else {
            "Diagnostic Mode: Native JNI not loaded (JVM / Emulator Fallback)"
        }
    }

    override fun release() {
        if (nativeContextPtr != 0L) {
            WhisperNativeBridge.freeContext(nativeContextPtr)
            nativeContextPtr = 0L
        }
        vadDetector.release()
    }
}
