package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Span

/**
 * A queue of completed spans that are waiting to be written to disk.
 */
internal fun completedSpansQueue(): TelemetryQueue<Span> = TelemetryQueue(identityOf = Span::spanId)

/**
 * A queue of span snapshots that are waiting to be written to disk.
 */
internal fun spanSnapshotsQueue(): TelemetryQueue<EmbraceSdkSpan> = TelemetryQueue(identityOf = EmbraceSdkSpan::spanId)
