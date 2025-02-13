package io.embrace.android.gradle.plugin.tasks.ndk

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class NdkSymbolsResource(
    val symbols: Map<String, Map<String, String>>
)
