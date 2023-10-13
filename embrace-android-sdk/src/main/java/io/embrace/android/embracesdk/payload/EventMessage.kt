package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.comms.api.ApiClient

internal data class EventMessage(
    @SerializedName("et")
    val event: Event,

    @SerializedName("cr")
    val crash: Crash? = null,

    @SerializedName("d")
    val deviceInfo: DeviceInfo? = null,

    @SerializedName("a")
    val appInfo: AppInfo? = null,

    @SerializedName("u")
    val userInfo: UserInfo? = null,

    @SerializedName("p")
    val performanceInfo: PerformanceInfo? = null,

    @SerializedName("sk")
    val stacktraces: Stacktraces? = null,

    @SerializedName("v")
    val version: Int = ApiClient.MESSAGE_VERSION,

    @SerializedName("crn")
    val nativeCrash: NativeCrash? = null
)
