package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.internal.utils.MessageUtils.withMap

internal class NativeCrashMetadata(
    @SerializedName("a") val appInfo: AppInfo,
    @SerializedName("d") val deviceInfo: DeviceInfo,
    @SerializedName("u") val userInfo: UserInfo,
    @SerializedName("sp") val sessionProperties: Map<String?, String?>?
) {

    fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{\"a\":")
        sb.append(appInfo.toJson())
        sb.append(",\"d\":")
        sb.append(deviceInfo.toJson())
        sb.append(",\"u\":")
        sb.append(userInfo.toJson())
        sb.append(",\"sp\":")
        sb.append(withMap(sessionProperties))
        sb.append("}")
        return sb.toString()
    }
}
