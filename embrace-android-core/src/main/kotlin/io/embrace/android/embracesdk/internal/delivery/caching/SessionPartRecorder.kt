package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.delivery.storage.session.Manifest
import io.embrace.android.embracesdk.internal.delivery.storage.session.PersistedSession
import io.embrace.android.embracesdk.internal.delivery.storage.session.SessionMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.session.SessionPartDirectory
import io.embrace.android.embracesdk.internal.delivery.storage.session.SessionPartStore
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.spans.toFailedSpan
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import kotlin.math.max

/**
 * Observes the SDK's telemetry sources and drives [SessionPartStore] with event-driven writes:
 *
 *  - [onSpanUpdate] (fired by the span-update notifier) appends newly-completed spans and rewrites
 *    the in-flight span snapshots.
 *  - [onSessionEnd] (fired when the session is about to end) flushes any span change that arrived
 *    after the last [onSpanUpdate].
 *  - [onUserInfoUpdate] (fired by the user-info change signal) rewrites the metadata file.
 *
 * The session span is written on the existing 2s cache cadence via [SessionPartStore.onCacheSnapshot];
 * the manifest is written once when the part starts.
 *
 * On the next launch, [resurrect] recovers session-part directories that never finalized because the
 * process died, converting still-open snapshots into failed spans as the delivery resurrection path
 * does.
 */
class SessionPartRecorder(
    private val store: SessionPartStore,
    private val spanRepository: SpanRepository,
    private val spanSink: SpanSink,
    private val resourceProvider: () -> EnvelopeResource,
    private val metadataProvider: () -> EnvelopeMetadata,
    private val sessionIdsProvider: () -> SessionIdsSnapshot,
    private val clock: Clock,
    private val uuidProvider: () -> String,
    private val symbolMapProvider: () -> Map<String, String>? = { null },
) {

    // Guards the span writes: the spans recorded by the previous invocation, so a write that would
    // reproduce the identical on-disk state (notably the end-of-session flush after a quiet period)
    // is skipped. Confined to the span-update/session-end callers, which are serialized.
    private var lastSpanState: SpanState? = null

    // These are invoked from the span-update notifier and a background timer, so a failure must
    // never escape into the SDK's span-processing or worker threads.
    fun onSpanUpdate() {
        runCatching {
            val partId = ensureCurrentPart() ?: return
            writeSpans(partId)
        }
    }

    /**
     * Flushes any span change to disk when the session is about to end. Intended to be registered as
     * a [io.embrace.android.embracesdk.internal.arch.SessionPartEndListener] so spans that changed
     * after the last [onSpanUpdate] are persisted before the part is finalized. No-op when the spans
     * are identical to those already recorded, so an unchanged session end does not rewrite them.
     */
    fun onSessionEnd() {
        runCatching {
            val partId = ensureCurrentPart() ?: return
            writeSpans(partId)
        }
    }

    private fun writeSpans(partId: String) {
        val completed = spanSink.completedSpans()
            .map(EmbraceSpanData::toEmbracePayload)
            .filterNot(Span::isSessionSpan)
        val snapshots = spanRepository.getActiveSpans()
            .mapNotNull { it.snapshot() }
            .filterNot(Span::isSessionSpan)

        val state = SpanState(partId, completed, snapshots)
        if (state == lastSpanState) return
        lastSpanState = state

        store.appendCompletedSpans(partId, completed)
        store.updateSnapshots(partId, snapshots)
    }

    /**
     * Rewrites the metadata for the active session part. Intended to be registered with
     * [io.embrace.android.embracesdk.internal.capture.user.UserService.addUserInfoListener] so a
     * change to the user info is persisted immediately rather than inferred on the cache cadence.
     */
    fun onUserInfoUpdate() {
        runCatching {
            val ids = sessionIdsProvider()
            if (ids.sessionPartId.isEmpty() || !store.isSessionPartActive(ids.sessionPartId)) return
            store.updateMetadata(ids.sessionPartId, SessionMetadata(metadataProvider()))
        }
    }

    /**
     * Recovers the given leftover directories (captured before the current session starts) by
     * reconstituting each and converting still-open snapshots into failed spans.
     */
    fun resurrect(directories: List<SessionPartDirectory>): List<Envelope<SessionPartPayload>> =
        directories.mapNotNull { directory ->
            store.reconstitute(directory)?.failOpenSnapshots()?.also { store.markDelivered(directory) }
        }

    private fun ensureCurrentPart(): String? {
        val ids = sessionIdsProvider()
        if (ids.sessionPartId.isEmpty()) return null
        if (!store.isSessionPartActive(ids.sessionPartId)) {
            store.startSessionPart(
                PersistedSession(clock.now(), uuidProvider(), ids.userSessionId, ids.sessionPartId),
                Manifest(resourceProvider(), VERSION, TYPE, symbolMapProvider()),
                SessionMetadata(metadataProvider()),
            )
        }
        return ids.sessionPartId
    }

    private fun Envelope<SessionPartPayload>.failOpenSnapshots(): Envelope<SessionPartPayload> {
        val completedIds = data.spans?.mapNotNull(Span::spanId)?.toSet() ?: emptySet()
        val endTimeMs = failedSpanEndTimeMs()
        val failedSpans = data.spanSnapshots
            ?.filterNot { completedIds.contains(it.spanId) }
            ?.map { it.toFailedSpan(endTimeMs) }
            ?: emptyList()
        return copy(
            data = data.copy(
                spans = data.spans.orEmpty() + failedSpans,
                spanSnapshots = emptyList(),
            ),
        )
    }

    private fun Envelope<SessionPartPayload>.failedSpanEndTimeMs(): Long {
        val sessionSpan = data.spans?.firstOrNull(Span::isSessionSpan) ?: return 0L
        val endTimeNanos = sessionSpan.endTimeNanos ?: 0L
        val heartbeatNanos = sessionSpan.attributes
            ?.findAttributeValue(EmbSessionAttributes.EMB_HEARTBEAT_TIME_UNIX_NANO)
            ?.toLongOrNull() ?: 0L
        return max(endTimeNanos, heartbeatNanos).nanosToMillis()
    }

    /** Snapshot of the spans last written for a part, used to skip redundant writes. */
    private data class SpanState(
        val partId: String,
        val completed: List<Span>,
        val snapshots: List<Span>,
    )

    private companion object {
        private const val VERSION = "0.1.0"
        private const val TYPE = "spans"
    }
}

private fun Span.isSessionSpan(): Boolean = hasEmbraceAttribute(EmbType.Ux.Session)
