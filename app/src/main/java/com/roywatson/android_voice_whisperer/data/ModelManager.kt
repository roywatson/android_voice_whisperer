package com.roywatson.android_voice_whisperer.data

import android.content.Context
import android.util.Log
import com.roywatson.android_voice_whisperer.whisper.WhisperModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        const val MODELS_DIR_NAME = "models"
        const val SILERO_VAD_MODEL_NAME = "silero_vad.tflite"
    }

    val modelsDir: File by lazy {
        File(context.filesDir, MODELS_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    fun getModelFile(fileName: String): File {
        return File(modelsDir, fileName)
    }

    fun getDefaultWhisperModelFile(): File {
        return getModelFile(WhisperModelType.TINY_EN_Q5_1.fileName)
    }

    fun getSileroVadModelFile(): File {
        return getModelFile(SILERO_VAD_MODEL_NAME)
    }

    fun isModelReady(fileName: String): Boolean {
        val file = getModelFile(fileName)
        return file.exists() && file.length() > 10_000_000L
    }

    /**
     * Ensures required bundled models are extracted from APK assets to the app's internal filesDir.
     */
    suspend fun setupInitialModels(): Boolean = withContext(Dispatchers.IO) {
        var allOk = true

        // 1. Check & copy Whisper tiny model (~32MB)
        val whisperDest = getDefaultWhisperModelFile()
        val whisperAssetPath = "models/${WhisperModelType.TINY_EN_Q5_1.fileName}"
        if (!whisperDest.exists() || whisperDest.length() < 10_000_000L) {
            Log.i(TAG, "Extracting Whisper model from APK assets to ${whisperDest.absolutePath}...")
            val copied = copyAssetFileSafely(whisperAssetPath, whisperDest)
            if (!copied) {
                Log.e(TAG, "Whisper model asset $whisperAssetPath missing from APK!")
                allOk = false
            } else {
                Log.i(TAG, "Whisper model extracted successfully (${whisperDest.length()} bytes)")
            }
        }

        // 2. Check & copy Silero VAD model (~1.2MB)
        val vadDest = getSileroVadModelFile()
        val vadAssetPath = "models/$SILERO_VAD_MODEL_NAME"
        if (!vadDest.exists() || vadDest.length() < 100_000L) {
            Log.i(TAG, "Extracting Silero VAD model from APK assets...")
            val copied = copyAssetFileSafely(vadAssetPath, vadDest)
            if (!copied) {
                Log.w(TAG, "Silero VAD model not found in assets, adaptive Energy VAD fallback will be active")
            } else {
                Log.i(TAG, "Silero VAD model extracted successfully (${vadDest.length()} bytes)")
            }
        }

        allOk
    }

    private fun copyAssetFileSafely(assetPath: String, destFile: File): Boolean {
        return try {
            val input: InputStream = context.assets.open(assetPath)
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
            input.close()
            Log.i(TAG, "Copied asset $assetPath to ${destFile.absolutePath} (${destFile.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.i(TAG, "Asset not found: $assetPath (${e.message})")
            false
        }
    }
}
