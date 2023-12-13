package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal data class Crash(
    @SerializedName("id")
    @JvmField
    val crashId: String,

    @SerializedName("ex")
    val exceptions: List<ExceptionInfo>? = null,

    @SerializedName("rep_js")
    val jsExceptions: List<String>? = null,

    @SerializedName("th")
    val threads: List<ThreadInfo>? = null
)
