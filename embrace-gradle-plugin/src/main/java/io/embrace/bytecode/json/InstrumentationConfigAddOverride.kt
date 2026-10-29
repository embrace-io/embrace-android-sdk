package io.embrace.bytecode.json

import kotlinx.serialization.Serializable

@Serializable
internal data class InstrumentationConfigAddOverride(
    val owner: String,
    val name: String,
    val descriptor: String,
)
