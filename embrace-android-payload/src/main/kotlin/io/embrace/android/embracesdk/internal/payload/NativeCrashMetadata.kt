package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public data class NativeCrashMetadata(
    @Json(name = "a") public val appInfo: AppInfo,
    @Json(name = "d") public val deviceInfo: DeviceInfo,
    @Json(name = "u") public val userInfo: UserInfo,
    @Json(name = "sp") public val sessionProperties: Map<String, String>?
)
