package io.embrace.android.gradle.plugin.instrumentation.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class InstrumentationConfigFeature(
    val name: String,
    val target: InstrumentationConfigTarget,
    val insert: InstrumentationConfigInsert,
    val visitStrategy: InstrumentationConfigVisitStrategy,
)
