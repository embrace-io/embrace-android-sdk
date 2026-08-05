package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.spans.AutoTerminationMode

/**
 * Internal representation of how a span should be terminated automatically. This mirrors the public
 * [AutoTerminationMode] enum but additionally allows a [Timeout] that carries the duration after
 * which an in-flight span is assumed to have leaked (and can be stopped with a failure error code).
 */
sealed class SpanTerminationMode {

    /**
     * The span will not terminate automatically.
     */
    object None : SpanTerminationMode()

    /**
     * The span will terminate when the app goes to the background.
     */
    object OnBackground : SpanTerminationMode()

    /**
     * The span will be failed once [timeoutMs] has elapsed since it started.
     */
    data class Timeout(val timeoutMs: Long) : SpanTerminationMode()
}

/**
 * Maps the public [AutoTerminationMode] onto its internal [SpanTerminationMode] equivalent.
 */
fun AutoTerminationMode.toTerminationMode(): SpanTerminationMode = when (this) {
    AutoTerminationMode.NONE -> SpanTerminationMode.None
    AutoTerminationMode.ON_BACKGROUND -> SpanTerminationMode.OnBackground
}

/**
 * Maps the internal [SpanTerminationMode] back onto the public [AutoTerminationMode]. [SpanTerminationMode.Timeout]
 * has no public equivalent, so it surfaces as [AutoTerminationMode.NONE].
 */
fun SpanTerminationMode.toAutoTerminationMode(): AutoTerminationMode = when (this) {
    SpanTerminationMode.OnBackground -> AutoTerminationMode.ON_BACKGROUND
    else -> AutoTerminationMode.NONE
}
