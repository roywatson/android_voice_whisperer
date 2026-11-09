package com.roywatson.android_voice_whisperer.whisper

import android.util.Log

object WhisperNativeBridge {
    private const val TAG = "WhisperNativeBridge"
    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("whisper_jni")
            isLibraryLoaded = true
            Log.i(TAG, "Native library 'whisper_jni' loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load 'whisper_jni': ${e.message}")
            isLibraryLoaded = false
        }
    }

    fun isNativeSupported(): Boolean = isLibraryLoaded

    external fun initContext(modelPath: String): Long
    external fun freeContext(contextPtr: Long)
    external fun isModelLoaded(contextPtr: Long): Boolean
    external fun transcribeSamples(
        contextPtr: Long,
        samples: FloatArray,
        nThreads: Int,
        language: String
    ): WhisperNativeResult?
    external fun getSystemInfo(): String
}
