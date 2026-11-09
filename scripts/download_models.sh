#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="${SCRIPT_DIR}/../app/src/main/assets/models"

mkdir -p "${TARGET_DIR}"

echo "=== Downloading Voice Whisperer Models for Offline Use ==="

# 1. Download Whisper Tiny.en (Q5_1 Quantized ~31MB)
WHISPER_MODEL="whisper-tiny-en-q5_1.bin"
if [ ! -f "${TARGET_DIR}/${WHISPER_MODEL}" ]; then
    echo "Downloading ${WHISPER_MODEL} from HuggingFace..."
    curl -L "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q5_1.bin" \
         -o "${TARGET_DIR}/${WHISPER_MODEL}"
    echo "Saved ${WHISPER_MODEL} to ${TARGET_DIR}"
else
    echo "${WHISPER_MODEL} already exists."
fi

# 2. Download Silero VAD (TFLite ~1.2MB)
VAD_MODEL="silero_vad.tflite"
if [ ! -f "${TARGET_DIR}/${VAD_MODEL}" ] || [ $(wc -c < "${TARGET_DIR}/${VAD_MODEL}") -lt 100000 ]; then
    echo "Downloading ${VAD_MODEL}..."
    curl -L "https://huggingface.co/pat229988/silero-vad-16k-tflite/resolve/main/silero_vad_16k.tflite" \
         -o "${TARGET_DIR}/${VAD_MODEL}"
    echo "Saved ${VAD_MODEL} to ${TARGET_DIR}"
else
    echo "${VAD_MODEL} already exists."
fi

echo "=== All models ready for on-device inference ==="
