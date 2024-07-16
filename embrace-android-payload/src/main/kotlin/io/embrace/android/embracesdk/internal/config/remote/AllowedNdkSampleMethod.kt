package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class AllowedNdkSampleMethod(
    @Json(name = "c") public val clz: String? = null,
    @Json(name = "m") public val method: String? = null
)
