package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class NativeCrashDataError(
    @Json(name = "n") public val number: Int?,
    @Json(name = "c") public val context: Int?
)
