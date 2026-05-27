package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A recording of an event. Typically the record includes a timestamp indicating when the event
 * happened, as well as other data that describes what happened, where it happened, etc.
 *
 * @param timeUnixNano The time the log was captured, in nanoseconds since the Unix epoch
 * @param severityNumber Numerical value of the severity, normalized to values going from 1 to 24,
 * where 1 is the least severe and 24 is the most severe. The ranges are as follows: 1-4: Trace,
 * 5-8: Debug, 9-12: Info, 13-16: Warn, 17-20: Error, 21-24: Fatal.
 * @param severityText Also known as log level. This is the original string representation of the
 * severity as it is known at the source.
 * @param body
 * @param attributes
 * @param traceId Request trace ID as defined in W3C Trace Context. Can be set for logs that are
 * part of request processing and have an assigned trace id.
 * @param spanId The span ID of the log. Can be set for logs that are part of a particular
 * processing span. If span_id is present, trace_id SHOULD be also present.
 */
@Serializable
@JsonClass(generateAdapter = true)
data class Log(

    /* The time the log was captured, in nanoseconds since the Unix epoch */
    @SerialName("time_unix_nano")
    @Json(name = "time_unix_nano")
    val timeUnixNano: Long? = null,

    /* Numerical value of the severity, normalized to values going from 1 to 24, where
    1 is the least severe and 24 is the most severe. The ranges are as follows: 1-4: Trace,
     5-8: Debug, 9-12: Info, 13-16: Warn, 17-20: Error, 21-24: Fatal. */
    @SerialName("severity_number")
    @Json(name = "severity_number")
    val severityNumber: Int? = null,

    /* Also known as log level. This is the original string representation of the severity
    as it is known at the source. */
    @SerialName("severity_text")
    @Json(name = "severity_text")
    val severityText: String? = null,

    @SerialName("body")
    @Json(name = "body")
    val body: String? = null,

    @SerialName("attributes")
    @Json(name = "attributes")
    val attributes: List<Attribute>? = null,

    /* Request trace ID as defined in W3C Trace Context. Can be set for logs that are part of
    request processing and have an assigned trace id. */
    @SerialName("trace_id")
    @Json(name = "trace_id")
    val traceId: String? = null,

    /* The span ID of the log. Can be set for logs that are part of a particular processing span.
     If span_id is present, trace_id SHOULD be also present. */
    @SerialName("span_id")
    @Json(name = "span_id")
    val spanId: String? = null,
)
