package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.envelope.session.SESSION_ENVELOPE_TYPE
import io.embrace.android.embracesdk.internal.envelope.session.SESSION_ENVELOPE_VERSION
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.persistence.CompletedSpansWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionManifestWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionMetadataWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectoryStore
import io.embrace.android.embracesdk.internal.session.persistence.SessionSpanWriter
import io.embrace.android.embracesdk.internal.session.persistence.SpanSnapshotsWriter
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

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
    clock: Clock,
    private val logger: InternalLogger,
    private val resourceSource: EnvelopeResourceSource,
    private val metadataSource: EnvelopeMetadataSource,
    private val currentSessionPartSpan: CurrentSessionPartSpan,
    private val spanSnapshotSource: () -> List<Span>,
) : SessionPartWriter {

    private companion object {
        const val CRASH_DRAIN_TIMEOUT_MS: Long = 3000
    }

    /**
     * The writers for the session part that telemetry is currently written to. Each targets one
     * fixed directory, so a write that was queued for a session part cannot land in a later one.
     */
    @Volatile
    private var current: PartWriters? = null

    @Volatile
    private var crashing = false

    @Volatile
    private var resourceListenerRegistered = false

    private val directoryStore = SessionPartDirectoryStore(sessionsDir, worker, clock, logger)

    override fun onSessionPartStarted(timestamp: Long, userSessionId: String, sessionPartId: String) {
        if (!enabled()) {
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

        current = writers
        directoryStore.create(writers.directory)
        queueManifestWrite(writers)
        queueMetadataWrite(writers)
        queueSessionSpanWrite(writers)
        queueSpanSnapshotsWrite(writers)
        registerResourceChangeListener()
    }

    override fun onSessionPartEnded(sessionPartId: String) {
        if (!enabled()) {
            return
        }
        val writers = current ?: return
        if (writers.directory.sessionPartId != sessionPartId) {
            return
        }
        queueSessionSpanWrite(writers)
        queueSpanSnapshotsWrite(writers)
    }

    override fun onMetadataChanged() {
        if (!enabled()) {
            return
        }
        queueMetadataWrite(current ?: return)
    }

    override fun onSpanCompleted(spans: List<Span>) {
        if (!enabled() || spans.isEmpty()) {
            return
        }
        queueCompletedSpansWrite(current ?: return, spans)
    }

    private fun onResourceChanged() {
        if (!enabled()) {
            return
        }
        queueManifestWrite(current ?: return)
    }

    override fun onPeriodicWrite() {
        if (!enabled()) {
            return
        }
        queueSessionSpanWrite(current ?: return)
    }

    override fun onCrash() {
        if (!enabled()) {
            return
        }
        crashing = true
        worker.shutdownAndWait(CRASH_DRAIN_TIMEOUT_MS)
    }

    /**
     * Subscribes to resource changes so the manifest on disk keeps up with them. Registration
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
        execute(InternalErrorType.SessionManifestWriteFail, { worker.submit(it) }) {
            resourceSource.addChangeListener { _ -> onResourceChanged() }
        }
    }

    private fun queueManifestWrite(writers: PartWriters) {
        execute(InternalErrorType.SessionManifestWriteFail, writers.manifestWrites::submit) {
            writers.manifest.write(
                directory = writers.directory,
                resource = resourceSource.getEnvelopeResource(),
                envelopeVersion = SESSION_ENVELOPE_VERSION,
                envelopeType = SESSION_ENVELOPE_TYPE,
                sharedLibSymbolMapping = configService.nativeSymbolMap,
            )
        }
    }

    private fun queueMetadataWrite(writers: PartWriters) {
        execute(InternalErrorType.SessionMetadataWriteFail, writers.metadataWrites::submit) {
            writers.metadata.write()
        }
    }

    /**
     * Writes the session span as it stands right now.
     */
    private fun queueSessionSpanWrite(writers: PartWriters) {
        val span = writers.span?.snapshot() ?: return
        execute(InternalErrorType.SessionSpanWriteFail, writers.sessionSpanWrites::submit) {
            writers.sessionSpan.write(span)
        }
    }

    /**
     * Writes in-flight spans to a snapshot file.
     */
    private fun queueSpanSnapshotsWrite(writers: PartWriters) {
        // TODO: future: don't call this every time the listener is invoked as it's expensive to
        // obtain _all_ the spans for every change. Currently this write only happens at session
        // start/end
        val spans = try {
            spanSnapshotSource()
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.SpanSnapshotsWriteFail, exc)
            return
        }
        execute(InternalErrorType.SpanSnapshotsWriteFail, writers.spanSnapshotWrites::submit) {
            writers.spanSnapshots.write(spans)
        }
    }

    private fun queueCompletedSpansWrite(writers: PartWriters, spans: List<Span>) {
        execute(InternalErrorType.CompletedSpansWriteFail, { worker.submit(it) }) {
            writers.completedSpans.write(spans)
        }
    }

    /**
     * Queues [action] with [submit], or runs it on the calling thread if the process is crashing
     * and the [worker] has already been sealed by [onCrash]. Telemetry gathered inside [action] can
     * throw, so a failure is tracked here rather than escaping onto a crashing thread.
     */
    private fun execute(
        errorType: InternalErrorType,
        submit: (Runnable) -> Unit,
        action: () -> Unit,
    ) {
        if (crashing) {
            try {
                action()
            } catch (exc: Throwable) {
                logger.trackInternalError(errorType, exc)
            }
        } else {
            submit(Runnable(action))
        }
    }

    private fun enabled(): Boolean {
        return configService.persistenceBehavior.isMultiFilePersistenceEnabled() && !crashing
    }

    private inner class PartWriters(val directory: SessionPartDirectory) {

        val span: EmbraceSdkSpan? = currentSessionPartSpan.current()
        val manifest = SessionManifestWriter(sessionsDir, logger)

        val metadata = SessionMetadataWriter(
            sessionsDir = sessionsDir,
            sessionPartDirectorySource = { directory },
            metadataSource = metadataSource::getEnvelopeMetadata,
            logger = logger,
        )

        val sessionSpan = SessionSpanWriter(
            sessionsDir = sessionsDir,
            sessionPartDirectorySource = { directory },
            logger = logger,
        )

        val completedSpans = CompletedSpansWriter(
            sessionsDir = sessionsDir,
            sessionPartDirectorySource = { directory },
            logger = logger,
        )

        val spanSnapshots = SpanSnapshotsWriter(
            sessionsDir = sessionsDir,
            sessionPartDirectorySource = { directory },
            logger = logger,
        )

        val manifestWrites = CoalescingWriteQueue(worker)
        val metadataWrites = CoalescingWriteQueue(worker)
        val sessionSpanWrites = CoalescingWriteQueue(worker)
        val spanSnapshotWrites = CoalescingWriteQueue(worker)
    }

    /**
     * Queues the writes for one file in a session part. Each write persists the whole file so
     * we should cancel and remove any enqueued writes as they will be superseded by later ones.
     * A write that has already started is left to finish.
     */
    private class CoalescingWriteQueue(private val worker: BackgroundWorker) {
        private val pending = AtomicReference<Future<*>?>(null)

        fun submit(runnable: Runnable) {
            pending.getAndSet(worker.submit(runnable))?.cancel(false)
        }
    }
}
