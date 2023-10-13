package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal class NativeCrashDataError(
    @SerializedName("n") val number: Int?,
    @SerializedName("c") val context: Int?
)
