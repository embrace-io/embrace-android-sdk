package io.embrace.android.gradle.plugin.instrumentation.json

import kotlinx.serialization.Serializable

@Serializable
internal data class InstrumentationConfigTarget(
    val name: String,
    val descriptor: String,
)
