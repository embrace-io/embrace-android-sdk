package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.capture.state.StateRecorder
import io.embrace.android.embracesdk.internal.capture.state.withStateSpan
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartWriterImpl
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionReconstructionService
import io.embrace.android.embracesdk.internal.utils.UuidSourceImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The session parts of a [harness] written as multiple files under [storageDir] by a [SessionPartWriterImpl] on a worker of
 * its own, as the SDK persists them, and read back as the envelopes that would be delivered. It does not deliver a part, and
 * unless [keepParts] is set it deletes each part once it has been written, as delivery would.
 *
 * What the fixtures built on it record into the part is theirs: they call [changeSessionPart] when the app changes between
 * foreground and background, and hook in what else that change does.
 */
internal class SessionPartFiles(
    private val harness: TelemetryDestinationHarness,
    configService: ConfigService,
    private val storageDir: File,
    private val clock: Clock,
    private val keepParts: Boolean,
    stateRecorder: StateRecorder?,
) : AutoCloseable {

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val writer = SessionPartWriterImpl(
        sessionsDir = lazy { storageDir },
        worker = BackgroundWorker(executor),
        configService = configService,
        uuidSource = UuidSourceImpl(),
        clock = clock,
        errorHandler = harness.errorHandler,
        resourceSource = FixedResourceSource,
        metadataSource = EnvelopeMetadataSource { EnvelopeMetadata() },
        currentSessionPartSpan = harness.currentSessionPartSpan,
        inFlightSpanSource = harness.spanRepository::getActiveEmbraceSpans,
        telemetryService = harness.telemetryService,
        onWritesComplete = ::deleteWrittenParts,
        stateRecorder = stateRecorder,
    )
    private val reconstruction = SessionReconstructionService(lazy { storageDir }, harness.errorHandler)

    /**
     * What the worker waits on while writes are held, or null while they are not.
     */
    private var writesHeld: CountDownLatch? = null

    private var partNumber = 0

    /**
     * The id of the session part in progress.
     */
    @Volatile
    var currentPartId: String = nextPartId()
        private set

    init {
        storageDir.mkdirs()
        harness.spanRepository.addCompletedOtelSpansListener(writer::onSpanCompleted)
        harness.spanRepository.addSpanChangeListener(writer::onSpanSnapshotChanged)
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, currentPartId)
    }

    /**
     * Ends the current session part and starts the next, as the SDK does when the app changes between foreground and
     * background, and returns the id of the part that ended. [beforeEnd] is what runs before the part's span ends, and
     * [afterChange] what runs once the next part's id is current but before it is written as started.
     */
    fun changeSessionPart(beforeEnd: () -> Unit = {}, afterChange: () -> Unit = {}): String {
        val ended = currentPartId
        beforeEnd()
        harness.currentSessionPartSpan.endSession(startNewSession = true)
        writer.onSessionPartEnded(ended)
        currentPartId = nextPartId()
        afterChange()
        writer.onSessionPartStarted(clock.now(), USER_SESSION_ID, currentPartId)
        return ended
    }

    /**
     * Waits for everything queued for the worker so far to be written.
     */
    fun awaitWrites() {
        executor.submit { }.get(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /**
     * Waits for everything queued for the worker so far to be written, then keeps the worker from running anything more
     * until [releaseWrites]. What is done in between is then the work of the calling thread alone: nothing the SDK does on
     * a thread that reports a change or changes the session part waits for the worker.
     */
    fun holdWrites() {
        check(writesHeld == null) { "writes are already held" }
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        executor.execute {
            reached.countDown()
            release.await()
        }
        check(reached.await(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "the worker did not reach the hold" }
        writesHeld = release
    }

    /**
     * Lets the worker run again after [holdWrites], and waits for it to write everything queued meanwhile.
     */
    fun releaseWrites() {
        checkNotNull(writesHeld) { "writes are not held" }.countDown()
        writesHeld = null
        awaitWrites()
    }

    /**
     * Where the session part with [sessionPartId] is on disk, once everything queued for the worker has been written.
     */
    fun partDirectory(sessionPartId: String): SessionPartDirectory {
        awaitWrites()
        return partDirectories().single { it.sessionPartId == sessionPartId }
    }

    /**
     * Reads the session part in [directory] back from disk as the envelope that would be delivered for it, which for
     * journaled states is where their span is made. The part must have ended, and [keepParts] must be set.
     */
    fun readPart(directory: SessionPartDirectory): Envelope<SessionPartPayload> {
        val part = checkNotNull(reconstruction.reconstruct(directory)) {
            "session part ${directory.sessionPartId} could not be read"
        }
        return part.envelope.withStateSpan(part.stateJournal)
    }

    /**
     * The directory holding the files of the session part with [sessionPartId].
     */
    fun partDir(sessionPartId: String): File = File(storageDir, partDirectory(sessionPartId).dirName)

    override fun close() {
        writesHeld?.countDown()
        executor.shutdownNow()
    }

    private fun partDirectories(): List<SessionPartDirectory> =
        storageDir.list().orEmpty().mapNotNull(SessionPartDirectory::fromDirName)

    /**
     * Runs on the worker once a part has been fully written. Every part but the one in progress is done with.
     */
    private fun deleteWrittenParts() {
        if (keepParts) {
            return
        }
        val current = currentPartId
        partDirectories().filter { it.sessionPartId != current }.forEach { File(storageDir, it.dirName).deleteRecursively() }
    }

    private fun nextPartId(): String = "%032x".format(++partNumber)

    private companion object {
        const val USER_SESSION_ID = "4f3c9a7d0b8e5f2a4c116c9b1f2ec1d3"
        const val WRITE_TIMEOUT_SECONDS = 60L
    }
}

private object FixedResourceSource : EnvelopeResourceSource {
    private val resource = EnvelopeResource(appVersion = "3.14.2", sdkVersion = "8.0.0", osName = "android")

    override fun getEnvelopeResource(): EnvelopeResource = resource
    override fun add(key: String, value: String) = Unit
    override fun addChangeListener(listener: (EnvelopeResource) -> Unit) = Unit
}
