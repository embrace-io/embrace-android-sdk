package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal data class ThermalState(

    @SerializedName("t")
    internal val timestamp: Long,

    @SerializedName("s")
    internal val status: Int
)
