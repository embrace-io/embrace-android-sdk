package io.embrace.android.gradle.plugin.instrumentation.json

import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
internal data class InstrumentationConfigInsert(
    val owner: String,
    val name: String,
    val descriptor: String,
    val operandStackIndices: List<Int>,
    val insertAtEnd: Boolean = false,
)
