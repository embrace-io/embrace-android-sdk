package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.opentelemetry.api.trace.SpanId

/**
 * A span represents a single unit of work done in the app. It can be a network request, a database
 * query, a view transition, etc. It has a start time, an end time, and attributes that describe it.
 *
 * @param traceId The ID of the trace that this span is part of
 * @param spanId A value that uniquely identifies a span instance
 * @param parentSpanId A value that uniquely identifies the parent span
 * @param name The name of the span
 * @param startTimeNanos The time the span started, in nanoseconds since the Unix epoch
 * @param endTimeNanos The time the span ended, in nanoseconds since the Unix epoch
 * @param status The status of the span. Can be one of 'Unset', 'Error', or 'Ok'
 * @param events
 * @param attributes
 */
@JsonClass(generateAdapter = true)
internal data class Span(

    /* The ID of the trace that this span is part of */
    @Json(name = "trace_id")
    val traceId: String? = null,

    /* A value that uniquely identifies a span instance */
    @Json(name = "span_id")
    val spanId: String? = null,

    /* A value that uniquely identifies the parent span */
    @Json(name = "parent_span_id")
    val parentSpanId: String? = SpanId.getInvalid(),

    /* The name of the span */
    @Json(name = "name")
    val name: String? = null,

    /* The time the span started, in nanoseconds since the Unix epoch */
    @Json(name = "start_time_unix_nano")
    val startTimeNanos: Long? = null,

    /* The time the span ended, in nanoseconds since the Unix epoch */
    @Json(name = "end_time_unix_nano")
    val endTimeNanos: Long? = null,

    /* The status of the span. Can be one of 'Unset', 'Error', or 'Ok' */
    @Json(name = "status")
    val status: Status? = null,

    @Json(name = "events")
    val events: List<SpanEvent>? = null,

    @Json(name = "attributes")
    val attributes: List<Attribute>? = null
) {

    /**
     * The status of the span. Can be one of 'Unset', 'Error', or 'Ok'
     *
     * Values: UNSET,ERROR,OK
     */
    @JsonClass(generateAdapter = false)
    internal enum class Status(val value: String) {
        @Json(name = "Unset")
        UNSET("Unset"),

        @Json(name = "Error")
        ERROR("Error"),

        @Json(name = "Ok")
        OK("Ok")
    }
}
