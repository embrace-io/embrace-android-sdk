package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal class NativeCrashDataError(
    @Json(name = "n") val number: Int?,
    @Json(name = "c") val context: Int?
)
