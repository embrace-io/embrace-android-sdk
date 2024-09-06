package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.internal.payload.Span

/**
 * Converts the data captured by a [DataCaptureService] into OpenTelemetry spans. This approach
 * can be desirable as opposed to using a [DataSource] when Embrace has requirements that are
 * incompatible with OTel's restrictions.
 *
 * For example, if we want to limit SpanEvents so that only the last N are captured, it's
 * non-trivial to attempt that with the OTel SDK. Instead, we should use this interface to
 * convert existing services to OTel telemetry, and then add it directly to our payloads.
 *
 * This has the disadvantage of the data is not sent in exporters, so should be used sparingly.
 */
fun interface DataCaptureServiceOtelConverter {

    /**
     * Returns a snapshot of the data captured by the service as a list of spans.
     */
    fun snapshot(isFinalPayload: Boolean): List<Span>?
}
