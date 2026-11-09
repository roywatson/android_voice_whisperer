package com.roywatson.android_voice_whisperer

import android.app.Application
import com.roywatson.android_voice_whisperer.audio.WavAudioPlayer
import com.roywatson.android_voice_whisperer.data.AppDatabase
import com.roywatson.android_voice_whisperer.data.ModelManager
import com.roywatson.android_voice_whisperer.data.VoiceNotesRepositoryImpl
import com.roywatson.android_voice_whisperer.domain.repository.VoiceNotesRepository
import com.roywatson.android_voice_whisperer.whisper.WhisperEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VoiceWhispererApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set

    lateinit var repository: VoiceNotesRepository
        private set

    lateinit var modelManager: ModelManager
        private set

    lateinit var transcriptionEngine: WhisperEngine
        private set

    lateinit var audioPlayer: WavAudioPlayer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase(this)
        repository = VoiceNotesRepositoryImpl(database)
        modelManager = ModelManager(this)
        transcriptionEngine = WhisperEngine(this)
        audioPlayer = WavAudioPlayer()

        // Background model setup
        applicationScope.launch {
            modelManager.setupInitialModels()
            val defaultModel = modelManager.getDefaultWhisperModelFile()
            transcriptionEngine.initialize(defaultModel)
        }
    }

    companion object {
        lateinit var instance: VoiceWhispererApplication
            private set
    }
}
