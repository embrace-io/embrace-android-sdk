package io.embrace.android.embracesdk.internal.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class NativeSymbols(
    @SerialName("symbols")
    val symbols: Map<String, Map<String, String>>,
)
