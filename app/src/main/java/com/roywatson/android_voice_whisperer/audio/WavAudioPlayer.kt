package com.roywatson.android_voice_whisperer.audio

import android.media.MediaPlayer
import android.media.PlaybackParams
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class WavAudioPlayer {

    sealed class PlaybackState {
        data object Idle : PlaybackState()
        data class Playing(val currentPositionMs: Long, val durationMs: Long, val speed: Float) : PlaybackState()
        data class Paused(val currentPositionMs: Long, val durationMs: Long) : PlaybackState()
        data object Completed : PlaybackState()
        data class Error(val message: String) : PlaybackState()
    }

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var currentSpeed: Float = 1.0f
    private var currentScope: CoroutineScope? = null

    fun play(audioFile: File, scope: CoroutineScope, startPositionMs: Long = 0L) {
        currentScope = scope
        stop()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                if (startPositionMs > 0) {
                    seekTo(startPositionMs.toInt())
                }
                setSpeed(currentSpeed)
                start()

                setOnCompletionListener {
                    stopProgressTracking()
                    _playbackState.value = PlaybackState.Completed
                }

                setOnErrorListener { _, what, extra ->
                    stopProgressTracking()
                    _playbackState.value = PlaybackState.Error("Playback error: $what, $extra")
                    true
                }
            }

            startProgressTracking(scope)
        } catch (e: Exception) {
            stop()
            _playbackState.value = PlaybackState.Error(e.message ?: "Failed to play audio")
        }
    }

    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                stopProgressTracking()
                _playbackState.value = PlaybackState.Paused(
                    player.currentPosition.toLong(),
                    player.duration.toLong()
                )
            }
        }
    }

    fun resume(scope: CoroutineScope) {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                startProgressTracking(scope)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { player ->
            player.seekTo(positionMs.toInt())
            if (player.isPlaying) {
                _playbackState.value = PlaybackState.Playing(
                    positionMs,
                    player.duration.toLong(),
                    currentSpeed
                )
            } else {
                _playbackState.value = PlaybackState.Paused(
                    positionMs,
                    player.duration.toLong()
                )
            }
        }
    }

    fun setSpeed(speed: Float) {
        currentSpeed = speed
        mediaPlayer?.let { player ->
            try {
                val params = player.playbackParams ?: PlaybackParams()
                params.speed = speed
                player.playbackParams = params
            } catch (ignored: Exception) {}
        }
    }

    fun stop() {
        stopProgressTracking()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (ignored: Exception) {}
        mediaPlayer = null
        _playbackState.value = PlaybackState.Idle
    }

    private fun startProgressTracking(scope: CoroutineScope) {
        stopProgressTracking()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive && mediaPlayer?.isPlaying == true) {
                val current = mediaPlayer?.currentPosition?.toLong() ?: 0L
                val total = mediaPlayer?.duration?.toLong() ?: 0L
                _playbackState.value = PlaybackState.Playing(current, total, currentSpeed)
                delay(100)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
