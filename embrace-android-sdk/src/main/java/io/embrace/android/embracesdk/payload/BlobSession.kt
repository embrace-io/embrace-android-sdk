package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal data class BlobSession(
    @SerializedName("si")
    val sessionId: String? = null
)
