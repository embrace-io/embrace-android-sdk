package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal class NativeCrash(
    @SerializedName("id") val id: String?,
    @SerializedName("m") val crashMessage: String?,
    @SerializedName("sb") val symbols: Map<String?, String?>?,
    @SerializedName("er") val errors: List<NativeCrashDataError?>?,
    @SerializedName("ue") val unwindError: Int?,
    @SerializedName("ma") val map: String?
)
