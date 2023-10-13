package io.embrace.android.embracesdk.spans

import io.embrace.android.embracesdk.BetaApi

/**
 * The public API used to add traces to your application. Use [isTracingAvailable] to determine if the SDK is ready log traces. Note that
 * [recordCompletedSpan] methods can still be invoked successfully before the [isTracingAvailable] returns true - the actual trace won't
 * be recorded until the system is ready, but the SDK will buffer the call and record it once it is. The other tracing methods, however,
 * will not work until [isTracingAvailable] returns true.
 */
@BetaApi
public interface TracingApi {
    /**
     * Returns true if the tracing API is fully initialized so that [createSpan] and [recordSpan] methods will work. This is different than
     * what [Embrace.isEnabled] returned as the tracing service is initialized asynchronously shortly after the SDK is initialized. Until
     * this returns true, the [recordCompletedSpan] method can be used as invocations to it will be buffered and replayed when tracing
     * service is ready to be used.
     */
    @BetaApi
    public fun isTracingAvailable(): Boolean
    /**
     * Create an [EmbraceSpan] with the given name that will be the root span of a new trace. Returns null if the [EmbraceSpan] cannot
     * be created given the current conditions of the SDK or an invalid name.
     */
    @BetaApi
    public fun createSpan(
        name: String
    ): EmbraceSpan?

    /**
     * Create an [EmbraceSpan] with the given name and parent. Passing in a parent that is null result in a new trace with this
     * [EmbraceSpan] as its root. Returns null if the [EmbraceSpan] cannot be created, e.g if the parent has not been started,
     * the name is invalid, or some other factor due to the current conditions of the SDK.
     */
    @BetaApi
    public fun createSpan(
        name: String,
        parent: EmbraceSpan?
    ): EmbraceSpan?

    /**
     * Execute the given block of code and record a new trace around it. If the span cannot be created, the block of code will still run and
     * return correctly. If an exception or error is thrown inside the block, the span will end at the point of the throw and the
     * [Throwable] will be rethrown.
     */
    @BetaApi
    public fun <T> recordSpan(
        name: String,
        code: () -> T
    ): T

    /**
     * Execute the given block of code and record a new span around it with the given parent. Passing in a parent that is null will result
     * in a new trace with the new span as its root. If the span cannot be created, the block of code will still run and
     * return correctly. If an exception or error is thrown inside the block, the span will end at the point of the throw and the
     * [Throwable] will be rethrown.
     */
    @BetaApi
    public fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        code: () -> T
    ): T

    /**
     * Record a span with the given name as well as start and end times, which will be the root span of a new trace.
     */
    @BetaApi
    public fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long
    ): Boolean

    /**
     * Record a span with the given name, error code, as well as start and end times, which will be the root span of a new trace. A
     * non-null [ErrorCode] can be passed in to denote the operation the span represents was ended unsuccessfully under the stated
     * circumstances.
     */
    @BetaApi
    public fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        errorCode: ErrorCode?
    ): Boolean

    /**
     * Record a span with the given name, parent, as well as start and end times. Passing in a parent that is null will result
     * in a new trace with the new span as its root.
     */
    @BetaApi
    public fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        parent: EmbraceSpan?
    ): Boolean

    /**
     * Record a span with the given name, parent, error code, as well as start and end times. Passing in a parent that is null will result
     * in a new trace with the new span as its root. A non-null [ErrorCode] can be passed in to denote the operation the span represents
     * was ended unsuccessfully under the stated circumstances.
     */
    @BetaApi
    public fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
    ): Boolean

    /**
     * Record a span with the given name as well as start and end times, which will be the root span of a new trace. You can also pass in
     * a [Map] with [String] keys and values to be used as the attributes of the recorded span, or a [List] of [EmbraceSpanEvent] to be
     * used as the events of the recorded span.
     */
    @BetaApi
    public fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?
    ): Boolean

    /**
     * Record a span with the given name, error code, parent, as well as start and end times. Passing in a parent that is null will result
     * in a new trace with the new span as its root. A non-null [ErrorCode] can be passed in to denote the operation the span represents
     * was ended unsuccessfully under the stated circumstances. You can also pass in a [Map] with [String] keys and values to be used as
     * the attributes of the recorded span, or a [List] of [EmbraceSpanEvent] to be used as the events of the recorded span.
     */
    @BetaApi
    public fun recordCompletedSpan(
        name: String,
        startTimeNanos: Long,
        endTimeNanos: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?
    ): Boolean
}
