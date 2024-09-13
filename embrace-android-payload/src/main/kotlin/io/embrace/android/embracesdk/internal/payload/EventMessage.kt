package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EventMessage(
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
    val version: Int = MESSAGE_VERSION,

    @Json(name = "crn")
    val nativeCrash: NativeCrash? = null
) {

    private companion object {
        /**
         * The version of the API message format.
         */
        const val MESSAGE_VERSION = 13
    }
}
