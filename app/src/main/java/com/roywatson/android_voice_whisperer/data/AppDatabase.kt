package com.roywatson.android_voice_whisperer.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.roywatson.android_voice_whisperer.domain.model.TranscriptSegment
import com.roywatson.android_voice_whisperer.domain.model.TranscriptionStatus
import com.roywatson.android_voice_whisperer.domain.model.VoiceNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AppDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "voice_whisperer.db"
        const val DATABASE_VERSION = 1

        const val TABLE_NOTES = "voice_notes"
        const val TABLE_SEGMENTS = "transcript_segments"

        // Notes Columns
        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_CREATED_AT = "created_at"
        const val COL_DURATION_MS = "duration_ms"
        const val COL_AUDIO_PATH = "audio_file_path"
        const val COL_STATUS = "status"
        const val COL_FULL_TRANSCRIPT = "full_transcript"
        const val COL_RTF = "real_time_factor"
        const val COL_INFERENCE_MS = "inference_time_ms"
        const val COL_MODEL_USED = "model_used"
        const val COL_SUMMARY = "summary"
        const val COL_ACTION_ITEMS = "action_items"
        const val COL_ERROR = "error_message"

        // Segments Columns
        const val COL_SEG_ID = "id"
        const val COL_SEG_NOTE_ID = "note_id"
        const val COL_SEG_START_MS = "start_ms"
        const val COL_SEG_END_MS = "end_ms"
        const val COL_SEG_TEXT = "text"
        const val COL_SEG_SPEAKER = "speaker_tag"
        const val COL_SEG_CONFIDENCE = "confidence"
    }

    private val changeCounter = java.util.concurrent.atomic.AtomicLong(1L)
    private val _dbChangeTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Long>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    ).apply { tryEmit(0L) }
    val dbChangeTrigger: Flow<Long> = _dbChangeTrigger

    private fun notifyChanged() {
        _dbChangeTrigger.tryEmit(changeCounter.incrementAndGet())
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NOTES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TITLE TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_DURATION_MS INTEGER NOT NULL,
                $COL_AUDIO_PATH TEXT NOT NULL,
                $COL_STATUS TEXT NOT NULL,
                $COL_FULL_TRANSCRIPT TEXT NOT NULL,
                $COL_RTF REAL NOT NULL,
                $COL_INFERENCE_MS INTEGER NOT NULL,
                $COL_MODEL_USED TEXT NOT NULL,
                $COL_SUMMARY TEXT,
                $COL_ACTION_ITEMS TEXT,
                $COL_ERROR TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SEGMENTS (
                $COL_SEG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SEG_NOTE_ID INTEGER NOT NULL,
                $COL_SEG_START_MS INTEGER NOT NULL,
                $COL_SEG_END_MS INTEGER NOT NULL,
                $COL_SEG_TEXT TEXT NOT NULL,
                $COL_SEG_SPEAKER TEXT,
                $COL_SEG_CONFIDENCE REAL NOT NULL,
                FOREIGN KEY ($COL_SEG_NOTE_ID) REFERENCES $TABLE_NOTES($COL_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_segments_note_id ON $TABLE_SEGMENTS($COL_SEG_NOTE_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SEGMENTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NOTES")
        onCreate(db)
    }

    suspend fun insertNote(note: VoiceNote): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_TITLE, note.title)
            put(COL_CREATED_AT, note.createdAt)
            put(COL_DURATION_MS, note.durationMs)
            put(COL_AUDIO_PATH, note.audioFilePath)
            put(COL_STATUS, note.status.name)
            put(COL_FULL_TRANSCRIPT, note.fullTranscript)
            put(COL_RTF, note.realTimeFactor)
            put(COL_INFERENCE_MS, note.inferenceTimeMs)
            put(COL_MODEL_USED, note.modelUsed)
            put(COL_SUMMARY, note.summary)
            put(COL_ACTION_ITEMS, note.actionItems.joinToString("\n"))
            put(COL_ERROR, note.errorMessage)
        }
        val id = writableDatabase.insert(TABLE_NOTES, null, values)
        notifyChanged()
        id
    }

    suspend fun updateNote(note: VoiceNote) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_TITLE, note.title)
            put(COL_DURATION_MS, note.durationMs)
            put(COL_STATUS, note.status.name)
            put(COL_FULL_TRANSCRIPT, note.fullTranscript)
            put(COL_RTF, note.realTimeFactor)
            put(COL_INFERENCE_MS, note.inferenceTimeMs)
            put(COL_MODEL_USED, note.modelUsed)
            put(COL_SUMMARY, note.summary)
            put(COL_ACTION_ITEMS, note.actionItems.joinToString("\n"))
            put(COL_ERROR, note.errorMessage)
        }
        writableDatabase.update(TABLE_NOTES, values, "$COL_ID = ?", arrayOf(note.id.toString()))
        notifyChanged()
    }

    suspend fun deleteNote(id: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_SEGMENTS, "$COL_SEG_NOTE_ID = ?", arrayOf(id.toString()))
        writableDatabase.delete(TABLE_NOTES, "$COL_ID = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    suspend fun getAllNotes(): List<VoiceNote> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VoiceNote>()
        val cursor = readableDatabase.query(
            TABLE_NOTES,
            null,
            null,
            null,
            null,
            null,
            "$COL_CREATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val note = cursorToVoiceNote(it)
                val segments = getSegmentsForNote(note.id)
                list.add(note.copy(segments = segments))
            }
        }
        list
    }

    suspend fun getNoteById(id: Long): VoiceNote? = withContext(Dispatchers.IO) {
        val cursor = readableDatabase.query(
            TABLE_NOTES,
            null,
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val note = cursorToVoiceNote(it)
                val segments = getSegmentsForNote(id)
                return@withContext note.copy(segments = segments)
            }
        }
        null
    }

    suspend fun insertSegments(segments: List<TranscriptSegment>) = withContext(Dispatchers.IO) {
        if (segments.isEmpty()) return@withContext
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (seg in segments) {
                val values = ContentValues().apply {
                    put(COL_SEG_NOTE_ID, seg.noteId)
                    put(COL_SEG_START_MS, seg.startMs)
                    put(COL_SEG_END_MS, seg.endMs)
                    put(COL_SEG_TEXT, seg.text)
                    put(COL_SEG_SPEAKER, seg.speakerTag)
                    put(COL_SEG_CONFIDENCE, seg.confidence)
                }
                db.insert(TABLE_SEGMENTS, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    suspend fun getSegmentsForNote(noteId: Long): List<TranscriptSegment> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TranscriptSegment>()
        val cursor = readableDatabase.query(
            TABLE_SEGMENTS,
            null,
            "$COL_SEG_NOTE_ID = ?",
            arrayOf(noteId.toString()),
            null,
            null,
            "$COL_SEG_START_MS ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    TranscriptSegment(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_SEG_ID)),
                        noteId = it.getLong(it.getColumnIndexOrThrow(COL_SEG_NOTE_ID)),
                        startMs = it.getLong(it.getColumnIndexOrThrow(COL_SEG_START_MS)),
                        endMs = it.getLong(it.getColumnIndexOrThrow(COL_SEG_END_MS)),
                        text = it.getString(it.getColumnIndexOrThrow(COL_SEG_TEXT)),
                        speakerTag = it.getString(it.getColumnIndexOrThrow(COL_SEG_SPEAKER)),
                        confidence = it.getFloat(it.getColumnIndexOrThrow(COL_SEG_CONFIDENCE))
                    )
                )
            }
        }
        list
    }

    suspend fun searchNotes(query: String): List<VoiceNote> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VoiceNote>()
        val cursor = readableDatabase.query(
            TABLE_NOTES,
            null,
            "$COL_TITLE LIKE ? OR $COL_FULL_TRANSCRIPT LIKE ?",
            arrayOf("%$query%", "%$query%"),
            null,
            null,
            "$COL_CREATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val note = cursorToVoiceNote(it)
                val segments = getSegmentsForNote(note.id)
                list.add(note.copy(segments = segments))
            }
        }
        list
    }

    private fun cursorToVoiceNote(c: Cursor): VoiceNote {
        val actionItemsRaw = c.getString(c.getColumnIndexOrThrow(COL_ACTION_ITEMS)) ?: ""
        val actionItems = if (actionItemsRaw.isBlank()) emptyList() else actionItemsRaw.split("\n")

        return VoiceNote(
            id = c.getLong(c.getColumnIndexOrThrow(COL_ID)),
            title = c.getString(c.getColumnIndexOrThrow(COL_TITLE)),
            createdAt = c.getLong(c.getColumnIndexOrThrow(COL_CREATED_AT)),
            durationMs = c.getLong(c.getColumnIndexOrThrow(COL_DURATION_MS)),
            audioFilePath = c.getString(c.getColumnIndexOrThrow(COL_AUDIO_PATH)),
            status = try {
                TranscriptionStatus.valueOf(c.getString(c.getColumnIndexOrThrow(COL_STATUS)))
            } catch (e: Exception) {
                TranscriptionStatus.IDLE
            },
            fullTranscript = c.getString(c.getColumnIndexOrThrow(COL_FULL_TRANSCRIPT)),
            realTimeFactor = c.getFloat(c.getColumnIndexOrThrow(COL_RTF)),
            inferenceTimeMs = c.getLong(c.getColumnIndexOrThrow(COL_INFERENCE_MS)),
            modelUsed = c.getString(c.getColumnIndexOrThrow(COL_MODEL_USED)),
            summary = c.getString(c.getColumnIndexOrThrow(COL_SUMMARY)),
            actionItems = actionItems,
            errorMessage = c.getString(c.getColumnIndexOrThrow(COL_ERROR))
        )
    }
}
