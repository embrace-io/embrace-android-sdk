package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json

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
internal data class Log(

    /* The time the log was captured, in nanoseconds since the Unix epoch */
    @Json(name = "time_unix_nano")
    val timeUnixNano: Long? = null,

    /* Numerical value of the severity, normalized to values going from 1 to 24, where
    1 is the least severe and 24 is the most severe. The ranges are as follows: 1-4: Trace,
     5-8: Debug, 9-12: Info, 13-16: Warn, 17-20: Error, 21-24: Fatal. */
    @Json(name = "severity_number")
    val severityNumber: Int? = null,

    /* Also known as log level. This is the original string representation of the severity
    as it is known at the source. */
    @Json(name = "severity_text")
    val severityText: String? = null,

    @Json(name = "body")
    val body: LogBody? = null,

    @Json(name = "attributes")
    val attributes: List<Attribute>? = null,

    /* Request trace ID as defined in W3C Trace Context. Can be set for logs that are part of
    request processing and have an assigned trace id. */
    @Json(name = "trace_id")
    val traceId: String? = null,

    /* The span ID of the log. Can be set for logs that are part of a particular processing span.
     If span_id is present, trace_id SHOULD be also present. */
    @Json(name = "span_id")
    val spanId: String? = null
)
