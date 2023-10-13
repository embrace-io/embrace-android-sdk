package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal data class NetworkRequests(
    @SerializedName("v2") val networkSessionV2: NetworkSessionV2?
)
