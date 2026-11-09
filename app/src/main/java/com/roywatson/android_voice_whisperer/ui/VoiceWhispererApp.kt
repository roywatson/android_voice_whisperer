package com.roywatson.android_voice_whisperer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roywatson.android_voice_whisperer.VoiceWhispererApplication
import com.roywatson.android_voice_whisperer.domain.model.AudioRecordingState
import com.roywatson.android_voice_whisperer.domain.model.VoiceNote
import com.roywatson.android_voice_whisperer.ui.screens.BenchmarkScreen
import com.roywatson.android_voice_whisperer.ui.screens.NoteDetailScreen
import com.roywatson.android_voice_whisperer.ui.screens.NotesListScreen
import com.roywatson.android_voice_whisperer.ui.screens.RecordingScreen
import com.roywatson.android_voice_whisperer.ui.viewmodel.NotesViewModel
import com.roywatson.android_voice_whisperer.ui.viewmodel.RecordingViewModel

enum class Screen {
    NOTES_LIST,
    NOTE_DETAIL,
    BENCHMARKS
}

@Composable
fun VoiceWhispererApp(
    notesViewModel: NotesViewModel = viewModel(),
    recordingViewModel: RecordingViewModel = viewModel()
) {
    val context = LocalContext.current
    val app = context.applicationContext as VoiceWhispererApplication

    var currentScreen by remember { mutableStateOf(Screen.NOTES_LIST) }
    var selectedNote by remember { mutableStateOf<VoiceNote?>(null) }
    var showRecordingOverlay by remember { mutableStateOf(false) }

    val notes by notesViewModel.notes.collectAsState()
    val searchQuery by notesViewModel.searchQuery.collectAsState()
    val recordingState by recordingViewModel.recordingState.collectAsState()

    // Smoothly auto-dismiss recording overlay as soon as on-device transcription completes
    LaunchedEffect(recordingState) {
        if (showRecordingOverlay && recordingState is AudioRecordingState.Idle) {
            showRecordingOverlay = false
        }
    }

    // System Back Gesture Handling
    BackHandler(enabled = showRecordingOverlay) {
        if (recordingState is AudioRecordingState.Recording || recordingState is AudioRecordingState.Paused) {
            recordingViewModel.cancelRecording()
        }
        showRecordingOverlay = false
    }

    BackHandler(enabled = !showRecordingOverlay && currentScreen != Screen.NOTES_LIST) {
        currentScreen = Screen.NOTES_LIST
        selectedNote = null
    }

    // Permission launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (recordGranted) {
            showRecordingOverlay = true
            recordingViewModel.startRecording()
        }
    }

    fun checkAndStartRecording() {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val neededPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (hasMicPermission) {
            showRecordingOverlay = true
            recordingViewModel.startRecording()
        } else {
            permissionsLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            Screen.NOTES_LIST -> {
                NotesListScreen(
                    notes = notes,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { notesViewModel.updateSearchQuery(it) },
                    onNoteClick = { note ->
                        selectedNote = note
                        currentScreen = Screen.NOTE_DETAIL
                    },
                    onDeleteNote = { note ->
                        notesViewModel.deleteNote(note)
                        if (selectedNote?.id == note.id) {
                            selectedNote = null
                        }
                    },
                    onStartRecordClick = { checkAndStartRecording() },
                    onOpenBenchmarksClick = { currentScreen = Screen.BENCHMARKS }
                )
            }
            Screen.NOTE_DETAIL -> {
                selectedNote?.let { note ->
                    // Keep detail view updated if note changes in DB
                    val currentNote = notes.find { it.id == note.id } ?: note
                    NoteDetailScreen(
                        note = currentNote,
                        audioPlayer = app.audioPlayer,
                        onBackClick = {
                            currentScreen = Screen.NOTES_LIST
                            selectedNote = null
                        }
                    )
                } ?: run {
                    currentScreen = Screen.NOTES_LIST
                }
            }
            Screen.BENCHMARKS -> {
                BenchmarkScreen(
                    notes = notes,
                    onBackClick = { currentScreen = Screen.NOTES_LIST }
                )
            }
        }

        // Recording Screen Overlay
        AnimatedVisibility(
            visible = showRecordingOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            RecordingScreen(
                state = recordingState,
                onPause = { recordingViewModel.pauseRecording() },
                onResume = { recordingViewModel.resumeRecording() },
                onStop = {
                    recordingViewModel.stopRecording()
                },
                onCancel = {
                    recordingViewModel.cancelRecording()
                    showRecordingOverlay = false
                }
            )
        }
    }
}
