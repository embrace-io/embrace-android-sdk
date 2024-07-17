package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal class NativeCrashMetadata(
    @Json(name = "a") val appInfo: AppInfo,
    @Json(name = "d") val deviceInfo: DeviceInfo,
    @Json(name = "u") val userInfo: UserInfo,
    @Json(name = "sp") val sessionProperties: Map<String, String>?
)
