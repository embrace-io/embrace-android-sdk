package io.embrace.android.gradle.plugin.instrumentation.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class InstrumentationConfigVisitStrategy(
    val type: String,
    val value: String,
)
