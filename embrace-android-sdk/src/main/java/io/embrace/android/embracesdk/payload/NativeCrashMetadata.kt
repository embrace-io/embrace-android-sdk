package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal class NativeCrashMetadata(
    @SerializedName("a") val appInfo: AppInfo,
    @SerializedName("d") val deviceInfo: DeviceInfo,
    @SerializedName("u") val userInfo: UserInfo,
    @SerializedName("sp") val sessionProperties: Map<String, String>?
)
