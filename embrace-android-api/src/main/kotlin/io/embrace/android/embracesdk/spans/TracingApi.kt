package io.embrace.android.embracesdk.spans

/**
 * The public API used to add traces to your application. Note that [recordCompletedSpan] can be used before the SDK is initialized.
 * The actual trace won't be recorded until the SDK is started, but it's safe to use this prior to SDK initialization.
 */
public interface TracingApi {

    /**
     * Create an [EmbraceSpan] with the given name that will be the root span of a new trace. Returns null if the [EmbraceSpan] cannot
     * be created given the current conditions of the SDK or an invalid name.
     *
     * Note: the [EmbraceSpan] created will not be started. For a method that creates and starts the span, use [startSpan]
     */
    public fun createSpan(
        name: String,
        autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
    ): EmbraceSpan? = createSpan(name = name, parent = null, autoTerminationMode = autoTerminationMode)

    /**
     * Create an [EmbraceSpan] with the given name and parent. Passing in a parent that is null result in a new trace with this
     * [EmbraceSpan] as its root. Returns null if the [EmbraceSpan] cannot be created, e.g if the parent has not been started,
     * the name is invalid, or some other factor due to the current conditions of the SDK.
     *
     * * Note: the [EmbraceSpan] created will not be started. For a method that creates and starts the span, use [startSpan]
     */
    public fun createSpan(
        name: String,
        parent: EmbraceSpan?,
        autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
    ): EmbraceSpan?

    /**
     * Create, start, and return a new [EmbraceSpan] with the given name that will be the root span of a new trace. Returns null if the
     * [EmbraceSpan] cannot be created or started.
     */
    public fun startSpan(
        name: String,
        autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
    ): EmbraceSpan? = startSpan(name = name, parent = null, autoTerminationMode = autoTerminationMode)

    /**
     * Create, start, and return a new [EmbraceSpan] with the given name and parent. Returns null if the [EmbraceSpan] cannot be created
     * or started, like if the parent has been started.
     */
    public fun startSpan(
        name: String,
        parent: EmbraceSpan?,
        autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
    ): EmbraceSpan? = startSpan(
        name = name,
        parent = parent,
        startTimeMs = null,
        autoTerminationMode = autoTerminationMode,
    )

    /**
     * Create, start, and return a new [EmbraceSpan] with the given name, parent, and start time (epoch time in milliseconds).
     * Returns null if the [EmbraceSpan] cannot be created or started, like if the parent has been started.
     */
    public fun startSpan(
        name: String,
        parent: EmbraceSpan?,
        startTimeMs: Long?,
        autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
    ): EmbraceSpan?

    /**
     * Execute the given block of code and record a new trace around it. If the span cannot be created, the block of code will still run and
     * return correctly. If an exception or error is thrown inside the block, the span will end at the point of the throw and the
     * [Throwable] will be rethrown.
     */
    public fun <T> recordSpan(
        name: String,
        autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
        code: () -> T,
    ): T = recordSpan(
        name = name,
        parent = null,
        attributes = null,
        events = null,
        code = code,
        autoTerminationMode = autoTerminationMode,
    )

    /**
     * Execute the given block of code and record a new span around it with the given parent. Passing in a parent that is null will result
     * in a new trace with the new span as its root. If the span cannot be created, the block of code will still run and
     * return correctly. If an exception or error is thrown inside the block, the span will end at the point of the throw and the
     * [Throwable] will be rethrown.
     */
    public fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
        code: () -> T,
    ): T = recordSpan(
        name = name,
        parent = parent,
        attributes = null,
        events = null,
        code = code,
        autoTerminationMode = autoTerminationMode,
    )

    /**
     * Execute the given block of code and record a new trace around it with optional attributes and list of [EmbraceSpanEvent]. If the span
     * cannot be created, the block of code will still run and return correctly. If an exception or error is thrown inside the block,
     * the span will end at the point of the throw and the [Throwable] will be rethrown.
     */
    public fun <T> recordSpan(
        name: String,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?,
        autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
        code: () -> T,
    ): T = recordSpan(
        name = name,
        parent = null,
        attributes = attributes,
        events = events,
        code = code,
        autoTerminationMode = autoTerminationMode,
    )

    /**
     * Execute the given block of code and record a new span around it with the given parent with optional attributes and list
     * of [EmbraceSpanEvent]. Passing in a parent that is null will result in a new trace with the new span as its root. If the span
     * cannot be created, the block of code will still run and return correctly. If an exception or error is thrown inside the block,
     * the span will end at the point of the throw and the
     * [Throwable] will be rethrown.
     */
    public fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?,
        autoTerminationMode: AutoTerminationMode = AutoTerminationMode.NONE,
        code: () -> T,
    ): T

    /**
     * Record a span with the given name, start time, and end time (epoch time in milliseconds). The created span will be the root span of
     * a new trace.
     */
    public fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
    ): Boolean =
        recordCompletedSpan(
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            errorCode = null,
            parent = null,
            attributes = null,
            events = null
        )

    /**
     * Record a span with the given name, error code, start time, and end time (epoch time in milliseconds). The created span will be the
     * root span of a new trace. A non-null [ErrorCode] can be passed in to denote the operation the span represents was ended
     * unsuccessfully under the stated circumstances.
     */
    public fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
    ): Boolean =
        recordCompletedSpan(
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            errorCode = errorCode,
            parent = null,
            attributes = null,
            events = null
        )

    /**
     * Record a span with the given name, parent, start time, and end time (epoch time in milliseconds). Passing in a parent that is null
     * will result in a new trace with the new span as its root.
     */
    public fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        parent: EmbraceSpan?,
    ): Boolean =
        recordCompletedSpan(
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            errorCode = null,
            parent = parent,
            attributes = null,
            events = null
        )

    /**
     * Record a span with the given name, parent, error code, start time, and end time (epoch time in milliseconds). Passing in a parent
     * that is null will result in a new trace with the new span as its root. A non-null [ErrorCode] can be passed in to denote the
     * operation the span represents was ended unsuccessfully under the stated circumstances.
     */
    public fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
    ): Boolean = recordCompletedSpan(
        name = name,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        errorCode = errorCode,
        parent = parent,
        attributes = null,
        events = null
    )

    /**
     * Record a span with the given name, start time, and end time (epoch time in milliseconds). The created span will be the root span of
     * a new trace. You can also pass in a [Map] with [String] keys and values to be used as the attributes of the recorded span, or
     * a [List] of [EmbraceSpanEvent] to be used as the events of the recorded span.
     */
    public fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?,
    ): Boolean = recordCompletedSpan(
        name = name,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        errorCode = null,
        parent = null,
        attributes = attributes,
        events = events
    )

    /**
     * Record a span with the given name, error code, parent, start time, and end time (epoch time in milliseconds). Passing in a parent
     * that is null will result in a new trace with the new span as its root. A non-null [ErrorCode] can be passed in to denote the
     * operation the span represents was ended unsuccessfully under the stated circumstances. You can also pass in a [Map]
     * with [String] keys and values to be used as the attributes of the recorded span, or a [List] of [EmbraceSpanEvent] to be used
     * as the events of the recorded span.
     */
    public fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>?,
        events: List<EmbraceSpanEvent>?,
    ): Boolean

    /**
     * Return the [EmbraceSpan] corresponding to the given [spanId] if it is active or it has completed in the current session.
     * Returns null if a span corresponding to the given [spanId] cannot be found, which can happen if this span never existed or
     * if was completed in a prior session.
     */
    public fun getSpan(spanId: String): EmbraceSpan?
}
