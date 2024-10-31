package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class JsException(
    @Json(name = "n") var name: String?,
    @Json(name = "m") var message: String?,
    @Json(name = "t") var type: String?,
    @Json(name = "st") var stacktrace: String?,
)
