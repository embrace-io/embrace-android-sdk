package io.embrace.android.gradle.plugin.instrumentation.json

import kotlinx.serialization.Serializable

@Serializable
internal data class InstrumentationConfigFeatures(
    val features: List<InstrumentationConfigFeature>,
)
