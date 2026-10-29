package io.embrace.bytecode.json

import kotlinx.serialization.Serializable

@Serializable
internal data class InstrumentationConfigFeatures(
    val features: List<InstrumentationConfigFeature>,
)
