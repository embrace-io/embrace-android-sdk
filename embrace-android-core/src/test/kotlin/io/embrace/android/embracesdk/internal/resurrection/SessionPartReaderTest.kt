package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeCachedLogEnvelopeStore
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeIntakeService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeNativeCrashService
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeResult
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.envelope.session.SESSION_ENVELOPE_TYPE
import io.embrace.android.embracesdk.internal.envelope.session.SESSION_ENVELOPE_VERSION
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.UserSessionRestoreDecision
import io.embrace.android.embracesdk.internal.session.getSessionPartSpan
import io.embrace.android.embracesdk.internal.session.persistence.CompletedSpansWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionMetadataWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectoryStore
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTarget
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTracker
import io.embrace.android.embracesdk.internal.session.persistence.SessionReconstructionService
import io.embrace.android.embracesdk.internal.session.persistence.SpanSnapshotsWriter
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class SessionPartReaderTest {

    private companion object {
        private const val PROCESS_ID = "cccccccccccccccccccccccccccccccc"
        private const val PERSISTED_PROCESS_ID = "dddddddddddddddddddddddddddddddd"
        private const val METADATA_FILE_NAME = "metadata.pb"
        private const val TERMINATION_REASON = "inactivity"

        private val partDirectory = SessionPartDirectory(
            timestamp = FakeClock.DEFAULT_FAKE_CURRENT_TIME,
            uuid = "c2610cd1-389f-422a-bfbc-25312c7a599a",
            userSessionId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            sessionPartId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        )

        private val laterPartDirectory = SessionPartDirectory(
            timestamp = FakeClock.DEFAULT_FAKE_CURRENT_TIME + 1000,
            uuid = "d2610cd1-389f-422a-bfbc-25312c7a599a",
            userSessionId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            sessionPartId = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
        )

        private fun sessionSpan(
            processIdentifier: String? = PERSISTED_PROCESS_ID,
            sessionPartId: String? = null,
        ) = Span(
            traceId = "6c9b1f2ec1d34f3c9a7d0b8e5f2a4c11",
            spanId = "aaaaaaaaaaaaaaa1",
            name = "emb-session",
            startTimeNanos = 1726739283136000000L,
            endTimeNanos = 1726739284136000000L,
            status = Span.Status.UNSET,
            events = emptyList(),
            attributes = listOfNotNull(
                Attribute(key = "emb.type", data = "ux.session"),
                processIdentifier?.let { Attribute(key = EmbSessionAttributes.EMB_PROCESS_IDENTIFIER, data = it) },
                sessionPartId?.let { Attribute(key = EmbSessionAttributes.EMB_SESSION_PART_ID, data = it) },
            ),
            links = emptyList(),
        )

        private fun ids(vararg directories: SessionPartDirectory): List<String> =
            directories.map(SessionPartDirectory::sessionPartId)
    }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var sessionsDir: File
    private lateinit var clock: FakeClock
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var logger: FakeInternalLogger
    private lateinit var directoryStore: SessionPartDirectoryStore
    private lateinit var intakeService: FakeIntakeService
    private lateinit var slowIntake: SlowIntakeService
    private lateinit var writeTracker: SessionPartWriteTracker
    private lateinit var nativeCrashService: FakeNativeCrashService
    private var restoreDecision: UserSessionRestoreDecision? = null

    @Before
    fun setUp() {
        sessionsDir = tempFolder.newFolder("embrace_sessions_split")
        clock = FakeClock()
        executor = BlockingScheduledExecutorService(clock, true)
        logger = FakeInternalLogger(throwOnInternalError = false)
        directoryStore = SessionPartDirectoryStore(lazy { sessionsDir }, BackgroundWorker(executor), clock, logger)
        intakeService = FakeIntakeService()
        slowIntake = SlowIntakeService()
        writeTracker = SessionPartWriteTracker()
        nativeCrashService = FakeNativeCrashService()
        restoreDecision = null
    }

    @Test
    fun `a persisted session part is delivered to the intake service and deleted`() {
        persist(partDirectory)
        createReader().readPersistedSessionParts()

        val intake = intakeService.getIntakes<SessionPartPayload>().single()
        assertEquals(sessionSpan(), intake.envelope.getSessionPartSpan())
        assertEquals(SESSION_ENVELOPE_VERSION, intake.envelope.version)
        assertEquals(SESSION_ENVELOPE_TYPE, intake.envelope.type)

        with(intake.metadata) {
            assertEquals(partDirectory.timestamp, timestamp)
            assertEquals(partDirectory.uuid, uuid)
            assertEquals(partDirectory.userSessionId, userSessionId)
            assertEquals(partDirectory.sessionPartId, sessionPartId)
            assertEquals(SupportedEnvelopeType.SESSION, envelopeType)
            assertEquals(PayloadType.SESSION, payloadType)
            assertTrue(complete)
        }
        assertDeleted(partDirectory)
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    @Test
    fun `a dead session part is resurrected and taken in complete`() {
        terminateUserSession()
        persist(partDirectory)
        createReader().readPersistedSessionParts(performingResurrection = true)

        val intake = intakeService.intakeList.single()
        assertTrue(intake.metadata.complete)
        assertEquals(emptyList<Any>(), intakeService.cacheList)
        assertEquals("1", intake.envelope.sessionPartAttribute(EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART))
        assertEquals(TERMINATION_REASON, intake.envelope.sessionPartAttribute(EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON))
        assertDeleted(partDirectory)
    }

    @Test
    fun `a dead session part that a later pass reaches is still resurrected`() {
        terminateUserSession()
        persistTwoParts()
        val reader = createReader(slowIntake)

        // the launch pass stops on the first part, so it doesn't reach the last part of the user session
        reader.readPersistedSessionParts(performingResurrection = true)
        assertEquals(ids(partDirectory), slowIntake.takenPartIds)

        // a later pass, which isn't the launch pass, reaches it and resurrects it all the same
        slowIntake.takes.single().store()
        slowIntake.stall = false
        reader.readPersistedSessionParts()

        val (first, last) = slowIntake.takes
        assertNull(first.envelope.sessionPartAttribute(EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART))
        assertEquals("1", last.envelope.sessionPartAttribute(EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART))
        assertDeleted(partDirectory, laterPartDirectory)
    }

    @Test
    fun `a session part this process persisted is not resurrected`() {
        terminateUserSession()
        persist(partDirectory, span = sessionSpan(processIdentifier = PROCESS_ID))
        createReader().readPersistedSessionParts()

        assertEquals(
            sessionSpan(processIdentifier = PROCESS_ID),
            intakeService.getIntakes<SessionPartPayload>().single().envelope.getSessionPartSpan(),
        )
    }

    @Test
    fun `a session part that records no process is only treated as dead by the launch pass`() {
        restoreDecision = UserSessionRestoreDecision.Restored(userSessionId = partDirectory.userSessionId, backgroundOnly = true)
        persist(partDirectory, span = sessionSpan(processIdentifier = null))
        persist(laterPartDirectory, span = sessionSpan(processIdentifier = null))
        writeTracker.markWriting(laterPartDirectory)
        val reader = createReader()

        reader.readPersistedSessionParts(performingResurrection = true)
        writeTracker.markComplete(laterPartDirectory)
        reader.readPersistedSessionParts()

        val (atLaunch, later) = intakeService.intakeList
        assertEquals("1", atLaunch.envelope.sessionPartAttribute(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART))
        assertNull(later.envelope.sessionPartAttribute(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART))
    }

    @Test
    fun `a dead session part's native crash is attached and only that crash is deleted`() {
        val crash = nativeCrash(partDirectory.sessionPartId)
        val otherCrash = nativeCrash("ffffffffffffffffffffffffffffffff")
        nativeCrashService.addNativeCrashData(crash)
        nativeCrashService.addNativeCrashData(otherCrash)
        persist(partDirectory, span = sessionSpan(sessionPartId = partDirectory.sessionPartId))
        createReader().readPersistedSessionParts()

        val envelope = intakeService.intakeList.single().envelope
        assertEquals(crash.nativeCrashId, envelope.sessionPartAttribute(EmbSessionAttributes.EMB_CRASH_ID))
        assertEquals(listOf(crash), nativeCrashService.nativeCrashesSent.map { it.first })
        assertEquals(listOf(otherCrash), nativeCrashService.getNativeCrashes())
    }

    @Test
    fun `each dead part a pass resurrects gets its own native crash`() {
        nativeCrashService.addNativeCrashData(nativeCrash(partDirectory.sessionPartId))
        nativeCrashService.addNativeCrashData(nativeCrash(laterPartDirectory.sessionPartId))
        persist(partDirectory, span = sessionSpan(sessionPartId = partDirectory.sessionPartId))
        persist(laterPartDirectory, span = sessionSpan(sessionPartId = laterPartDirectory.sessionPartId))

        createReader().readPersistedSessionParts()

        assertEquals(2, intakeService.intakeList.size)
        intakeService.intakeList.forEach { intake ->
            assertEquals(
                "crash-${intake.metadata.sessionPartId}",
                intake.envelope.sessionPartAttribute(EmbSessionAttributes.EMB_CRASH_ID),
            )
        }
    }

    @Test
    fun `a dead session part's native crash is only sent and deleted once the part is stored`() {
        val crash = nativeCrash(partDirectory.sessionPartId)
        nativeCrashService.addNativeCrashData(crash)
        persist(partDirectory, span = sessionSpan(sessionPartId = partDirectory.sessionPartId))
        intakeService.pendingResults.add(IntakeResult.RETRYABLE_FAILURE)
        val reader = createReader()

        // the part isn't stored, so its crash is neither sent nor deleted
        reader.readPersistedSessionParts()
        assertEquals(emptyList<NativeCrashData>(), nativeCrashService.nativeCrashesSent.map { it.first })
        assertEquals(listOf(crash), nativeCrashService.getNativeCrashes())

        // the retry finds the crash again, so the part still carries its id, and once it's stored the crash is sent
        reader.readPersistedSessionParts()
        val retried = intakeService.intakeList.last().envelope
        assertEquals(crash.nativeCrashId, retried.sessionPartAttribute(EmbSessionAttributes.EMB_CRASH_ID))
        assertEquals(listOf(crash), nativeCrashService.nativeCrashesSent.map { it.first })
        assertEquals(emptyList<NativeCrashData>(), nativeCrashService.getNativeCrashes())
    }

    @Test
    fun `a dead session part's native crash is left on disk when the part can never be stored`() {
        val crash = nativeCrash(partDirectory.sessionPartId)
        nativeCrashService.addNativeCrashData(crash)
        persist(partDirectory, span = sessionSpan(sessionPartId = partDirectory.sessionPartId))
        intakeService.pendingResults.add(IntakeResult.PERMANENT_FAILURE)
        createReader().readPersistedSessionParts()

        // the part is gone, and its crash is left for the next launch's resurrection to send on its own
        assertDeleted(partDirectory)
        assertEquals(emptyList<NativeCrashData>(), nativeCrashService.nativeCrashesSent.map { it.first })
        assertEquals(listOf(crash), nativeCrashService.getNativeCrashes())
    }

    @Test
    fun `a dead session part that cannot be resurrected is deleted and the pass moves on`() {
        persist(laterPartDirectory)
        // a second session span left as a snapshot makes the part hold two session spans once resurrected
        persist(partDirectory, snapshots = listOf(sessionSpan().copy(spanId = "aaaaaaaaaaaaaaa2", endTimeNanos = null)))
        createReader().readPersistedSessionParts()

        assertEquals(ids(laterPartDirectory), intakesAttempted())
        assertDeleted(partDirectory, laterPartDirectory)
        assertTrue(logger.internalErrorMessages.single().throwable is IllegalStateException)
    }

    @Test
    fun `the ids of every session part on disk are reported, delivered or not`() {
        persistTwoParts()
        writeTracker.markWriting(laterPartDirectory)

        assertEquals(setOf(partDirectory.sessionPartId, laterPartDirectory.sessionPartId), createReader().storedSessionPartIds())
    }

    @Test
    fun `the process that recorded the session part is preserved`() {
        persist(partDirectory)
        createReader().readPersistedSessionParts()
        assertEquals(PERSISTED_PROCESS_ID, intakeService.intakeList.single().metadata.processIdentifier)
    }

    @Test
    fun `the current process is used when the session part does not record one`() {
        persist(partDirectory, span = sessionSpan(processIdentifier = null))
        createReader().readPersistedSessionParts()
        assertEquals(PROCESS_ID, intakeService.intakeList.single().metadata.processIdentifier)
    }

    @Test
    fun `session parts are taken in serially in time order`() {
        persistTwoParts()
        slowIntake.stall = false
        createReader(slowIntake).readPersistedSessionParts()

        assertEquals(
            listOf(
                "take:${partDirectory.sessionPartId}",
                "wait:${partDirectory.sessionPartId}",
                "take:${laterPartDirectory.sessionPartId}",
                "wait:${laterPartDirectory.sessionPartId}",
            ),
            slowIntake.events,
        )
        assertDeleted(partDirectory, laterPartDirectory)
    }

    @Test
    fun `a session part that is still being written to is left alone`() {
        persist(partDirectory)
        writeTracker.markWriting(partDirectory)

        createReader().readPersistedSessionParts()

        assertNothingDelivered(retained = listOf(partDirectory))
    }

    @Test
    fun `a session part that cannot be reconstructed is deleted without being delivered or stopping the pass`() {
        persistTwoParts()
        assertTrue(File(File(sessionsDir, partDirectory.dirName), METADATA_FILE_NAME).delete())
        createReader().readPersistedSessionParts()

        assertEquals(ids(laterPartDirectory), intakesAttempted())
        assertDeleted(partDirectory, laterPartDirectory)
    }

    @Test
    fun `a session part intake that fails in a recoverable way stops the intake pass but its data is retained and intake is retried`() {
        persistTwoParts()
        intakeService.pendingResults.add(IntakeResult.RETRYABLE_FAILURE)
        val reader = createReader()

        // the part is kept and the pass stops, so the later part is not delivered ahead of it
        reader.readPersistedSessionParts()
        assertEquals(ids(partDirectory), intakesAttempted())
        assertRetained(partDirectory, laterPartDirectory)

        // the next pass is the retry, and it stores the part before the later one, so order is kept
        reader.readPersistedSessionParts()
        assertEquals(ids(partDirectory, partDirectory, laterPartDirectory), intakesAttempted())
        assertDeleted(partDirectory, laterPartDirectory)
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    @Test
    fun `a session part that fails its retry is deleted and the intake pass continues`() {
        persistTwoParts()
        intakeService.pendingResults.addAll(listOf(IntakeResult.RETRYABLE_FAILURE, IntakeResult.RETRYABLE_FAILURE))
        val reader = createReader()
        reader.readPersistedSessionParts()

        // one retry only, so a part that keeps failing cannot hold the parts behind it
        reader.readPersistedSessionParts()
        assertEquals(ids(partDirectory, partDirectory, laterPartDirectory), intakesAttempted())
        assertDeleted(partDirectory, laterPartDirectory)
        assertTrue(logger.internalErrorMessages.single().throwable is IllegalStateException)
    }

    @Test
    fun `a session part that fails storage in an unrecoverable way is deleted without a retry`() {
        persistTwoParts()
        intakeService.pendingResults.add(IntakeResult.PERMANENT_FAILURE)

        createReader().readPersistedSessionParts()

        // a failure that belongs to the payload would repeat on a retry, so the part's data is deleted, the failure
        // reported, and the later part is attempted in the same pass
        assertEquals(ids(partDirectory, laterPartDirectory), intakesAttempted())
        assertDeleted(partDirectory, laterPartDirectory)
        assertTrue(logger.internalErrorMessages.single().throwable is IllegalStateException)
    }

    @Test
    fun `reading persisted session parts is a no-op when there is nothing on disk`() {
        createReader().readPersistedSessionParts()

        assertNothingDelivered(retained = emptyList())
    }

    @Test
    fun `everything on disk is deleted when multi file persistence is disabled`() {
        persistTwoParts()
        assertTrue(File(sessionsDir, "unparseable.tmp").createNewFile())
        createReader(enabled = false).readPersistedSessionParts()

        assertEquals(emptyList<Any>(), intakeService.intakeList)
        assertEquals(emptyList<Any>(), intakeService.cacheList)
        assertFalse(sessionsDir.exists())
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    @Test
    fun `a session part whose intake times out twice is deleted and the intake pass continues`() {
        persistTwoParts()
        val reader = createReader(slowIntake)

        // the first timeout keeps the part and stops the pass before the later part is taken
        reader.readPersistedSessionParts()
        assertEquals(ids(partDirectory), slowIntake.takenPartIds)
        assertRetained(partDirectory, laterPartDirectory)

        // the intake never finishes, so the next pass gives up on it and takes the later part
        slowIntake.stall = false
        reader.readPersistedSessionParts()
        assertEquals(ids(partDirectory, laterPartDirectory), slowIntake.takenPartIds)
        assertEquals(2, slowIntake.takes.first().waits)
        assertDeleted(partDirectory, laterPartDirectory)
        assertEquals(
            listOf(TimeoutException::class, TimeoutException::class, IllegalStateException::class),
            logger.internalErrorMessages.map { it.throwable?.let { throwable -> throwable::class } },
        )
    }

    @Test
    fun `a timed out intake is waited on again by the next pass instead of retrying it from scratch`() {
        persistTwoParts()
        val reader = createReader(slowIntake)
        reader.readPersistedSessionParts()

        // the first intake finishes while the next pass waits on it
        val firstTake = slowIntake.takes.single()
        firstTake.onNextWait = firstTake::store
        slowIntake.stall = false
        reader.readPersistedSessionParts()

        assertEquals(ids(partDirectory, laterPartDirectory), slowIntake.takenPartIds)
        assertEquals(2, firstTake.waits)
        assertDeleted(partDirectory, laterPartDirectory)
    }

    @Test
    fun `a session part left by a timed out pass is retried when there is a new reader instance`() {
        persistTwoParts()
        createReader(slowIntake).readPersistedSessionParts()
        slowIntake.stall = false
        createReader(slowIntake).readPersistedSessionParts()

        assertEquals(ids(partDirectory, partDirectory, laterPartDirectory), slowIntake.takenPartIds)
        assertEquals(1, slowIntake.takes.first().waits)
        assertDeleted(partDirectory, laterPartDirectory)
    }

    @Test
    fun `a recoverable failure reported after the intake timed out will still be retried`() {
        persistTwoParts()
        val reader = createReader(slowIntake)
        reader.readPersistedSessionParts()

        // the late failure is picked up by the next pass, which holds the part for its retry
        slowIntake.takes.single().fail(IntakeResult.RETRYABLE_FAILURE)
        slowIntake.stall = false
        reader.readPersistedSessionParts()
        assertEquals(ids(partDirectory), slowIntake.takenPartIds)
        assertRetained(partDirectory, laterPartDirectory)

        // the retry takes in the previously failed part and stores it before the later part
        reader.readPersistedSessionParts()
        assertEquals(ids(partDirectory, partDirectory, laterPartDirectory), slowIntake.takenPartIds)
        assertDeleted(partDirectory, laterPartDirectory)
    }

    @Test
    fun `an unrecoverable failure after an initial intake time out deletes the part and doesn't retry`() {
        persistTwoParts()
        val reader = createReader(slowIntake)
        reader.readPersistedSessionParts()

        // the late failure is picked up by the next pass, which drops the part and moves on
        slowIntake.takes.single().fail(IntakeResult.PERMANENT_FAILURE)
        slowIntake.stall = false
        reader.readPersistedSessionParts()

        assertEquals(ids(partDirectory, laterPartDirectory), slowIntake.takenPartIds)
        assertDeleted(partDirectory, laterPartDirectory)
        assertTrue(logger.internalErrorMessages.last().throwable is IllegalStateException)
    }

    @Test
    fun `the session part from an interrupted intake will be retried again if it times out after its first complete intake attempt`() {
        persistTwoParts()
        slowIntake.firstWait = { throw InterruptedException() }
        val reader = createReader(slowIntake)

        // the interrupt stops the intake pass and the interrupt flag is restored on the thread
        reader.readPersistedSessionParts()
        assertTrue(Thread.interrupted())
        slowIntake.firstWait = null

        // the next intake pass times out the intake for the first time, so the part is kept
        reader.readPersistedSessionParts()
        assertEquals(ids(partDirectory), slowIntake.takenPartIds)
        assertRetained(partDirectory, laterPartDirectory)

        // the second timeout deletes it and the intake pass moves on to the later part
        slowIntake.stall = false
        reader.readPersistedSessionParts()
        assertEquals(ids(partDirectory, laterPartDirectory), slowIntake.takenPartIds)
        assertEquals(3, slowIntake.takes.first().waits)
        assertDeleted(partDirectory, laterPartDirectory)
    }

    @Test
    fun `a cancelled intake stops the intake pass and will be picked up in the next intake pass`() {
        persistTwoParts()
        slowIntake.firstWait = { throw CancellationException() }
        val reader = createReader(slowIntake)

        // the later part is not taken ahead of the cancelled one
        reader.readPersistedSessionParts()
        assertEquals(ids(partDirectory), slowIntake.takenPartIds)
        assertRetained(partDirectory, laterPartDirectory)

        slowIntake.firstWait = null
        slowIntake.stall = false
        reader.readPersistedSessionParts()
        assertEquals(ids(partDirectory, partDirectory, laterPartDirectory), slowIntake.takenPartIds)
        assertDeleted(partDirectory, laterPartDirectory)
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    /**
     * An intake service that can be configured to behave in nonstandard ways:
     *
     * - Set [stall] to true to ensure an intake never completes. Setting it to false completes the intakes right away.
     * - Set [firstWait] to run some code on the first wait of each new take.
     *
     * Every take and wait is recorded in [events].
     */
    private class SlowIntakeService : IntakeService {
        val takes: MutableList<SlowTake> = mutableListOf()
        val events: MutableList<String> = mutableListOf()
        var stall: Boolean = true
        var firstWait: (() -> Unit)? = null

        val takenPartIds: List<String>
            get() = takes.map(SlowTake::partId)

        override fun shutdown() {}

        override fun take(
            intake: Envelope<*>,
            metadata: StoredTelemetryMetadata,
            staleEntry: StoredTelemetryMetadata?,
            onStored: (() -> Unit)?,
        ): Future<IntakeResult> {
            val take = SlowTake(metadata.sessionPartId, intake, events, onStored)
            takes.add(take)
            events.add("take:${take.partId}")
            take.onNextWait = firstWait
            if (!stall) {
                take.store()
            }
            return take
        }
    }

    private class SlowTake(
        val partId: String,
        val envelope: Envelope<*>,
        private val events: MutableList<String>,
        private val onStored: (() -> Unit)?,
    ) : Future<IntakeResult> {
        var waits: Int = 0
        var onNextWait: (() -> Unit)? = null
        private var done = false
        private var result: IntakeResult = IntakeResult.STORED

        fun store() {
            onStored?.invoke()
            done = true
        }

        fun fail(result: IntakeResult) {
            this.result = result
            done = true
        }

        override fun cancel(mayInterruptIfRunning: Boolean) = false
        override fun isCancelled() = false
        override fun isDone() = done
        override fun get() = error("the reader must always wait with a timeout")
        override fun get(timeout: Long, unit: TimeUnit): IntakeResult {
            waits++
            events.add("wait:$partId")
            val action = onNextWait
            onNextWait = null
            action?.invoke()
            if (!done) {
                throw TimeoutException("stalled")
            }
            return result
        }
    }

    private fun createReader(
        intakeService: IntakeService = this.intakeService,
        enabled: Boolean = true,
    ) = SessionPartReader(
        sessionsDir = lazy { sessionsDir },
        directoryStore = directoryStore,
        reconstructionService = SessionReconstructionService(lazy { sessionsDir }, logger),
        intakeService = intakeService,
        writeTracker = writeTracker,
        deadPartResurrector = MultiFileDeadPartResurrector(
            resurrector = SessionPartResurrector(FakeCachedLogEnvelopeStore()),
            nativeCrashServiceProvider = { nativeCrashService },
            restoreDecisionProvider = { restoreDecision },
        ),
        processIdProvider = { PROCESS_ID },
        configService = FakeConfigService(
            config = EmbraceConfig(persistence = { PersistenceConfig(multiFileEnabled = { enabled }) }),
        ),
        logger = logger,
    )

    /**
     * Persists [partDirectory] and [laterPartDirectory], the later one first so that the reader has to
     * order them.
     */
    private fun persistTwoParts() {
        persist(laterPartDirectory)
        persist(partDirectory)
    }

    private fun persist(
        directory: SessionPartDirectory,
        span: Span = sessionSpan(),
        snapshots: List<Span> = emptyList(),
    ) {
        create(directory)

        val target = SessionPartWriteTarget(lazy { sessionsDir }) { directory }
        SessionMetadataWriter(
            target = target,
            metadataSource = { EnvelopeMetadata(username = "fake-user") },
            resourceSource = { EnvelopeResource(appVersion = "1.0.0") },
            envelopeVersion = SESSION_ENVELOPE_VERSION,
            envelopeType = SESSION_ENVELOPE_TYPE,
            sharedLibSymbolMappingSource = { null },
            logger = logger,
        ).write()
        CompletedSpansWriter(target, logger).write(listOf(span))
        if (snapshots.isNotEmpty()) {
            SpanSnapshotsWriter(target, logger).write(snapshots)
        }
    }

    /**
     * Creates the directory via the store, then drains the worker so the work has completed.
     */
    private fun create(directory: SessionPartDirectory) {
        directoryStore.create(directory)
        executor.runCurrentlyBlocked()
    }

    private fun intakesAttempted(): List<String> = intakeService.intakeList.map { it.metadata.sessionPartId }

    /**
     * Exactly [directories] are still stored, both in the store's index and on disk.
     */
    private fun assertRetained(vararg directories: SessionPartDirectory) {
        assertEquals(directories.toSet(), directoryStore.storedDirectories().toSet())
        assertEquals(directories.map(SessionPartDirectory::dirName).toSet(), sessionsDir.list()?.toSet() ?: emptySet<String>())
    }

    private fun assertDeleted(vararg directories: SessionPartDirectory) {
        val stored = directoryStore.storedDirectories().toSet()
        val onDisk = sessionsDir.list()?.toSet() ?: emptySet<String>()
        directories.forEach { directory ->
            assertTrue(directory !in stored)
            assertTrue(directory.dirName !in onDisk)
        }
    }

    /**
     * Nothing reached intake, and every session part is still on disk: nothing was delivered, so
     * nothing may have been deleted.
     */
    private fun assertNothingDelivered(retained: List<SessionPartDirectory>) {
        assertEquals(emptyList<Any>(), intakeService.intakeList)
        assertEquals(emptyList<Any>(), intakeService.cacheList)
        assertRetained(*retained.toTypedArray())
        assertEquals(emptyList<FakeInternalLogger.LogMessage>(), logger.internalErrorMessages)
    }

    /**
     * Makes the user session the test parts belong to one this launch terminated.
     */
    private fun terminateUserSession() {
        restoreDecision = UserSessionRestoreDecision.Terminated(
            userSessionId = partDirectory.userSessionId,
            backgroundOnly = false,
            reason = TERMINATION_REASON,
        )
    }

    private fun nativeCrash(sessionPartId: String) = NativeCrashData(
        nativeCrashId = "crash-$sessionPartId",
        sessionPartId = sessionPartId,
        userSessionId = partDirectory.userSessionId,
        timestamp = FakeClock.DEFAULT_FAKE_CURRENT_TIME,
        crash = "crash",
        symbols = null,
    )

    @Suppress("UNCHECKED_CAST")
    private fun Envelope<*>.sessionPartAttribute(key: String): String? =
        (this as Envelope<SessionPartPayload>).getSessionPartSpan()?.attributes?.findAttributeValue(key)
}
