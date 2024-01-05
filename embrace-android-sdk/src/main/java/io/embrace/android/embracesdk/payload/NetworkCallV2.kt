package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class NetworkCallV2(
    /** The URL being requested.  */
    @Json(name = "url")
    val url: String? = null,

    /** The HTTP method the network request corresponds to.  */
    @Json(name = "x")
    val httpMethod: String? = null,

    /** The HTTP response code.  */
    @Json(name = "rc")
    val responseCode: Int? = null,

    /** The number of bytes sent during the network request.  */
    @Json(name = "bo")
    val bytesSent: Long = 0,

    /** The number of bytes received during the network request.  */
    @Json(name = "bi")
    val bytesReceived: Long = 0,

    /** The start time of the request.  */
    @Json(name = "st")
    val startTime: Long = 0,

    /** The end time of the request.  */
    @Json(name = "et")
    val endTime: Long = 0,

    /** The duration of the network request.  */
    @Json(name = "dur")
    val duration: Long = 0,

    /** The trace ID that can be used to trace a particular request.  */
    @Json(name = "t")
    val traceId: String? = null,

    /** If an exception was thrown, the name of the class which caused the exception.  */
    @Json(name = "ed")
    val errorType: String? = null,

    /** If an exception was thrown, the exception message.  */
    @Json(name = "de")
    val errorMessage: String? = null,

    /** A Traceparent that is W3C compliant to be used to create a span for the this network request */
    @Json(name = "w3c_traceparent")
    val w3cTraceparent: String? = null
)
