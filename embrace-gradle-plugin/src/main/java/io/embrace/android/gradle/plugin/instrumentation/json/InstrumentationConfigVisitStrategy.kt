package io.embrace.android.gradle.plugin.instrumentation.json

import kotlinx.serialization.Serializable

@Serializable
internal data class InstrumentationConfigVisitStrategy(
    val type: String,
    val value: String? = null,
)
