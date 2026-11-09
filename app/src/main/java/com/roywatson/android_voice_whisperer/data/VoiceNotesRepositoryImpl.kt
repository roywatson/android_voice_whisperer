package com.roywatson.android_voice_whisperer.data

import com.roywatson.android_voice_whisperer.domain.model.TranscriptSegment
import com.roywatson.android_voice_whisperer.domain.model.VoiceNote
import com.roywatson.android_voice_whisperer.domain.repository.VoiceNotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VoiceNotesRepositoryImpl(
    private val database: AppDatabase
) : VoiceNotesRepository {

    override fun getAllNotes(): Flow<List<VoiceNote>> {
        return database.dbChangeTrigger.map {
            database.getAllNotes()
        }
    }

    override suspend fun getNoteById(id: Long): VoiceNote? {
        return database.getNoteById(id)
    }

    override suspend fun insertNote(note: VoiceNote): Long {
        return database.insertNote(note)
    }

    override suspend fun updateNote(note: VoiceNote) {
        database.updateNote(note)
    }

    override suspend fun deleteNote(id: Long) {
        database.deleteNote(id)
    }

    override suspend fun getSegmentsForNote(noteId: Long): List<TranscriptSegment> {
        return database.getSegmentsForNote(noteId)
    }

    override suspend fun insertSegments(segments: List<TranscriptSegment>) {
        database.insertSegments(segments)
    }

    override fun searchNotes(query: String): Flow<List<VoiceNote>> {
        return database.dbChangeTrigger.map {
            database.searchNotes(query)
        }
    }
}
