#include "whisper_engine.h"
#include "whisper.h"
#include <chrono>
#include <thread>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "VoiceWhispererEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace voice_whisperer {

struct WhisperEngine::Impl {
    struct whisper_context* ctx{nullptr};
    std::string model_path;

    ~Impl() {
        if (ctx) {
            whisper_free(ctx);
            ctx = nullptr;
        }
    }

    bool init(const std::string& path) {
        if (ctx) {
            whisper_free(ctx);
            ctx = nullptr;
        }

        struct whisper_context_params cparams = whisper_context_default_params();
        cparams.use_gpu = false;

        LOGI("Initializing whisper context from file: %s", path.c_str());
        ctx = whisper_init_from_file_with_params(path.c_str(), cparams);

        if (!ctx) {
            LOGE("Failed to initialize whisper context from '%s'", path.c_str());
            return false;
        }

        model_path = path;
        LOGI("Whisper context initialized successfully");
        return true;
    }
};

WhisperEngine::WhisperEngine() : impl_(std::make_unique<Impl>()) {}

WhisperEngine::~WhisperEngine() = default;

bool WhisperEngine::load_model(const std::string& model_path) {
    return impl_->init(model_path);
}

void WhisperEngine::release() {
    if (impl_->ctx) {
        whisper_free(impl_->ctx);
        impl_->ctx = nullptr;
    }
    impl_->model_path.clear();
}

bool WhisperEngine::is_loaded() const {
    return impl_->ctx != nullptr;
}

WhisperTranscriptionResult WhisperEngine::transcribe(
    const float* samples,
    int n_samples,
    int n_threads,
    const std::string& language
) {
    WhisperTranscriptionResult result;
    if (!impl_->ctx) {
        result.success = false;
        result.error_message = "Whisper context not initialized";
        LOGE("%s", result.error_message.c_str());
        return result;
    }

    if (!samples || n_samples <= 0) {
        result.success = false;
        result.error_message = "Invalid or empty audio samples";
        LOGE("%s", result.error_message.c_str());
        return result;
    }

    const auto start_time = std::chrono::high_resolution_clock::now();
    const float audio_duration_sec = static_cast<float>(n_samples) / 16000.0f;

    // On Android big.LITTLE architectures, using 4 threads targets the performance cores
    // and avoids scheduling bottlenecks on slower efficiency cores.
    const int actual_threads = (n_threads > 0)
        ? std::min(n_threads, 4)
        : std::min(4, std::max(1, static_cast<int>(std::thread::hardware_concurrency())));

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.strategy = WHISPER_SAMPLING_GREEDY;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    std::string lang = language.empty() ? "en" : language;
    params.language = lang.c_str();
    params.n_threads = actual_threads;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    // Mobile performance tuning:
    // Disabling temperature retry loops avoids repeated decoder passes
    params.temperature = 0.0f;
    params.temperature_inc = 0.0f;
    params.suppress_blank = true;
    params.suppress_nst = true;

    whisper_reset_timings(impl_->ctx);

    LOGI("Running whisper_full on %d samples (%.2fs) using %d threads with lang '%s'",
         n_samples, audio_duration_sec, actual_threads, lang.c_str());

    int res = whisper_full(impl_->ctx, params, samples, n_samples);
    if (res != 0) {
        LOGE("whisper_full returned error code %d", res);
        result.success = false;
        result.error_message = "whisper_full failed with code " + std::to_string(res);
        return result;
    }

    const int n_segments = whisper_full_n_segments(impl_->ctx);
    LOGI("whisper_full finished successfully. Extracted %d segment(s)", n_segments);

    for (int i = 0; i < n_segments; ++i) {
        const char* text_ptr = whisper_full_get_segment_text(impl_->ctx, i);
        // Whisper returns centiseconds (10 ms units)
        const int64_t t0 = whisper_full_get_segment_t0(impl_->ctx, i) * 10;
        const int64_t t1 = whisper_full_get_segment_t1(impl_->ctx, i) * 10;

        std::string segment_text = text_ptr ? text_ptr : "";
        LOGI("Segment %d [%lld - %lld ms]: %s", i, (long long)t0, (long long)t1, segment_text.c_str());

        WhisperSegment seg;
        seg.t0_ms = t0;
        seg.t1_ms = t1;
        seg.text = segment_text;
        seg.confidence = 0.95f;
        result.segments.push_back(seg);
    }

    result.success = true;
    const auto end_time = std::chrono::high_resolution_clock::now();
    result.inference_time_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        end_time - start_time
    ).count();

    if (audio_duration_sec > 0.001f) {
        result.real_time_factor = (static_cast<float>(result.inference_time_ms) / 1000.0f) / audio_duration_sec;
    } else {
        result.real_time_factor = 0.0f;
    }

    LOGI("Total inference time: %lld ms (RTF: %.3f)",
         (long long)result.inference_time_ms, result.real_time_factor);

    return result;
}

std::string WhisperEngine::get_system_info() {
    const char* sysinfo = whisper_print_system_info();
    return sysinfo ? sysinfo : "System info unavailable";
}

} // namespace voice_whisperer
