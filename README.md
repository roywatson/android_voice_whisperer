# Voice Whisperer

An Android voice-notes application that records audio, detects speech with a neural Voice Activity Detector, and transcribes it entirely on the device with OpenAI's Whisper model running natively through whisper.cpp, the Android NDK, and JNI.

The goal is to show what a complete on-device speech-to-text pipeline looks like on Android, without hiding the hard parts behind a cloud API or an SDK. Audio capture at Whisper's native sample rate, foreground-service recording, Silero VAD on LiteRT, chunking at natural pauses, a C++17 inference engine compiled for ARMv8.2 SIMD, a JNI bridge designed to cross the language boundary as rarely as possible, decoder tuning for mobile, and timestamp-accurate playback are all represented directly in the code.

Everything runs in Airplane Mode. The app does not request the `INTERNET` permission, and audio and transcripts never leave the phone.

The app has four screens:

- **Notes List** - recorded notes with duration, status, word count, and measured real-time factor, plus search across titles and transcripts.
- **Recording** - timer, live waveform, Speech/Silence indicator, and pause, resume, stop, and cancel controls.
- **Note Detail** - timestamped transcript under an audio player. Tap any segment to seek playback to it; the active segment highlights as audio plays. Adjustable playback speed, and export to `.txt`, `.md`, or `.srt`.
- **Benchmark** - device information, the CPU features the native build was compiled with, the active model, and aggregate performance across all notes.

There is no dependency injection framework, no Room, and no networking library. The project keeps those things out of the way so the audio, VAD, and inference code is easy to follow.

A 70-second screen recording of the app running on a physical device in Airplane Mode is in [`media/demo_recording.mp4`](media/demo_recording.mp4).

---

## Running the project

Clone the repository and open it in Android Studio. You will need the Android NDK and CMake 3.22.1 installed through the SDK Manager.

The quantized Whisper model and the Silero VAD model are included in `app/src/main/assets/models/`. If they are missing, they can be downloaded with:

```bash
./scripts/download_models.sh
```

To build from the command line:

```bash
./gradlew :app:assembleDebug
```

To run the JVM unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

The first build compiles whisper.cpp and ggml from source for each ABI and takes a few minutes. Later builds are incremental.

A physical ARM64 device is strongly recommended. The `arm64-v8a` build targets ARMv8.2 with FP16 and dot-product instructions, which covers essentially every 64-bit Android phone from roughly 2019 onward. An `x86_64` build is included so the app runs on an emulator, but emulator performance says nothing useful about real devices.

The app targets SDK 37 and supports devices back to Android 7.0 (`minSdk 24`).

---

