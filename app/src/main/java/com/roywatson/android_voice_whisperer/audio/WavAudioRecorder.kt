package com.roywatson.android_voice_whisperer.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class WavAudioRecorder {

    sealed class RecordingStatus {
        data object Idle : RecordingStatus()
        data class Recording(val durationMs: Long, val amplitude: Float) : RecordingStatus()
        data class Paused(val durationMs: Long) : RecordingStatus()
        data class Finished(val outputFile: File, val durationMs: Long) : RecordingStatus()
        data class Error(val message: String) : RecordingStatus()
    }

    private val _status = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
    val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private var isPaused = false
    private var currentOutputFile: File? = null
    private var recordedDurationMs: Long = 0L

    val minBufferSize: Int by lazy {
        AudioRecord.getMinBufferSize(
            WavUtils.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
    }

    @SuppressLint("MissingPermission")
    fun startRecording(outputFile: File, scope: CoroutineScope): Boolean {
        if (isRecording) return false

        val bufferSize = minBufferSize.coerceAtLeast(WavUtils.SAMPLE_RATE * 2)
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                WavUtils.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _status.value = RecordingStatus.Error("Failed to initialize AudioRecord")
                return false
            }

            audioRecord?.startRecording()
            isRecording = true
            isPaused = false
            currentOutputFile = outputFile
            recordedDurationMs = 0L

            recordingJob = scope.launch(Dispatchers.IO) {
                recordAudioLoop(outputFile, bufferSize)
            }
            return true
        } catch (e: Exception) {
            _status.value = RecordingStatus.Error(e.message ?: "Unknown recording error")
            return false
        }
    }

    fun pauseRecording() {
        if (isRecording && !isPaused) {
            isPaused = true
            _status.value = RecordingStatus.Paused(recordedDurationMs)
        }
    }

    fun resumeRecording() {
        if (isRecording && isPaused) {
            isPaused = false
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null

        isRecording = false
        isPaused = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (ignored: Exception) {}
        audioRecord = null

        return currentOutputFile
    }

    private suspend fun recordAudioLoop(outputFile: File, bufferSize: Int) {
        val fos = FileOutputStream(outputFile)
        // Reserve 44 bytes for header
        WavUtils.writeWavHeader(fos, 0L)

        val byteBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = ShortArray(bufferSize / 2)
        val reusableOutputArray = ByteArray(bufferSize)
        var totalBytesWritten = 0L

        try {
            while (isRecording) {
                if (isPaused) {
                    delay(50)
                    continue
                }

                val shortsRead = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                if (shortsRead > 0) {
                    byteBuffer.clear()
                    var sumSquares = 0.0
                    for (i in 0 until shortsRead) {
                        val s = shortBuffer[i]
                        byteBuffer.putShort(s)
                        sumSquares += (s * s).toDouble()
                    }

                    val bytesToWrite = shortsRead * 2
                    byteBuffer.flip()
                    byteBuffer.get(reusableOutputArray, 0, bytesToWrite)
                    fos.write(reusableOutputArray, 0, bytesToWrite)
                    totalBytesWritten += bytesToWrite

                    // Calculate RMS normalized amplitude [0.0f, 1.0f]
                    val rms = sqrt(sumSquares / shortsRead)
                    val normalizedAmplitude = (rms / 32768.0).toFloat().coerceIn(0.0f, 1.0f)

                    // 16000 samples per second, 1 sample = 2 bytes
                    recordedDurationMs = (totalBytesWritten * 1000L) / (WavUtils.SAMPLE_RATE * 2L)

                    _status.value = RecordingStatus.Recording(recordedDurationMs, normalizedAmplitude)
                }
            }
        } catch (e: Exception) {
            _status.value = RecordingStatus.Error(e.message ?: "Error during audio streaming")
        } finally {
            try {
                fos.flush()
                fos.close()
            } catch (ignored: Exception) {}

            if (outputFile.exists() && outputFile.length() > WavUtils.HEADER_SIZE) {
                WavUtils.updateWavHeaderSizes(outputFile)
                _status.value = RecordingStatus.Finished(outputFile, recordedDurationMs)
            } else {
                _status.value = RecordingStatus.Idle
            }
        }
    }
}
