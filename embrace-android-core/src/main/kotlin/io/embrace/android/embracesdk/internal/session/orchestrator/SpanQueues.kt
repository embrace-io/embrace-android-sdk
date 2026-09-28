package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Span

/**
 * A queue of completed spans that are waiting to be written to disk. A span completes once, so
 * nothing in this queue is ever superseded and it is never compacted.
 */
internal fun completedSpansQueue(): TelemetryQueue<Span> = OrderedTelemetryQueue()

/**
 * A queue of span snapshots that are waiting to be written to disk.
 */
internal fun spanSnapshotsQueue(): TelemetryQueue<EmbraceSdkSpan> =
    CompactingTelemetryQueue(identityOf = EmbraceSdkSpan::spanId)
