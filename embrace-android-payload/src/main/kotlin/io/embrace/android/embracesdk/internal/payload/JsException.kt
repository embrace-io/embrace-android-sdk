package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class JsException(
    @Json(name = "n") public var name: String?,
    @Json(name = "m") public var message: String?,
    @Json(name = "t") public var type: String?,
    @Json(name = "st") public var stacktrace: String?
)
