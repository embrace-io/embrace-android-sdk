package io.embrace.android.gradle.plugin.instrumentation.json

import kotlinx.serialization.Serializable

@Serializable
internal data class InstrumentationConfigInsert(
    val owner: String,
    val name: String,
    val descriptor: String,
    val operandStackIndices: List<Int>,
    val insertAtEnd: Boolean = false,
)
