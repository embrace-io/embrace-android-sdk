package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal data class NetworkEvent(
    @SerializedName("app_id")
    val appId: String,

    @SerializedName("a")
    val appInfo: AppInfo,

    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("id")
    val eventId: String,

    @SerializedName("n")
    val networkCaptureCall: NetworkCapturedCall,

    @SerializedName("ts")
    val timestamp: String,

    @SerializedName("ip")
    val ipAddress: String?,

    @SerializedName("si")
    val sessionId: String? = null
)
