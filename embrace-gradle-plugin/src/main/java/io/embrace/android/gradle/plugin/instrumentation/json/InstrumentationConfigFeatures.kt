package io.embrace.android.gradle.plugin.instrumentation.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class InstrumentationConfigFeatures(
    val features: List<InstrumentationConfigFeature>,
)
