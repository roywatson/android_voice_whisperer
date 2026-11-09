package com.roywatson.android_voice_whisperer.domain.repository

import com.roywatson.android_voice_whisperer.domain.model.TranscriptSegment
import com.roywatson.android_voice_whisperer.domain.model.VoiceNote
import kotlinx.coroutines.flow.Flow

interface VoiceNotesRepository {
    fun getAllNotes(): Flow<List<VoiceNote>>
    suspend fun getNoteById(id: Long): VoiceNote?
    suspend fun insertNote(note: VoiceNote): Long
    suspend fun updateNote(note: VoiceNote)
    suspend fun deleteNote(id: Long)
    suspend fun getSegmentsForNote(noteId: Long): List<TranscriptSegment>
    suspend fun insertSegments(segments: List<TranscriptSegment>)
    fun searchNotes(query: String): Flow<List<VoiceNote>>
}