## Permissions

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
```

`RECORD_AUDIO` and, on Android 13 and newer, `POST_NOTIFICATIONS` are requested at runtime. Recording runs under a foreground service declared with `foregroundServiceType="microphone"`, which Android 14 and newer require for recording that continues while the app is in the background. A microphone foreground service must be started while the app is visible, so it is started when the user taps Record.

There is no `INTERNET` permission.

---

## What this demonstrates

### Recording

`WavAudioRecorder` captures 16 kHz, 16-bit mono PCM with `AudioRecord`, which is exactly what Whisper expects, so there is no resampling step anywhere in the pipeline. Audio streams to a WAV file with a placeholder header; the RIFF size fields are patched with `RandomAccessFile` when recording stops. The recording loop reuses its buffers and publishes an RMS amplitude for the Compose waveform.

The app records the complete note, then transcribes it after Stop. Whisper is not a streaming model, and for voice notes reviewed afterward, record-then-transcribe is simpler, cheaper, and more accurate than repeatedly re-transcribing a sliding window.

### Voice Activity Detection

Whisper can hallucinate text during long silence, and every second of silence sent to the model costs battery. `SileroVadDetector` runs the Silero VAD model through LiteRT (TensorFlow Lite) on 512-sample (32 ms) windows, carrying the model's recurrent state between frames. If the model is unavailable, `EnergyVadDetector` provides an adaptive-noise-floor energy and zero-crossing-rate fallback. Both implement the same `VoiceActivityDetector` interface.

The live Speech/Silence indicator on the recording screen uses a cheaper amplitude heuristic. Its hangover window is tied to the chunker's silence threshold, so the UI and the pipeline agree on what counts as a pause.

### Chunking

`AudioChunker` splits speech into chunks at natural pauses: 850 ms of silence closes a chunk, 300 ms of padding is added before speech so first syllables are not clipped, and chunks are capped at 28 seconds to stay inside Whisper's 30-second window. If VAD finds no speech in a non-empty recording, the whole clip is transcribed anyway. Chunk-relative timestamps from Whisper are re-based to the start of the recording.

### Native build

whisper.cpp (1.9.5) is vendored in `app/src/main/cpp/whisper_src` and built with CMake as static libraries linked into a single `libwhisper_jni.so` per ABI.

Optimization flags are applied with `add_compile_options` *before* `add_subdirectory`, so they reach ggml's kernels and not just the JNI bridge:

```cmake
add_compile_options(-O3 -fPIC)
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_compile_options(-march=armv8.2-a+fp16+dotprod)
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    add_compile_options(-mfpu=neon-vfpv4 -mfloat-abi=softfp)
endif()
```

The Benchmark screen displays `whisper_print_system_info()`, which shows which CPU features the native code was actually compiled with.

Targeting ARMv8.2 means the `arm64-v8a` build will not run on older ARMv8.0 CPUs. For a production app with a broad device audience, consider ggml's runtime CPU-variant selection, a second baseline build, or Play Console device targeting.

### JNI bridge

The native side is split into `WhisperEngine` (plain C++, owns the `whisper_context`, no JNI types) and `whisper_jni.cpp` (thin translation layer). The Kotlin side holds the native engine as an opaque `Long` handle owned by the Kotlin `WhisperEngine`.

Each chunk crosses the boundary once in each direction: a `FloatArray` in, released with `JNI_ABORT` because it is never modified, and a single `WhisperNativeResult` containing an array of phrase-level `WhisperNativeSegment`s out. Whisper's 10 ms timestamp units are converted to milliseconds in C++.

Because C++ looks up the result classes by name, `app/src/main/keepRules/rules.keep` keeps them from being renamed or removed by R8. Without those rules, the minified release build fails at runtime while the debug build works.

### Decoder tuning

`whisper_engine.cpp` configures whisper.cpp for predictable mobile latency:

- greedy sampling instead of beam search
- `temperature = 0` and `temperature_inc = 0`, disabling the fallback that can re-decode a difficult segment up to five more times
- `no_context = true`, so a mistake in one chunk is not carried into the next
- inference capped at four threads, so ggml's per-operation barriers are not held up by the slowest cores (the thread count is limited; threads are not pinned to specific cores)

Each of these trades a little accuracy on difficult audio for speed and consistency. An app transcribing noisy audio might reasonably choose differently.

### Model delivery

`ggml-tiny.en-q5_1` (about 32 MB, English-only, 5-bit quantized) and `silero_vad.tflite` are bundled as assets and copied to the app's files directory on first launch by `ModelManager`. That is simple, but it stores the model twice. Play Asset Delivery for on-demand model downloads is planned for the Play Store milestone.

### Persistence

Notes and transcript segments are stored in two SQLite tables (`SQLiteOpenHelper`, write-ahead logging, foreign keys with cascade delete). Change notification uses a `MutableSharedFlow` driven by an `AtomicLong` counter. An earlier version used a `StateFlow` holding a timestamp; two writes in the same millisecond produced equal values, and `StateFlow` conflated away the final "completed" update.

### Transcript playback and export

`WavAudioPlayer` wraps `MediaPlayer` with millisecond seeking, playback speed control, and a 100 ms position-tracking coroutine that drives segment highlighting. `TranscriptExporter` produces plain text, Markdown, and SRT subtitles, shared through the Android share sheet so no storage permission is needed.

---

## Benchmark baseline

Measured on a Samsung Galaxy S25 Ultra (SM-S938U, Snapdragon 8 Elite) with `tiny.en` Q5_1, four inference threads, in Airplane Mode:

| Test | Audio | Native inference | Native RTF | End-to-end RTF | Words correct |
|---|---|---|---|---|---|
| Counting 1 to 15 | 15.00 s | 430 ms | 0.029 (~35x real time) | 0.04 | 15 / 15 |
| Conversational speech | 22.28 s | 668 ms | 0.030 (~33x real time) | 0.04 | 34 / 34 |

- Peak memory during inference: about 135 MB
- Network data transferred: 0 bytes

Real-time factor (RTF) is processing time divided by audio duration; lower is faster. Native RTF covers the `whisper_full` call. End-to-end RTF covers the whole Kotlin transcription call, including WAV decoding, VAD, chunking, and JNI.

These are two clean recordings on a current flagship phone — a smoke test, not an accuracy benchmark. Word Error Rate on LibriSpeech across multiple models and devices is the subject of Milestone B2.

---

## Incorporating this into your project

The screens are sample workflows. The reusable pieces are:

- **`app/src/main/cpp/`** - `CMakeLists.txt`, `whisper_engine.h/.cpp`, and `whisper_jni.cpp`. Together with the vendored `whisper_src`, this is a self-contained native transcription engine.
- **`whisper/WhisperNativeBridge.kt`**, **`WhisperNativeResult.kt`**, **`WhisperNativeSegment.kt`** - the Kotlin side of the JNI contract. If you change the package name, update the class names in `whisper_jni.cpp` and the keep rules to match.
- **`whisper/WhisperEngine.kt`** - the Kotlin pipeline: read WAV, run VAD, chunk, transcribe, re-base timestamps.
- **`vad/`** - `SileroVadDetector`, `EnergyVadDetector`, and `AudioChunker`.
- **`audio/`** - `WavAudioRecorder`, `WavUtils`, `WavAudioPlayer`, and `AudioRecordingService`.
- **`domain/repository/TranscriptionEngine.kt`** and **`VoiceActivityDetector.kt`** - small interfaces that keep the rest of the app independent of the specific engine.

This is not intended to be a general-purpose speech library. If you bring the code into a production app, review the ARMv8.2 build target against your device audience, consider moving the `AudioRecord` into the foreground service so recording survives activity destruction, plan model delivery and storage, and measure performance on the lowest-end device you intend to support.

---

## Running the tests

The JVM unit tests cover the deterministic logic:

- splitting audio into chunks at silence boundaries, and the fallback when VAD returns nothing
- plain text, Markdown, and SRT export formatting
- note duration formatting, segment time ranges, and word counts

```bash
./gradlew :app:testDebugUnitTest
```

Audio capture and native inference need physical devices to test meaningfully. If the native library cannot load, `WhisperEngine` runs in a diagnostic mode that produces placeholder segments with correct timing, so the UI, persistence, and export paths can still be exercised.

---

## Roadmap

- [x] **B1. Record and transcribe on the phone** - recording, VAD, chunking, native whisper.cpp inference, synchronized playback, export
- [ ] **B2. Model comparisons** - `tiny` vs `base` vs `small` at several quantizations; WER on LibriSpeech, RTF, and memory across multiple devices; in-app model switcher
- [ ] **B3. Speaker diarization** - on-device speaker embeddings and clustering
- [ ] **B4. Local summaries and action items** - interchangeable backends: Gemini Nano (ML Kit GenAI), Gemma on LiteRT-LM, and llama.cpp
- [ ] **B5. Google Play release** - Play Asset Delivery for on-demand model downloads
- [ ] **B6. Cross-note semantic search** - local embeddings and vector search
- [ ] **B7. Compose Multiplatform companion** - iOS and Desktop

Interfaces for B3, B4, and B6 (`SpeakerDiarizer`, `SummarizationEngine`, `SemanticSearchEngine`) are already in `domain/repository`, and the data model and exporters already carry speaker tags, summaries, and action items.

---

## Project structure

```
app/src/main/
  cpp/
    CMakeLists.txt               # NDK build, global SIMD flags, links whisper + ggml
    whisper_engine.h / .cpp      # C++ inference engine (no JNI types)
    whisper_jni.cpp              # JNI translation layer
    whisper_src/                 # Vendored whisper.cpp 1.9.5 and ggml

  assets/models/
    whisper-tiny-en-q5_1.bin     # Quantized Whisper model (~32 MB)
    silero_vad.tflite            # Silero VAD model (~1.2 MB)

  keepRules/rules.keep           # R8 rules protecting JNI-referenced classes

  java/com/roywatson/android_voice_whisperer/
    VoiceWhispererApplication.kt # Manual wiring, background model setup
    MainActivity.kt

    audio/
      WavAudioRecorder.kt        # 16 kHz PCM capture to WAV
      WavUtils.kt                # RIFF header write/patch, fast PCM read
      WavAudioPlayer.kt          # Seekable playback with speed control
      AudioRecordingService.kt   # Microphone foreground service

    vad/
      SileroVadDetector.kt       # Silero VAD on LiteRT
      EnergyVadDetector.kt       # Energy + zero-crossing fallback
      AudioChunker.kt            # Pause-based chunking

    whisper/
      WhisperEngine.kt           # Kotlin transcription pipeline
      WhisperNativeBridge.kt     # external fun declarations
      WhisperNativeResult.kt     # JNI result types
      WhisperNativeSegment.kt
      WhisperModelConfig.kt      # Model catalog

    data/
      AppDatabase.kt             # SQLiteOpenHelper, change notification
      VoiceNotesRepositoryImpl.kt
      ModelManager.kt            # Asset extraction

    domain/
      model/                     # VoiceNote, TranscriptSegment, VadSegment, ...
      repository/                # Engine, VAD, repository, and future-milestone interfaces

    export/
      TranscriptExporter.kt      # .txt, .md, .srt

    ui/
      VoiceWhispererApp.kt       # Navigation, permissions, BackHandler
      screens/                   # NotesList, Recording, NoteDetail, Benchmark
      components/                # AudioPlayerBar, TranscriptSegmentItem, WaveformVisualizer
      viewmodel/                 # NotesViewModel, RecordingViewModel
      theme/
