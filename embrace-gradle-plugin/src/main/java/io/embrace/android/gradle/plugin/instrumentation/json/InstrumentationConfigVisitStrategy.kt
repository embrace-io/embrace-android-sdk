package io.embrace.android.gradle.plugin.instrumentation.json

import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
internal data class InstrumentationConfigVisitStrategy(
    val type: String,
    val value: String? = null,
)
