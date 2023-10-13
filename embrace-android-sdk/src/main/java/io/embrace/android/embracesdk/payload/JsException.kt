package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal class JsException(
    @SerializedName("n") var name: String?,
    @SerializedName("m") var message: String?,
    @SerializedName("t") var type: String?,
    @SerializedName("st") var stacktrace: String?
)
