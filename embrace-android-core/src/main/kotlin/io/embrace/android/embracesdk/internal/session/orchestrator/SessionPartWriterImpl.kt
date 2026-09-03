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
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTracker
import io.embrace.android.embracesdk.internal.session.persistence.SessionSpanWriter
import io.embrace.android.embracesdk.internal.session.persistence.SpanSnapshotsWriter
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
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
    clock: Clock,
    private val logger: InternalLogger,
    private val resourceSource: EnvelopeResourceSource,
    private val metadataSource: EnvelopeMetadataSource,
    private val currentSessionPartSpan: CurrentSessionPartSpan,
    private val spanSnapshotSource: () -> List<Span>,
    private val directoryStore: SessionPartDirectoryStore =
        SessionPartDirectoryStore(sessionsDir, worker, clock, logger),
    private val writeTracker: SessionPartWriteTracker = SessionPartWriteTracker(),
    private val onWritesComplete: () -> Unit = {},
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
    private var writesSealed = false

    @Volatile
    private var resourceListenerRegistered = false

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

        current = writers
        writeTracker.markWriting(sessionPartId)

        // the session span is snapshotted after it has stopped, so it must hold on to its events and
        // links until this part's writes have completed
        writers.span?.retainDataAfterStop()

        directoryStore.create(writers.directory)
        queueManifestWrite(writers)
        queueMetadataWrite(writers)
        queueSessionSpanWrite(writers)
        queueSpanSnapshotsWrite(writers)
        registerResourceChangeListener()
    }

    override fun onSessionPartEnded(sessionPartId: String) {
        if (!acceptingWrites()) {
            return
        }
        val writers = current ?: return
        if (writers.directory.sessionPartId != sessionPartId) {
            return
        }
        queueSessionSpanWrite(writers)
        queueSpanSnapshotsWrite(writers)
        worker.submit {
            writeTracker.markComplete(sessionPartId)

            if (!writesSealed) {
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
        queueCompletedSpansWrite(current ?: return, spans)
    }

    private fun onResourceChanged() {
        if (!acceptingWrites()) {
            return
        }
        queueMetadataWrite(current ?: return)
    }

    override fun onPeriodicWrite() {
        if (!acceptingWrites()) {
            return
        }
        queueSessionSpanWrite(current ?: return)
    }

    override fun onCrash() {
        if (!persistenceEnabled() || writesSealed) {
            return
        }
        writesSealed = true
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
        execute(InternalErrorType.SessionMetadataWriteFail, { worker.submit(it) }) {
            resourceSource.addChangeListener { _ -> onResourceChanged() }
        }
    }

    private fun queueManifestWrite(writers: PartWriters) {
        execute(InternalErrorType.SessionManifestWriteFail, { worker.submit(it) }) {
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
        val span = writers.span ?: return
        execute(InternalErrorType.SessionSpanWriteFail, writers.sessionSpanWrites::submit) {
            val snapshot = span.snapshot()
            if (snapshot != null) {
                writers.sessionSpan.write(snapshot)
            }
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
     * Runs [action] on the [worker] via [submit], tracking any failure as an internal error rather
     * than letting it escape - the telemetry gathered inside [action] can throw.
     */
    private fun execute(
        errorType: InternalErrorType,
        submit: (Runnable) -> Unit,
        action: () -> Unit,
    ) {
        val task = Runnable {
            try {
                action()
            } catch (exc: Throwable) {
                logger.trackInternalError(errorType, exc)
            }
        }
        if (writesSealed) {
            task.run()
        } else {
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
    private fun acceptingWrites(): Boolean = persistenceEnabled() && !writesSealed

    private inner class PartWriters(val directory: SessionPartDirectory) {

        val span: EmbraceSdkSpan? = currentSessionPartSpan.current()
        val manifest = SessionManifestWriter(sessionsDir, logger)

        val metadata = SessionMetadataWriter(
            sessionsDir = sessionsDir,
            sessionPartDirectorySource = { directory },
            metadataSource = metadataSource::getEnvelopeMetadata,
            resourceSource = resourceSource::getEnvelopeResource,
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

        val metadataWrites = CoalescingWriteQueue(worker)
        val sessionSpanWrites = CoalescingWriteQueue(worker)
        val spanSnapshotWrites = CoalescingWriteQueue(worker)
    }
}
