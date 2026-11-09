package com.roywatson.android_voice_whisperer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roywatson.android_voice_whisperer.VoiceWhispererApplication
import com.roywatson.android_voice_whisperer.domain.model.VoiceNote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VoiceWhispererApplication
    private val repository = app.repository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<VoiceNote>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.getAllNotes()
            } else {
                repository.searchNotes(query.trim())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteNote(note: VoiceNote) {
        viewModelScope.launch {
            repository.deleteNote(note.id)
            val file = File(note.audioFilePath)
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
