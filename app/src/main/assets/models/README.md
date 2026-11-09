# Model Assets for Voice Whisperer

This directory contains the on-device AI models used by Voice Whisperer.

## 1. OpenAI Whisper Model (`whisper.cpp` GGML Format)
- **Model:** `whisper-tiny-en-q5_1.bin`
- **Size:** ~31 MB
- **Quantization:** Q5_1 (Integer + Scale Quantization for fast mobile inference)
- **Source:** OpenAI Whisper via `whisper.cpp` model conversion tools
- **Download:**
  ```bash
  # Using whisper.cpp model downloader
  curl -L -O https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q5_1.bin
  mv ggml-tiny.en-q5_1.bin whisper-tiny-en-q5_1.bin
  ```

## 2. Voice Activity Detection (Silero VAD)
- **Model:** `silero_vad.tflite`
- **Size:** ~1.5 MB
- **Role:** Eliminates silence regions, prevents Whisper hallucination during quiet periods, and cuts audio into manageable chunks at natural speech pauses.
- **Fallback:** When `silero_vad.tflite` is omitted, the app automatically switches to an optimized adaptive energy + zero-crossing VAD engine.

## Offline / Airplane Mode Assurance
Voice Whisperer performs 100% of speech-to-text inference on-device through native C++ NDK/JNI (`whisper_jni`). Audio and transcripts never leave the phone.
