package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public data class AppExitInfoData(
    @Json(name = "sid")
    val sessionId: String?,

    @Json(name = "side")
    val sessionIdError: String?,

    // the importance of the process that it used to have before the death.
    @Json(name = "im")
    val importance: Int?,

    // Last proportional set size of the memory that the process had used in Bytes.
    @Json(name = "pss")
    val pss: Long?,

    @Json(name = "rs")
    val reason: Int?,

    // Last resident set size of the memory that the process had used in Bytes.
    @Json(name = "rss")
    val rss: Long?,

    // The exit status argument of exit() if the application calls it,
    // or the signal number if the application is signaled.
    @Json(name = "st")
    val status: Int?,

    @Json(name = "ts")
    val timestamp: Long?,

    // file with ANR/CRASH traces compressed as string
    @Json(name = "blob")
    val trace: String?,

    @Json(name = "ds")
    val description: String?,

    // Error or Exception if the traces couldn't be collected
    @Json(name = "trs")
    val traceStatus: String?
)
