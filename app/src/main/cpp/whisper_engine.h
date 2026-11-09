#pragma once

#include <string>
#include <vector>
#include <cstdint>
#include <memory>

namespace voice_whisperer {

struct WhisperSegment {
    int64_t t0_ms{0};
    int64_t t1_ms{0};
    std::string text;
    float confidence{1.0f};
};

struct WhisperTranscriptionResult {
    bool success{false};
    std::string error_message;
    std::vector<WhisperSegment> segments;
    int64_t inference_time_ms{0};
    float real_time_factor{0.0f}; // inference_time / audio_duration
};

class WhisperEngine {
public:
    WhisperEngine();
    ~WhisperEngine();

    bool load_model(const std::string& model_path);
    void release();
    bool is_loaded() const;

    WhisperTranscriptionResult transcribe(
        const float* samples,
        int n_samples,
        int n_threads,
        const std::string& language
    );

    static std::string get_system_info();

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace voice_whisperer
