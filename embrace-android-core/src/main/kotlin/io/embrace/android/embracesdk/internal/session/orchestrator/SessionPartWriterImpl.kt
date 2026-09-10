package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.envelope.session.SESSION_ENVELOPE_TYPE
import io.embrace.android.embracesdk.internal.envelope.session.SESSION_ENVELOPE_VERSION
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.persistence.CompletedSpansWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionManifestWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionMetadataWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectoryStore
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTarget
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTracker
import io.embrace.android.embracesdk.internal.session.persistence.SessionSpanWriter
import io.embrace.android.embracesdk.internal.session.persistence.SpanSnapshotsWriter
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import java.io.File

/**
 * Writes session part telemetry to disk, if the multi-file persistence layer is enabled.
 * All filesystem work is queued on a single-threaded [worker]. A queued write that is superseded
 * before it runs is cancelled to avoid duplicate work.
 */
class SessionPartWriterImpl(
    private val sessionsDir: Lazy<File>,
    private val worker: BackgroundWorker,
    private val configService: ConfigService,
    private val uuidSource: UuidSource,
    private val clock: Clock,
    private val logger: InternalLogger,
    private val resourceSource: EnvelopeResourceSource,
    private val metadataSource: EnvelopeMetadataSource,
    private val currentSessionPartSpan: CurrentSessionPartSpan,
    private val inFlightSpanSource: () -> List<EmbraceSdkSpan>,
    private val telemetryService: TelemetryService,
    private val directoryStore: SessionPartDirectoryStore =
        SessionPartDirectoryStore(sessionsDir, worker, clock, logger),
    private val writeTracker: SessionPartWriteTracker = SessionPartWriteTracker(),
    private val onWritesComplete: () -> Unit = {},
) : SessionPartWriter {

    internal companion object {
        private const val CRASH_DRAIN_TIMEOUT_MS: Long = 3000
        private const val MAX_CARRIED_OVER_SPANS: Int = 1000
        private const val CARRIED_OVER_SPAN_LIMIT_TYPE: String = "carried_over_span"

        const val METADATA_WRITE_DELAY_MS: Long = 500
        const val SESSION_SPAN_WRITE_DELAY_MS: Long = 1000
        const val SPAN_SNAPSHOT_WRITE_DELAY_MS: Long = 1000
    }

    /**
     * The writers for the session part that telemetry is currently written to. Each targets one
     * fixed directory, so a write that was queued for a session part cannot land in a later one.
     */
    @Volatile
    private var current: PartWriters? = null

    @Volatile
    private var processTerminating = false

    @Volatile
    private var resourceListenerRegistered = false

    private val bufferLock = Any()
    private val carriedOverSpans = ArrayDeque<Span>()

    override fun onSessionPartStarted(timestamp: Long, userSessionId: String, sessionPartId: String) {
        if (!acceptingWrites()) {
            return
        }
        val writers = PartWriters(
            SessionPartDirectory(
                timestamp = timestamp,
                uuid = uuidSource.createUuid(),
                userSessionId = userSessionId,
                sessionPartId = sessionPartId,
            ),
        )

        writeTracker.markWriting(sessionPartId)

        // the session span is snapshotted after it has stopped, so it must hold on to its events and
        // links until this part's writes have completed
        writers.span?.retainDataAfterStop()
        directoryStore.create(writers.directory)

        synchronized(bufferLock) {
            if (carriedOverSpans.isNotEmpty()) {
                queueCompletedSpansWrite(writers, carriedOverSpans.toList())
                carriedOverSpans.clear()
            }
            current = writers
        }

        queueManifestWrite(writers)
        queueMetadataWrite(writers)
        queueSessionSpanWrite(writers)
        queueSpanSnapshotsWrite(writers)
        registerResourceChangeListener()
    }

    override fun onSessionPartEnded(sessionPartId: String, crashing: Boolean) {
        if (!acceptingWrites()) {
            return
        }

        val writers = synchronized(bufferLock) {
            val ref = current ?: return
            if (ref.directory.sessionPartId != sessionPartId) {
                return
            }
            current = null
            ref
        }
        queueSessionSpanWrite(writers)
        queueSpanSnapshotsWrite(writers)
        writers.flushPendingWrites()

        worker.submit {
            writers.seal()
            writeTracker.markComplete(sessionPartId)

            if (!crashing && !processTerminating) {
                notifyWritesComplete()
            }
            writers.span?.releaseRetainedData()
        }
    }

    override fun onMetadataChanged() {
        if (!acceptingWrites()) {
            return
        }
        queueMetadataWrite(current ?: return)
    }

    override fun onSpanCompleted(spans: List<Span>) {
        if (!acceptingWrites() || spans.isEmpty()) {
            return
        }
        val writers = current ?: synchronized(bufferLock) {
            current ?: return carryOver(spans)
        }
        queueCompletedSpansWrite(writers, spans)
    }

    override fun onSpanSnapshotChanged() = EmbTrace.trace("mf-span-snapshot-changed") {
        if (!acceptingWrites()) {
            return@trace
        }
        queueSpanSnapshotsRefresh(current ?: return@trace)
    }

    private fun onResourceChanged() {
        if (!acceptingWrites()) {
            return
        }
        queueMetadataWrite(current ?: return)
    }

    override fun onSessionSpanChanged() {
        if (!acceptingWrites()) {
            return
        }
        queueSessionSpanWrite(current ?: return, onlyIfCurrent = true)
    }

    override fun onCrash() {
        if (!persistenceEnabled() || processTerminating) {
            return
        }
        processTerminating = true

        // flush then wait for pending writes
        current?.flushPendingWrites()
        worker.shutdownAndWait(CRASH_DRAIN_TIMEOUT_MS)
    }

    /**
     * Subscribes to resource changes so the metadata on disk keeps up with them. Registration
     * happens at most once as the listener outlives any one session part, and is deferred to the
     * [worker] because building a resource can touch the filesystem.
     *
     * The resource handed to the listener is discarded: each write rebuilds it, so the newest
     * resource wins even if two changes are delivered out of order.
     */
    private fun registerResourceChangeListener() {
        if (resourceListenerRegistered) {
            return
        }
        resourceListenerRegistered = true
        execute(null, InternalErrorType.SessionMetadataWriteFail, { worker.submit(it) }) {
            resourceSource.addChangeListener { _ -> onResourceChanged() }
        }
    }

    private fun queueManifestWrite(writers: PartWriters) = EmbTrace.trace("mf-queue-manifest") {
        execute(writers, InternalErrorType.SessionManifestWriteFail, { worker.submit(it) }) {
            writers.manifest.write(
                resource = resourceSource.getEnvelopeResource(),
                envelopeVersion = SESSION_ENVELOPE_VERSION,
                envelopeType = SESSION_ENVELOPE_TYPE,
                sharedLibSymbolMapping = configService.nativeSymbolMap,
            )
        }
    }

    private fun queueMetadataWrite(writers: PartWriters) = EmbTrace.trace("mf-queue-metadata") {
        execute(writers, InternalErrorType.SessionMetadataWriteFail, writers.metadataWrites::submit) {
            writers.metadata.write()
        }
    }

    /**
     * Writes the session span as it stands right now.
     */
    private fun queueSessionSpanWrite(writers: PartWriters, onlyIfCurrent: Boolean = false) =
        EmbTrace.trace("mf-queue-session-span") {
            val span = writers.span ?: return@trace
            execute(writers, InternalErrorType.SessionSpanWriteFail, writers.sessionSpanWrites::submit) {
                if (onlyIfCurrent && current !== writers) {
                    return@execute
                }
                val snapshot = span.snapshot()
                if (snapshot != null) {
                    writers.sessionSpan.write(snapshot.withHeartbeat())
                }
            }
        }

    /**
     * Stamps emb.heartbeat_unix_time_nanos on the span.
     */
    private fun Span.withHeartbeat(): Span {
        val heartbeat = Attribute(
            EmbSessionAttributes.EMB_HEARTBEAT_TIME_UNIX_NANO,
            (endTimeNanos ?: clock.now().millisToNanos()).toString(),
        )
        val existing = attributes.orEmpty().filterNot { it.key == heartbeat.key }
        return copy(attributes = existing + heartbeat)
    }

    /**
     * Writes in-flight spans to a snapshot file.
     */
    private fun queueSpanSnapshotsWrite(writers: PartWriters) = EmbTrace.trace("mf-queue-span-snapshots") {
        val spans = try {
            inFlightSpanSource()
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.SpanSnapshotsWriteFail, exc)
            return@trace
        }
        execute(writers, InternalErrorType.SpanSnapshotsWriteFail, writers.spanSnapshotWrites::submit) {
            writers.spanSnapshots.write(spans.mapNotNull(EmbraceSdkSpan::snapshot))
        }
    }

    private fun queueSpanSnapshotsRefresh(writers: PartWriters) = EmbTrace.trace("mf-queue-span-snapshots-refresh") {
        execute(writers, InternalErrorType.SpanSnapshotsWriteFail, writers.spanSnapshotWrites::submit) {
            if (current === writers) {
                writers.spanSnapshots.write(inFlightSpanSource().mapNotNull(EmbraceSdkSpan::snapshot))
            }
        }
    }

    private fun queueCompletedSpansWrite(writers: PartWriters, spans: List<Span>) =
        EmbTrace.trace("mf-queue-completed-spans") {
            execute(null, InternalErrorType.CompletedSpansWriteFail, { worker.submit(it) }) {
                if (writers.sealed) {
                    // the part sealed while this write was queued. Hold the spans for the next one
                    synchronized(bufferLock) { carryOver(spans) }
                } else {
                    writers.completedSpans.write(spans)
                }
            }
        }

    /**
     * Holds [spans] until the next session part starts. Spans beyond [MAX_CARRIED_OVER_SPANS] are
     * dropped.
     */
    private fun carryOver(spans: List<Span>) {
        spans.forEach { span ->
            if (carriedOverSpans.size >= MAX_CARRIED_OVER_SPANS) {
                telemetryService.trackAppliedLimit(CARRIED_OVER_SPAN_LIMIT_TYPE, AppliedLimitType.DROP)
            } else {
                carriedOverSpans.add(span)
            }
        }
    }

    /**
     * Runs [action] on the [worker] via [submit], tracking any failure as an internal error rather
     * than letting it escape - the telemetry gathered inside [action] can throw.
     */
    private fun execute(
        writers: PartWriters?,
        errorType: InternalErrorType,
        submit: (Runnable) -> Unit,
        action: () -> Unit,
    ) {
        val task = Runnable {
            if (writers?.abandoned == true) {
                return@Runnable
            }
            try {
                action()
            } catch (exc: Throwable) {
                logger.trackInternalError(errorType, exc)
            }
        }
        if (!processTerminating) {
            submit(task)
        }
    }

    /**
     * Signals that a session part is fully written.
     */
    private fun notifyWritesComplete() {
        try {
            onWritesComplete()
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.SessionPartWritesCompleteFail, exc)
        }
    }

    private fun persistenceEnabled(): Boolean =
        configService.persistenceBehavior.isMultiFilePersistenceEnabled()

    /**
     * Whether new telemetry should still be recorded. [onCrash] flushes what already exists and
     * then seals the writer: the [worker] is shut down at that point and the process is about to
     * die, so anything that happens afterwards is dropped rather than queued onto a dead worker.
     */
    private fun acceptingWrites(): Boolean = persistenceEnabled() && !processTerminating

    private inner class PartWriters(val directory: SessionPartDirectory) {

        @Volatile
        var sealed: Boolean = false

        private val target = SessionPartWriteTarget(sessionsDir) { directory }

        /**
         * Whether nothing more should be written for this part: it has either been fully written,
         * or the directory is not on disk.
         */
        val abandoned: Boolean
            get() = sealed || target.failed

        val span: EmbraceSdkSpan? = currentSessionPartSpan.current()
        val manifest = SessionManifestWriter(target, logger)

        val metadata = SessionMetadataWriter(
            target = target,
            metadataSource = metadataSource::getEnvelopeMetadata,
            resourceSource = resourceSource::getEnvelopeResource,
            logger = logger,
        )

        val sessionSpan = SessionSpanWriter(target, logger)
        val completedSpans = CompletedSpansWriter(target, logger)
        val spanSnapshots = SpanSnapshotsWriter(target, logger)

        val metadataWrites = CoalescingWriteQueue(worker, clock, METADATA_WRITE_DELAY_MS)
        val sessionSpanWrites = CoalescingWriteQueue(worker, clock, SESSION_SPAN_WRITE_DELAY_MS)
        val spanSnapshotWrites = CoalescingWriteQueue(worker, clock, SPAN_SNAPSHOT_WRITE_DELAY_MS)

        private val writeQueues = listOf(metadataWrites, sessionSpanWrites, spanSnapshotWrites)

        fun flushPendingWrites() = writeQueues.forEach(CoalescingWriteQueue::flush)

        /**
         * Marks this part as fully written and releases the file [completedSpans] holds open.
         * This must run on the [worker] so that it cannot overlap a write still queued for the part.
         */
        fun seal() {
            sealed = true
            completedSpans.close()
        }
    }
}
