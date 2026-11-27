package io.embrace.android.embracesdk.internal.otel.state

/**
 * The value of every state at one moment, as the OpenTelemetry pipeline needs it to place telemetry among state changes.
 *
 * An instance never changes, so everything read from one describes the same moment, and a reference to one can be kept
 * for as long as that moment matters.
 */
interface StateStamp {

    /**
     * The number of the last state change before that moment. 0 until a state has a value.
     */
    val sequence: Long

    /**
     * When that change was recorded, in milliseconds by the SDK's clock, which is when these values took effect. Nothing
     * has changed between then and whenever this is read as the current stamp, so the stamp also describes any moment in
     * between.
     */
    val sinceMs: Long

    /**
     * Whether no state has reported a value yet, in which case there is nothing to stamp.
     */
    fun isEmpty(): Boolean

    /**
     * Every state's value as one string, the value of [StateAttributes.STATES]. Built the first time it is read and kept.
     */
    val encoded: String

    /**
     * Every state's value as attributes: [StateAttributes.VALUE_PREFIX] followed by a state's name holds its value, and
     * [StateAttributes.VALUE_TYPE_PREFIX] followed by its name holds [StateAttributes.SYSTEM] if the SDK generated that
     * value. Built the first time it is read and kept.
     */
    val attributes: Map<String, String>

    /**
     * The value of each state that is not what it was in [start], as attributes: [StateAttributes.END_VALUE_PREFIX]
     * followed by a state's name holds its value here, and [StateAttributes.END_VALUE_TYPE_PREFIX] followed by its name
     * holds [StateAttributes.SYSTEM] if the SDK generated that value.
     */
    fun endAttributes(start: StateStamp): Map<String, String>
}
