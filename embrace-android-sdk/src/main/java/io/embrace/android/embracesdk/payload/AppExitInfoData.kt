package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class AppExitInfoData(
    @Json(name = "sid")
    internal val sessionId: String?,

    @Json(name = "side")
    internal val sessionIdError: String?,

    // the importance of the process that it used to have before the death.
    @Json(name = "im")
    internal val importance: Int?,

    // Last proportional set size of the memory that the process had used in Bytes.
    @Json(name = "pss")
    internal val pss: Long?,

    @Json(name = "rs")
    internal val reason: Int?,

    // Last resident set size of the memory that the process had used in Bytes.
    @Json(name = "rss")
    internal val rss: Long?,

    // The exit status argument of exit() if the application calls it,
    // or the signal number if the application is signaled.
    @Json(name = "st")
    internal val status: Int?,

    @Json(name = "ts")
    internal val timestamp: Long?,

    // file with ANR/CRASH traces compressed as string
    @Json(name = "blob")
    internal val trace: String?,

    @Json(name = "ds")
    internal val description: String?,

    // Error or Exception if the traces couldn't be collected
    @Json(name = "trs")
    internal val traceStatus: String?
)
