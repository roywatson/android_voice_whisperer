#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper_engine.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace voice_whisperer;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_roywatson_android_1voice_1whisperer_whisper_WhisperNativeBridge_initContext(
    JNIEnv *env,
    jobject /* thiz */,
    jstring model_path
) {
    if (!model_path) {
        LOGE("Model path is null");
        return 0;
    }

    const char *path_utf = env->GetStringUTFChars(model_path, nullptr);
    if (!path_utf) {
        LOGE("Failed to extract model path string");
        return 0;
    }

    auto *engine = new WhisperEngine();
    bool loaded = engine->load_model(path_utf);
    env->ReleaseStringUTFChars(model_path, path_utf);

    if (!loaded) {
        LOGE("Failed to load Whisper model");
        delete engine;
        return 0;
    }

    LOGI("Whisper context initialized at %p", engine);
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_roywatson_android_1voice_1whisperer_whisper_WhisperNativeBridge_freeContext(
    JNIEnv * /* env */,
    jobject /* thiz */,
    jlong context_ptr
) {
    if (context_ptr != 0) {
        auto *engine = reinterpret_cast<WhisperEngine *>(context_ptr);
        delete engine;
        LOGI("Whisper context freed at %p", engine);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_roywatson_android_1voice_1whisperer_whisper_WhisperNativeBridge_isModelLoaded(
    JNIEnv * /* env */,
    jobject /* thiz */,
    jlong context_ptr
) {
    if (context_ptr == 0) return JNI_FALSE;
    auto *engine = reinterpret_cast<WhisperEngine *>(context_ptr);
    return engine->is_loaded() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobject JNICALL
Java_com_roywatson_android_1voice_1whisperer_whisper_WhisperNativeBridge_transcribeSamples(
    JNIEnv *env,
    jobject /* thiz */,
    jlong context_ptr,
    jfloatArray samples,
    jint n_threads,
    jstring language
) {
    jclass result_class = env->FindClass("com/roywatson/android_voice_whisperer/whisper/WhisperNativeResult");
    if (!result_class) {
        LOGE("Could not find WhisperNativeResult class");
        return nullptr;
    }

    jmethodID result_ctor = env->GetMethodID(
        result_class,
        "<init>",
        "(ZLjava/lang/String;[Lcom/roywatson/android_voice_whisperer/whisper/WhisperNativeSegment;JF)V"
    );

    if (context_ptr == 0 || !samples) {
        jstring err = env->NewStringUTF("Invalid context or null audio samples");
        jobject res = env->NewObject(result_class, result_ctor, JNI_FALSE, err, nullptr, (jlong)0, 0.0f);
        return res;
    }

    auto *engine = reinterpret_cast<WhisperEngine *>(context_ptr);
    jsize n_samples = env->GetArrayLength(samples);
    jfloat *samples_ptr = env->GetFloatArrayElements(samples, nullptr);

    std::string lang = "en";
    if (language) {
        const char *lang_utf = env->GetStringUTFChars(language, nullptr);
        if (lang_utf) {
            lang = lang_utf;
            env->ReleaseStringUTFChars(language, lang_utf);
        }
    }

    WhisperTranscriptionResult native_res = engine->transcribe(samples_ptr, n_samples, n_threads, lang);
    env->ReleaseFloatArrayElements(samples, samples_ptr, JNI_ABORT);

    // Build segments array
    jclass segment_class = env->FindClass("com/roywatson/android_voice_whisperer/whisper/WhisperNativeSegment");
    jmethodID segment_ctor = env->GetMethodID(segment_class, "<init>", "(JJLjava/lang/String;F)V");

    jobjectArray segments_array = env->NewObjectArray(
        native_res.segments.size(),
        segment_class,
        nullptr
    );

    for (size_t i = 0; i < native_res.segments.size(); ++i) {
        const auto &seg = native_res.segments[i];
        jstring text_str = env->NewStringUTF(seg.text.c_str());
        jobject seg_obj = env->NewObject(
            segment_class,
            segment_ctor,
            static_cast<jlong>(seg.t0_ms),
            static_cast<jlong>(seg.t1_ms),
            text_str,
            seg.confidence
        );
        env->SetObjectArrayElement(segments_array, i, seg_obj);
        env->DeleteLocalRef(text_str);
        env->DeleteLocalRef(seg_obj);
    }

    jstring err_msg = env->NewStringUTF(native_res.error_message.c_str());
    jobject final_result = env->NewObject(
        result_class,
        result_ctor,
        native_res.success ? JNI_TRUE : JNI_FALSE,
        err_msg,
        segments_array,
        static_cast<jlong>(native_res.inference_time_ms),
        native_res.real_time_factor
    );

    return final_result;
}

JNIEXPORT jstring JNICALL
Java_com_roywatson_android_1voice_1whisperer_whisper_WhisperNativeBridge_getSystemInfo(
    JNIEnv *env,
    jobject /* thiz */
) {
    std::string info = WhisperEngine::get_system_info();
    return env->NewStringUTF(info.c_str());
}

} // extern "C"
