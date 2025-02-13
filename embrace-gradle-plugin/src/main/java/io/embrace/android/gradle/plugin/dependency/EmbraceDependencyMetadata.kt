package io.embrace.android.gradle.plugin.dependency

import io.embrace.embrace_gradle_plugin.BuildConfig

// Neither group nor name will likely ever change, so it is good to be here.
private const val EMBRACE_SDK_GROUP = "io.embrace"
private const val EMBRACE_CORE_SDK_NAME = "embrace-android-sdk"
private const val EMBRACE_OKHTTP_NAME = "embrace-android-okhttp3"

internal sealed class EmbraceDependencyMetadata(
    val group: String,
    val artefact: String,
    val version: String,
) {
    fun gradleShortNomenclature() = "$group:$artefact:$version"
    class Core(version: String = BuildConfig.VERSION) : EmbraceDependencyMetadata(
        EMBRACE_SDK_GROUP,
        EMBRACE_CORE_SDK_NAME,
        version
    )
    class OkHttp(version: String = BuildConfig.VERSION) : EmbraceDependencyMetadata(
        EMBRACE_SDK_GROUP,
        EMBRACE_OKHTTP_NAME,
        version
    )
}
