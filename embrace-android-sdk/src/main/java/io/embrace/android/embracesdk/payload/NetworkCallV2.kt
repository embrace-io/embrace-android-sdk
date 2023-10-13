package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal data class NetworkCallV2(
    /** The URL being requested.  */
    @SerializedName("url")
    val url: String? = null,

    /** The HTTP method the network request corresponds to.  */
    @SerializedName("x")
    val httpMethod: String? = null,

    /** The HTTP response code.  */
    @SerializedName("rc")
    val responseCode: Int? = null,

    /** The number of bytes sent during the network request.  */
    @SerializedName("bo")
    val bytesSent: Long = 0,

    /** The number of bytes received during the network request.  */
    @SerializedName("bi")
    val bytesReceived: Long = 0,

    /** The start time of the request.  */
    @SerializedName("st")
    val startTime: Long = 0,

    /** The end time of the request.  */
    @SerializedName("et")
    val endTime: Long = 0,

    /** The duration of the network request.  */
    @SerializedName("dur")
    val duration: Long = 0,

    /** The trace ID that can be used to trace a particular request.  */
    @SerializedName("t")
    val traceId: String? = null,

    /** If an exception was thrown, the name of the class which caused the exception.  */
    @SerializedName("ed")
    val errorType: String? = null,

    /** If an exception was thrown, the exception message.  */
    @SerializedName("de")
    val errorMessage: String? = null,

    /** A Traceparent that is W3C compliant to be used to create a span for the this network request */
    @SerializedName("w3c_traceparent")
    val w3cTraceparent: String? = null
)
