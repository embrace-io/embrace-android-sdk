package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.internal.comms.api.ApiClient

@JsonClass(generateAdapter = true)
internal data class EventMessage(
    @Json(name = "et")
    val event: Event,

    @Json(name = "d")
    val deviceInfo: DeviceInfo? = null,

    @Json(name = "a")
    val appInfo: AppInfo? = null,

    @Json(name = "u")
    val userInfo: UserInfo? = null,

    @Json(name = "sk")
    val stacktraces: Stacktraces? = null,

    @Json(name = "v")
    val version: Int = ApiClient.MESSAGE_VERSION,

    @Json(name = "crn")
    val nativeCrash: NativeCrash? = null
)
