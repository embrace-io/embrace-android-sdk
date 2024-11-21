package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class NativeSymbols(
    @Json(name = "symbols")
    val symbols: Map<String, Map<String, String>>,
)