```

---

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.2.10, C++17 |
| UI framework | Jetpack Compose with Material 3 |
| Speech recognition | whisper.cpp 1.9.5 (ggml), `tiny.en` Q5_1 |
| Voice activity detection | Silero VAD on LiteRT (TensorFlow Lite 2.16.1) |
| Native build | Android NDK, CMake 3.22.1 |
| ABIs | arm64-v8a, armeabi-v7a, x86_64 |
| Android Gradle Plugin | 9.5.1 |
| Android min SDK | 24 |
| Android target SDK | 37 |
| Tests | JUnit 4 |

---

## Third-party components

- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) and ggml - MIT License
- [OpenAI Whisper](https://github.com/openai/whisper) model weights - MIT License
- [Silero VAD](https://github.com/snakers4/silero-vad) - MIT License
- [LiteRT / TensorFlow Lite](https://ai.google.dev/edge/litert) - Apache License 2.0

The application code outside `app/src/main/cpp/whisper_src` is original and is licensed under the MIT License. See [LICENSE](LICENSE).

---

## About

Before Roy Watson wrote a line of Android code, he was programming the propellant management system for NASA's Delta launch vehicles, building radiation detection software for Los Alamos and Sandia National Laboratories, and developing embedded vision systems for IBM's autonomous vehicle research. After 30+ years as a systems developer, he approaches Android differently than most.

Over the past 17 years he has specialized in Android, staying current with every major evolution of the platform: Kotlin, Jetpack Compose, and for the last two years Kotlin Multiplatform and Compose Multiplatform for iOS, Desktop, and Web. Recent clients include the New York Public Library, Auddia, Bechtel, and Goodyear. When performance work requires dropping into C/C++, SIMD intrinsics, or custom memory management, he does not hand it off.

His personal published apps have accumulated 70,000+ Android downloads (4.5-star) and 207,000+ iOS downloads (4.6-star). He studied Physics and Mathematics at Purdue University and is a member of Mensa International.

This project is part of a series of public examples demonstrating current Android and Compose Multiplatform architecture and patterns. The series begins with [Android Custom Theme](https://github.com/roywatson/android_custom_theme) and [Compose Multiplatform Custom Theme](https://github.com/roywatson/cmp_custom_theme), which build a complete custom theming system from first principles. Subsequent projects introduce [Dependency Injection with Koin](https://github.com/roywatson/cmp_di_koin), [DataStore Preferences](https://github.com/roywatson/cmp_di_koin_datastore_prefs), [HTTP networking with Ktor](https://github.com/roywatson/cmp_ktor), [Custom JVM Titlebars](https://github.com/roywatson/cmp_jvm_custom_titlebar), [Compose Multiplatform WebView](https://github.com/roywatson/cmp_webview), and [Android BLE Tools](https://github.com/roywatson/android_ble_tools). This project stays on Android and moves into on-device machine learning, where the most important work happens in native code close to the hardware.

Questions, corrections, or suggestions are welcome. Reach out through [roywatson.app](https://www.roywatson.app) or directly at rwatson@roywatson.com.

Available for contract and full-time engagements - [roywatson.app](https://www.roywatson.app)

[roywatson.app](https://www.roywatson.app) | [linkedin.com/in/roywatson3](https://linkedin.com/in/roywatson3) | [github.com/roywatson](https://github.com/roywatson)
