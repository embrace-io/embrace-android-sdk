package io.embrace.android.embracesdk.internal.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
@Serializable
data class Span(

    /* The ID of the trace that this span is part of */
    @SerialName("trace_id")
    val traceId: String? = null,

    /* A value that uniquely identifies a span instance */
    @SerialName("span_id")
    val spanId: String? = null,

    /* A value that uniquely identifies the parent span */
    @SerialName("parent_span_id")
    val parentSpanId: String? = null,

    /* The name of the span */
    @SerialName("name")
    val name: String? = null,

    /* The time the span started, in nanoseconds since the Unix epoch */
    @SerialName("start_time_unix_nano")
    val startTimeNanos: Long? = null,

    /* The time the span ended, in nanoseconds since the Unix epoch */
    @SerialName("end_time_unix_nano")
    val endTimeNanos: Long? = null,

    /* The status of the span. Can be one of 'Unset', 'Error', or 'Ok' */
    @SerialName("status")
    val status: Status? = null,

    @SerialName("events")
    val events: List<SpanEvent>? = null,

    @SerialName("attributes")
    val attributes: List<Attribute>? = null,

    @SerialName("links")
    val links: List<Link>? = null,
) {

    /**
     * The status of the span. Can be one of 'Unset', 'Error', or 'Ok'
     *
     * Values: UNSET,ERROR,OK
     */
    @Serializable
    enum class Status(val value: String) {
        @SerialName("Unset")
        UNSET("Unset"),

        @SerialName("Error")
        ERROR("Error"),

        @SerialName("Ok")
        OK("Ok"),
    }
}
