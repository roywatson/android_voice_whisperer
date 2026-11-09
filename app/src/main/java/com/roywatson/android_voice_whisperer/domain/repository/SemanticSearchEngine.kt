package com.roywatson.android_voice_whisperer.domain.repository

import com.roywatson.android_voice_whisperer.domain.model.VoiceNote

/**
 * Architectural Seam for Step B6: Semantic Search & Cross-Note QA.
 * Utilizes local text embeddings and vector indexing alongside FTS keyword search.
 */
data class SemanticSearchResult(
    val note: VoiceNote,
    val matchedExcerpt: String,
    val similarityScore: Float
)

interface SemanticSearchEngine {
    suspend fun indexNote(note: VoiceNote)
    suspend fun removeNote(noteId: Long)
    suspend fun search(query: String, maxResults: Int = 10): List<SemanticSearchResult>
    suspend fun answerQuestionAcrossNotes(question: String): Result<String>
}
