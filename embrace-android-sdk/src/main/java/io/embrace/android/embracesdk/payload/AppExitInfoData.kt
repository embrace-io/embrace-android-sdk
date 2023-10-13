package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal data class AppExitInfoData(
    @SerializedName("sid")
    internal val sessionId: String?,

    @SerializedName("side")
    internal val sessionIdError: String?,

    // the importance of the process that it used to have before the death.
    @SerializedName("im")
    internal val importance: Int?,

    // Last proportional set size of the memory that the process had used in Bytes.
    @SerializedName("pss")
    internal val pss: Long?,

    @SerializedName("rs")
    internal val reason: Int?,

    // Last resident set size of the memory that the process had used in Bytes.
    @SerializedName("rss")
    internal val rss: Long?,

    // The exit status argument of exit() if the application calls it,
    // or the signal number if the application is signaled.
    @SerializedName("st")
    internal val status: Int?,

    @SerializedName("ts")
    internal val timestamp: Long?,

    // file with ANR/CRASH traces compressed as string
    @SerializedName("blob")
    internal val trace: String?,

    @SerializedName("ds")
    internal val description: String?,

    // Error or Exception if the traces couldn't be collected
    @SerializedName("trs")
    internal val traceStatus: String?
)
