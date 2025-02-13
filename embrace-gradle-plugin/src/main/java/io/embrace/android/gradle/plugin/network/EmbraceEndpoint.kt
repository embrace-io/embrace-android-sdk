package io.embrace.android.gradle.plugin.network

enum class EmbraceEndpoint(val url: String) {
    BUILD_DATA("/v2/debug/android/build"),
    SOURCE_MAP("/v2/store/sourcemap"),
    NDK_HANDSHAKE("/v2/store/ndk/handshake"),
    NDK("/v2/store/ndk"),
    PROGUARD("/v2/store/proguard"),
    METHOD_MAP("/v2/store/methodmap"),
    LINE_MAP("/v2/store/linemap")
}
