package io.embrace.android.embracesdk.internal.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class JsException(
    @SerialName("n") var name: String?,
    @SerialName("m") var message: String?,
    @SerialName("t") var type: String?,
    @SerialName("st") var stacktrace: String?,
)
