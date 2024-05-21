package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class NetworkEvent(
    @Json(name = "app_id")
    val appId: String?,

    @Json(name = "a")
    val appInfo: AppInfo,

    @Json(name = "device_id")
    val deviceId: String,

    @Json(name = "id")
    val eventId: String,

    @Json(name = "n")
    val networkCaptureCall: NetworkCapturedCall,

    @Json(name = "ts")
    val timestamp: String,

    @Json(name = "ip")
    val ipAddress: String?,

    @Json(name = "si")
    val sessionId: String? = null
)
