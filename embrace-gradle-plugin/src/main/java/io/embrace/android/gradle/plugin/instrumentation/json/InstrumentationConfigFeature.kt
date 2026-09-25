package io.embrace.android.gradle.plugin.instrumentation.json

import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
internal data class InstrumentationConfigFeature(
    val name: String,
    val target: InstrumentationConfigTarget,
    val insert: InstrumentationConfigInsert,
    val visitStrategy: InstrumentationConfigVisitStrategy,
    val addOverride: InstrumentationConfigAddOverride?,
)
