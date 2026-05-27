package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
class JsException(
    @SerialName("n") @Json(name = "n") var name: String?,
    @SerialName("m") @Json(name = "m") var message: String?,
    @SerialName("t") @Json(name = "t") var type: String?,
    @SerialName("st") @Json(name = "st") var stacktrace: String?,
)
