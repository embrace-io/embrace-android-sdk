package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.envelope.session.SESSION_ENVELOPE_TYPE
import io.embrace.android.embracesdk.internal.envelope.session.SESSION_ENVELOPE_VERSION
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.orchestrator.TelemetryWriteScheduler.WriteStrategy
import io.embrace.android.embracesdk.internal.session.persistence.CompletedSpansWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionMetadataWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectoryStore
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTarget
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTracker
import io.embrace.android.embracesdk.internal.session.persistence.SpanSnapshotsWriter
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.utils.EmbTrace
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

        const val METADATA_WRITE_DELAY_MS: Long = 2000
        const val SPAN_SNAPSHOT_WRITE_DELAY_MS: Long = 2000
        const val COMPLETED_SPAN_WRITE_DELAY_MS: Long = 2000
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
        EmbTrace.trace("mf-part-started") {
            val writers = PartWriters(
                SessionPartDirectory(
                    timestamp = timestamp,
                    uuid = uuidSource.createUuid(),
                    userSessionId = userSessionId,
                    sessionPartId = sessionPartId,
                ),
            )

            writeTracker.markWriting(sessionPartId)
            directoryStore.create(writers.directory)

            synchronized(bufferLock) {
                // a part should always end before the next one starts. the orphan is dropped before
                // it is finished, so a failure to finish it cannot leave it as the current part
                current?.let { orphan ->
                    current = null
                    reportMissedEnd("Session part started before the previous one ended")
                    finish(orphan, crashing = false)
                }
                if (carriedOverSpans.isNotEmpty()) {
                    writers.completedSpanWrites.write(WriteStrategy.IMMEDIATE, carriedOverSpans.toList())
                    carriedOverSpans.clear()
                }
                current = writers
            }

            queueMetadataWrite(writers, WriteStrategy.IMMEDIATE)
            queueSpanSnapshotsSeed(writers)
            registerResourceChangeListener()
        }
    }

    override fun onSessionPartEnded(sessionPartId: String, crashing: Boolean) {
        if (!acceptingWrites()) {
            return
        }
        EmbTrace.trace("mf-part-ended") {
            val writers = synchronized(bufferLock) {
                val ref = current ?: return
                if (ref.directory.sessionPartId != sessionPartId) {
                    reportMissedEnd("Session part ended after another had started")
                    return
                }
                current = null
                ref
            }
            finish(writers, crashing)
        }
    }

    private fun finish(writers: PartWriters, crashing: Boolean) {
        writers.flushPendingWrites()

        if (processTerminating) {
            return
        }
        worker.submit {
            writers.seal()
            writeTracker.markComplete(writers.directory.sessionPartId)

            if (!crashing && !processTerminating) {
                notifyWritesComplete()
            }
        }
    }

    private fun reportMissedEnd(msg: String) =
        logger.trackInternalError(InternalErrorType.SessionPartEndMissed, IllegalStateException(msg))

    override fun onMetadataChanged() {
        if (!acceptingWrites()) {
            return
        }
        queueMetadataWrite(current ?: return, WriteStrategy.DEBOUNCED)
    }

    /**
     * Ownership means accepted rather than written: the batch is queued onto the [worker] or held
     * for the next part. An empty batch counts as owned.
     */
    override fun onSpanCompleted(spans: List<Span>): Boolean {
        if (!acceptingWrites()) {
            return false
        }
        if (spans.isEmpty()) {
            return true
        }
        val writers = current ?: synchronized(bufferLock) {
            current ?: run {
                carryOver(spans)
                return true
            }
        }
        writers.completedSpanWrites.write(WriteStrategy.DEBOUNCED, spans)
        return true
    }

    override fun onSpanSnapshotChanged(span: EmbraceSdkSpan) {
        if (!acceptingWrites()) {
            return
        }
        EmbTrace.trace("mf-span-snapshot-changed") {
            val writers = current ?: return@trace
            when {
                span.isRecording -> writers.spanSnapshotWrites.write(WriteStrategy.DEBOUNCED, span)
                else -> writers.spanSnapshotWrites.remove(span)
            }
        }
    }

    private fun onResourceChanged() {
        if (!acceptingWrites()) {
            return
        }
        queueMetadataWrite(current ?: return, WriteStrategy.DEBOUNCED)
    }

    override fun onCrash() {
        if (!persistenceEnabled() || processTerminating) {
            return
        }
        EmbTrace.trace("mf-flush-writes") {
            processTerminating = true

            // flush then wait for pending writes
            current?.flushPendingWrites()
            worker.shutdownAndWait(CRASH_DRAIN_TIMEOUT_MS)
        }
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

    private fun queueMetadataWrite(writers: PartWriters, timing: WriteStrategy) =
        EmbTrace.trace("mf-queue-metadata") {
            if (!processTerminating) {
                writers.metadataWrites.write(timing, MetadataChange)
            }
        }

    /**
     * Seeds the snapshot file with everything in flight when a session part starts. This is the one
     * write that knows it holds the complete set, so it goes straight to the worker rather than
     * through [PartWriters.spanSnapshotWrites], which can only append what it drains.
     */
    private fun queueSpanSnapshotsSeed(writers: PartWriters) = EmbTrace.trace("mf-queue-span-snapshots") {
        val spans = try {
            inFlightSpanSource()
        } catch (exc: Throwable) {
            logger.trackInternalError(InternalErrorType.SpanSnapshotsWriteFail, exc)
            return@trace
        }
        execute(writers, InternalErrorType.SpanSnapshotsWriteFail, { worker.submit(it) }) {
            writers.spanSnapshots.write(writers.snapshotsOf(spans))
        }
    }

    /**
     * Holds [spans] until the next session part starts. Spans beyond [MAX_CARRIED_OVER_SPANS] are
     * dropped, as is a session span: it belongs to the part that was current when it stopped, and a
     * part holding two session spans cannot be read back.
     */
    internal fun carryOver(spans: List<Span>) {
        spans.forEach { span ->
            if (span.hasEmbraceAttribute(EmbType.Ux.Session)) {
                reportOrphanedSessionSpan()
            } else if (carriedOverSpans.size >= MAX_CARRIED_OVER_SPANS) {
                telemetryService.trackAppliedLimit(CARRIED_OVER_SPAN_LIMIT_TYPE, AppliedLimitType.DROP)
            } else {
                carriedOverSpans.add(span)
            }
        }
    }

    private fun reportOrphanedSessionSpan() = logger.trackInternalError(
        InternalErrorType.OrphanedSessionSpan,
        IllegalStateException("Session span completed with no session part to write it to"),
    )

    /**
     * Runs [action] on the [worker] via [submit], guarded by [guard].
     */
    private fun execute(
        writers: PartWriters?,
        errorType: InternalErrorType,
        submit: (Runnable) -> Unit,
        action: () -> Unit,
    ) {
        val task = guard(errorType) { writers?.abandoned == true }(Runnable(action))
        if (!processTerminating) {
            submit(task)
        }
    }

    /**
     * Wraps a write so that it is dropped once [abandoned] reports the session part is done with,
     * and so that a failure is tracked as an internal error rather than escaping onto the [worker].
     */
    internal fun guard(
        errorType: InternalErrorType,
        abandoned: () -> Boolean = { false },
    ): (Runnable) -> Runnable = { task ->
        Runnable {
            if (!abandoned()) {
                try {
                    task.run()
                } catch (exc: Throwable) {
                    logger.trackInternalError(errorType, exc)
                }
            }
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

        val sessionSpanId: String? = currentSessionPartSpan.current()?.spanId

        val metadata = SessionMetadataWriter(
            target = target,
            metadataSource = metadataSource::getEnvelopeMetadata,
            resourceSource = resourceSource::getEnvelopeResource,
            envelopeVersion = SESSION_ENVELOPE_VERSION,
            envelopeType = SESSION_ENVELOPE_TYPE,
            sharedLibSymbolMappingSource = { configService.nativeSymbolMap },
            logger = logger,
        )

        val completedSpans = CompletedSpansWriter(target, logger)
        val spanSnapshots = SpanSnapshotsWriter(target, logger)

        val metadataWrites = TelemetryWriteScheduler(
            worker = worker,
            delayMs = METADATA_WRITE_DELAY_MS,
            queue = metadataChangeQueue(),
            guard = guard(InternalErrorType.SessionMetadataWriteFail) { abandoned },
            onWrite = { metadata.write() },
        )

        val completedSpanWrites = TelemetryWriteScheduler(
            worker = worker,
            delayMs = COMPLETED_SPAN_WRITE_DELAY_MS,
            queue = completedSpansQueue(),
            guard = guard(InternalErrorType.CompletedSpansWriteFail),
            onWrite = ::writeCompletedSpans,
        )

        val spanSnapshotWrites = TelemetryWriteScheduler(
            worker = worker,
            delayMs = SPAN_SNAPSHOT_WRITE_DELAY_MS,
            queue = spanSnapshotsQueue(),
            guard = guard(InternalErrorType.SpanSnapshotsWriteFail) { abandoned },
            onWrite = ::writeSpanSnapshots,
        )

        fun flushPendingWrites() {
            metadataWrites.flush()
            completedSpanWrites.flush()
            spanSnapshotWrites.flush()
        }

        fun snapshotsOf(spans: List<EmbraceSdkSpan>): List<Span> =
            spans.filter { it.spanId == sessionSpanId || !it.isSessionSpan() }
                .mapNotNull(EmbraceSdkSpan::snapshot)

        private fun EmbraceSdkSpan.isSessionSpan(): Boolean = hasEmbraceAttribute(EmbType.Ux.Session)

        private fun writeSpanSnapshots(spans: List<EmbraceSdkSpan>) {
            val snapshots = snapshotsOf(spans)
            if (snapshots.isNotEmpty()) {
                spanSnapshots.append(snapshots) { snapshotsOf(inFlightSpanSource()) }
            }
        }

        private fun writeCompletedSpans(spans: List<Span>) {
            when {
                spans.isEmpty() -> Unit
                // the part sealed while this write was queued. Hold the spans for the next one
                sealed -> synchronized(bufferLock) { carryOver(spans) }
                else -> completedSpans.write(spans)
            }
        }

        /**
         * Marks this part as fully written and releases the files held open for it.
         * This must run on the [worker] so that it cannot overlap a write still queued for the part.
         */
        fun seal() {
            sealed = true
            completedSpans.close()
            spanSnapshots.close()
        }
    }
}
