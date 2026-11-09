cmake_minimum_required(VERSION 3.22.1)
project("whisper_jni" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Global optimizations applied to ggml, whisper, and JNI bridge
add_compile_options(-O3 -fPIC)

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_compile_options(-march=armv8.2-a+fp16+dotprod)
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    add_compile_options(-mfpu=neon-vfpv4 -mfloat-abi=softfp)
endif()

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Add whisper.cpp source tree with hardware optimizations enabled
add_subdirectory(whisper_src whisper_build)

# Create our JNI shared library
add_library(
    whisper_jni
    SHARED
    whisper_engine.cpp
    whisper_jni.cpp
)

target_include_directories(
    whisper_jni
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper_src/include
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper_src/src
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper_src/ggml/include
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    whisper_jni
    PRIVATE
    whisper
    ggml
    ${log-lib}
    ${android-lib}
)
