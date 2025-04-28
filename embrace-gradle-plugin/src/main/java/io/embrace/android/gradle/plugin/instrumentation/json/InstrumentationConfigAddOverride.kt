package io.embrace.android.gradle.plugin.instrumentation.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class InstrumentationConfigAddOverride(
    val owner: String,
    val name: String,
    val descriptor: String,
)
