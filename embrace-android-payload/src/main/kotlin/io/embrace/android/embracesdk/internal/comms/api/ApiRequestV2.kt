package io.embrace.android.embracesdk.internal.comms.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiRequestV2(
    val contentType: String = "application/json",

    val userAgent: String,

    val contentEncoding: String? = null,

    val accept: String = "application/json",

    val acceptEncoding: String? = null,

    val appId: String? = null,

    val deviceId: String? = null,

    val eventId: String? = null,

    val logId: String? = null,

    val url: String,

    val eTag: String? = null,
)
