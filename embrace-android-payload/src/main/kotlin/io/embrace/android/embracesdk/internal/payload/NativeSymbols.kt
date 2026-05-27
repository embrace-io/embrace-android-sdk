package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
class NativeSymbols(
    @SerialName("symbols")
    @Json(name = "symbols")
    val symbols: Map<String, Map<String, String>>,
)
