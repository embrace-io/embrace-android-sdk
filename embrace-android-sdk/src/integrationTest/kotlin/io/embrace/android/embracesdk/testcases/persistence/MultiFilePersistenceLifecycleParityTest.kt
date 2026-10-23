package io.embrace.android.embracesdk.testcases.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.assertions.waitUntilConditionMet
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_PERIODIC_CACHE_INTERVAL_MS
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SpanCollection
import io.embrace.android.embracesdk.internal.session.persistence.SpanProto
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Parity across session part boundaries and process deaths: multiple parts, crashes, kills, relaunches and full
 * payload storage.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class MultiFilePersistenceLifecycleParityTest(
    persistenceMode: PersistenceMode,
    otelSdkMode: OtelSdkMode,
) : MultiFilePersistenceParityTestBase(persistenceMode, otelSdkMode) {

    /**
     * A change made just before the process dies is lost in both modes: the multi-file mode's
     * debounced metadata write and the single-file mode's next periodic cache tick are both still
     * pending, so the part is resurrected with the metadata it started with.
     */
    @Test
    fun `envelope metadata changed just before the process dies is lost in both modes`() {
        killMidPart {
            changeEnvelopeMetadata(AFTER)
            clock.tick(METADATA_WRITE_DELAY_MS - 1)
            persistDueWrites()
        }
        relaunchAndAssertDeliveredMetadata(BEFORE)
    }

    /**
     * The modes differ here. The single-file mode caches on a fixed cadence, so a change made just after
     * a tick waits a whole interval for the next one; the multi-file mode writes metadata a short
     * delay after the change itself. A process that dies between the two keeps the change under the
     * multi-file mode and loses it under the single-file mode.
     */
    @Test
    fun `envelope metadata changed just after a single-file cache tick is kept only by the multi-file mode`() {
        killMidPart {
            changeEnvelopeMetadata(AFTER)
            clock.tick(METADATA_WRITE_DELAY_MS)
            persistDueWrites()
        }
        relaunchAndAssertDeliveredMetadata(
            when (persistenceMode) {
                PersistenceMode.SINGLE_FILE -> BEFORE
                PersistenceMode.MULTI_FILE -> AFTER
            },
        )
    }

    /**
     * The second change comes within the multi-file mode's metadata write delay of the first, so both
     * modes coalesce the two into one write: the multi-file mode's debounced metadata write and the
     * single-file mode's next periodic cache tick.
     */
    @Test
    fun `the last envelope metadata change inside one write interval is what the next launch delivers`() {
        killMidPart {
            changeEnvelopeMetadata(AFTER)
            clock.tick(METADATA_WRITE_DELAY_MS / 2)
            changeEnvelopeMetadata(LAST)
            clock.tick(WRITE_INTERVAL_MS - METADATA_WRITE_DELAY_MS / 2)
            persistDueWrites()
        }
        relaunchAndAssertDeliveredMetadata(LAST)
    }

    @Test
    fun `each session part in a process is persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession {
                    embrace.recordSpan("first-part-span") { clock.tick(100) }
                }
                clock.tick(20000)
                recordSession {
                    embrace.recordSpan("second-part-span") { clock.tick(100) }
                }
            },
            assertAction = {
                val parts = deliveredParts(expectedParts = 2)
                parts.forEach(::assertMinimalSessionPayload)
                assertEquals(
                    setOf("first-part-span", "second-part-span"),
                    parts.map { part -> part.allSpanNames().single { it.endsWith("-part-span") } }.toSet(),
                )
            },
        )
    }

    @Test
    fun `a span that stops after its session part ended is persisted identically`() {
        lateinit var span: EmbraceSpan
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(backgroundActivity = BackgroundActivityRemoteConfig(0f)),
            testCaseAction = {
                recordSession {
                    span = checkNotNull(embrace.startSpan("late-span"))
                }
                clock.tick(100)
                span.stop()
                clock.tick(20000)
                recordSession()
            },
            assertAction = {
                val parts = deliveredParts(expectedParts = 2)
                assertTrue(
                    "late-span missing from every payload",
                    parts.any { "late-span" in it.allSpanNames() },
                )
            },
        )
    }

    @Test
    fun `no span is created while no session part is active`() {
        var blockRan = false
        lateinit var orphaned: EmbraceSpan
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(backgroundActivity = BackgroundActivityRemoteConfig(0f)),
            testCaseAction = {
                recordSession()
                orphaned = embrace.startSpan("started-span")
                embrace.recordSpan("recorded-span") {
                    blockRan = true
                    clock.tick(100)
                }
                clock.tick(20000)
                recordSession()
            },
            assertAction = {
                assertFalse("a span was started with no active session part", orphaned.isRecording)
                assertNull("a span was started with no active session part", orphaned.spanId)
                assertTrue("recordSpan did not run the block it was given", blockRan)

                deliveredParts(expectedParts = 2).forEach { part ->
                    val names = part.allSpanNames()
                    assertFalse("started-span was persisted: $names", "started-span" in names)
                    assertFalse("recorded-span was persisted: $names", "recorded-span" in names)
                }
            },
        )
    }

    @Test
    fun `a crashed session part is left on disk for the next process launch`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession {
                    simulateJvmUncaughtException(RuntimeException("Boom!"))
                }
            },
            assertAction = {
                assertEquals(0, getSessionEnvelopes(0).size)

                when (persistenceMode) {
                    // the single-file mode keeps no session part directories
                    PersistenceMode.SINGLE_FILE -> assertEquals(
                        emptyList<SessionPartDirectory>(),
                        storedSessionPartDirectories(),
                    )

                    // reading a part back is very unlikely to complete while the process is
                    // crashing, so the part is sealed and left for the next launch to deliver
                    // rather than being handed to the intake service here
                    PersistenceMode.MULTI_FILE -> {
                        assertEquals(
                            "the crashed session part was not left on disk",
                            1,
                            storedSessionPartDirectories().size,
                        )
                        val sessionSpans = sessionSpansOnDisk()
                        assertEquals(
                            "wrong number of session spans logged for the crashed part",
                            1,
                            sessionSpans.size,
                        )
                        assertNotNull(
                            "the crashed session part was left unsealed on disk",
                            sessionSpans.single().end_time_unix_nano,
                        )
                    }
                }
            },
        )
    }

    @Test
    fun `a crashed session part is delivered by the next process launch`() {
        lateinit var crashedProcessId: String
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession {
                    embrace.recordSpan("pre-crash-span") { clock.tick(100) }
                    simulateJvmUncaughtException(RuntimeException("Boom!"))
                }
            },
            assertAction = {
                crashedProcessId =
                    testRule.services.otelSdkConfig.processIdentifier
            },
        )

        // tear the crashed process down so the next launch reads the shared storage from scratch
        testRule.services.networkConnectivityService.close()
        testRule.services.otelSdkConfig.close()
        testRule.services.workerThreadPool.close()

        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {},
            assertAction = {
                val envelope = deliveredParts().single()
                val attributes = envelope.findSessionPartSpan().attributes
                val names = envelope.allSpanNames()
                assertTrue("pre-crash-span missing: $names", "pre-crash-span" in names)
                assertNotNull(
                    "the part delivered by the next launch is not the crashed one",
                    attributes?.findAttributeValue(EmbSessionAttributes.EMB_CRASH_ID),
                )
                assertEquals(
                    crashedProcessId,
                    attributes?.findAttributeValue(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER),
                )
                assertFalse(
                    "the delivered part was left on disk",
                    storedSessionPartDirectories().any { it.sessionPartId == envelope.getSessionPartId() },
                )
            },
        )
    }

    @Test
    fun `a session part whose process died after it ended is delivered unchanged by the next launch`() {
        // Verify the state of the payloads after process termination
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            setupAction = {
                getFakeErrorHandler().throwOnSdkError = false
            },
            testCaseAction = {
                // Allow a session part's data to be persisted to disk, but stop the worker used in
                // multi-file mode from processing it, leaving the raw files on disk.
                val ioRegWorker =
                    testRule.setup.getFakedWorkerExecutor(Worker.Background.IoRegWorker)
                ioRegWorker.blockingMode = true
                recordSession()
                ioRegWorker.shutdownNow()
            },
            assertAction = {
                when (persistenceMode) {
                    // In single-file mode, the ended part is built and handed to delivery directly, so it's
                    // delivered in this process.
                    PersistenceMode.SINGLE_FILE -> {
                        assertMatchesGoldenFile(deliveredParts().single())
                        awaitDeliveredSessionPayloadsDeleted()
                    }

                    // In multi-file mode, the ended parts stay on disk because the blocked worker never
                    // handed them to delivery: the background part before the session, the session, and
                    // the background part that was live.
                    PersistenceMode.MULTI_FILE -> {
                        assertEquals(0, getSessionEnvelopes(0).size)
                        assertEquals(3, storedSessionPartDirectories().size)
                        assertEquals(0, testRule.setup.getFakeErrorHandler().sdkErrorMessages.size)
                    }
                }
            },
        )

        testRule.services.networkConnectivityService.close()
        testRule.services.otelSdkConfig.close()
        testRule.services.workerThreadPool.close()

        // Verify the real payload gets delivered in a new process with an unblocked worker if we are in multi-file mode.
        // This part ended cleanly, so it must not be treated as terminated by the process's death, and is delivered as
        // the single-file mode would have delivered it.
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            setupAction = {
                getFakeErrorHandler().throwOnSdkError = false
            },
            testCaseAction = {},
            assertAction = {
                when (persistenceMode) {
                    PersistenceMode.SINGLE_FILE -> {
                        // Everything was delivered in the first process, so nothing is left to deliver.
                        assertEquals(0, getSessionEnvelopes(0).size)
                    }

                    PersistenceMode.MULTI_FILE -> {
                        // Payload assembled and delivered as expected with no errors.
                        assertMatchesGoldenFile(deliveredParts().single())
                        assertEquals(0, testRule.setup.getFakeErrorHandler().sdkErrorMessages.size)

                        // Every part the dead process left behind is delivered, not just its last one: the
                        // background part before the session, and the one that was live when it died.
                        deliveredParts(expectedParts = 2, state = ProcessState.BACKGROUND)
                    }
                }
            },
        )
    }

    @Test
    fun `each session part holds exactly one session span`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession()
                clock.tick(20000)
                recordSession()
            },
            assertAction = {
                val parts = deliveredParts(expectedParts = 2)
                parts.forEach(::assertExactlyOneSessionSpan)
                deliveredParts(expectedParts = 2, state = ProcessState.BACKGROUND)
                    .forEach(::assertExactlyOneSessionSpan)
                assertEquals(
                    "the same session span was delivered for both parts",
                    2,
                    parts.map { it.findSessionPartSpan().spanId }.distinct().size,
                )
            },
        )
    }

    @Test
    fun `a span in flight across a part boundary does not disturb either session span`() {
        lateinit var span: EmbraceSpan
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(backgroundActivity = BackgroundActivityRemoteConfig(0f)),
            testCaseAction = {
                recordSession {
                    span = checkNotNull(embrace.startSpan("cross-part-span"))
                }
                clock.tick(20000)
                recordSession {
                    span.stop()
                }
            },
            assertAction = {
                val parts = deliveredParts(expectedParts = 2)
                parts.forEach(::assertExactlyOneSessionSpan)
                assertTrue(
                    "cross-part-span was not snapshotted by the part it started in",
                    parts.any { part -> part.data.spanSnapshots.orEmpty().any { it.name == "cross-part-span" } },
                )
                assertTrue(
                    "cross-part-span was not completed in the part it stopped in",
                    parts.any { part -> part.data.spans.orEmpty().any { it.name == "cross-part-span" } },
                )
            },
        )
    }

    @Test
    fun `a session part rejected by full payload storage in multi-file mode is kept and delivered once space frees`() {
        assumeTrue(persistenceMode == PersistenceMode.MULTI_FILE)
        lateinit var heldBack: List<StoredTelemetryMetadata>
        lateinit var keptAfterRejection: List<SessionPartDirectory>
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(backgroundActivity = BackgroundActivityRemoteConfig(0f)),
            setupAction = {
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                heldBack = fillPayloadStorage()
            },
            testCaseAction = {
                recordSession {
                    embrace.recordSpan("first-part-span") { clock.tick(100) }
                }
                keptAfterRejection = storedSessionPartDirectories()

                freePayloadStorage(heldBack)
                simulateConnectivityChange(ConnectivityStatus.Wifi(true))
                clock.tick(20000)
                recordSession {
                    embrace.recordSpan("second-part-span") { clock.tick(100) }
                }
            },
            assertAction = {
                assertEquals(
                    "the part the storage rejected was not left on disk",
                    1,
                    keptAfterRejection.size,
                )
                val retried = deliveredParts(expectedParts = 2).single { "first-part-span" in it.allSpanNames() }
                assertEquals(keptAfterRejection.single().sessionPartId, retried.getSessionPartId())
                assertEquals(
                    "a delivered part was left on disk",
                    emptyList<SessionPartDirectory>(),
                    storedSessionPartDirectories(),
                )
            },
        )
    }

    @Test
    fun `a session part still rejected by full payload storage in multi-file mode on its retry is deleted`() {
        assumeTrue(persistenceMode == PersistenceMode.MULTI_FILE)
        lateinit var keptAfterRejection: List<SessionPartDirectory>
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(backgroundActivity = BackgroundActivityRemoteConfig(0f)),
            setupAction = {
                // the deleted part is reported as an SDK error
                getFakeErrorHandler().throwOnSdkError = false
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                fillPayloadStorage()
            },
            testCaseAction = {
                recordSession()
                keptAfterRejection = storedSessionPartDirectories()
                clock.tick(20000)
                recordSession()
            },
            assertAction = {
                val rejectedTwice = keptAfterRejection.single()
                val remaining = storedSessionPartDirectories()
                assertFalse(
                    "the part rejected on its retry was left on disk",
                    remaining.any { it.sessionPartId == rejectedTwice.sessionPartId },
                )
                assertEquals(
                    "the part rejected for the first time was not left on disk for its retry",
                    1,
                    remaining.size,
                )
                assertTrue(
                    "the deleted part was not reported",
                    testRule.setup.getFakeErrorHandler().sdkErrorMessages.any {
                        it.msg == SdkErrorType.SessionPartReadFail.toString() &&
                            it.throwable?.message?.contains("failed intake retry failed again") == true
                    },
                )
            },
        )
    }

    private fun assertExactlyOneSessionSpan(envelope: Envelope<SessionPartPayload>) {
        assertEquals(
            "wrong number of session spans in the delivered part",
            1,
            envelope.data.spans.orEmpty().count { it.hasEmbraceAttribute(EmbType.Ux.Session) },
        )
        assertEquals(
            "a session span was delivered as a snapshot",
            0,
            envelope.data.spanSnapshots.orEmpty().count { it.hasEmbraceAttribute(EmbType.Ux.Session) },
        )
    }

    /**
     * Records a foreground part that the process dies in the middle of. Both modes persist the part as it starts.
     */
    private fun killMidPart(action: EmbraceActionInterface.() -> Unit) {
        testRule.runTest(
            instrumentedConfig = hostedSdkConfig,
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                changeEnvelopeMetadata(BEFORE)
                recordSession(endInBackground = false) {
                    // the single-file mode's first cache tick is due as the part starts, while the
                    // multi-file mode has already written the part's metadata
                    persistDueWrites()
                    action()
                }
                killProcess()
            },
            assertAction = {
                assertEquals(0, getSessionEnvelopes(0).size)
            },
        )
    }

    /**
     * Starts the next process, which delivers the part the previous one died in, and asserts it
     * carries the expected set of metadata prefixed by [token].
     */
    private fun relaunchAndAssertDeliveredMetadata(token: String) {
        testRule.services.networkConnectivityService.close()
        testRule.services.otelSdkConfig.close()
        testRule.services.workerThreadPool.close()
        testRule.runTest(
            instrumentedConfig = hostedSdkConfig,
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {},
            assertAction = {
                deliveredParts().single().assertEnvelopeMetadata(token)
            },
        )
    }

    /**
     * Runs every persistence write that is due by now: the multi-file mode's debounced writes, and
     * the single-file mode's periodic cache tick.
     */
    private fun persistDueWrites() {
        persistenceWorkers.forEach { worker ->
            testRule.setup.getFakedWorkerExecutor(worker).runCurrentlyBlocked()
        }
    }

    /**
     * Kills the process: every write still waiting on a debounce or a cache tick dies with it. What
     * was already handed to storage is allowed to land, so only the timing of the persistence mode
     * decides what survives.
     */
    private fun killProcess() {
        persistenceWorkers.forEach { worker ->
            testRule.setup.getFakedWorkerExecutor(worker).apply {
                // queue the pending writes without running them, then discard them
                blockingMode = true
                shutdownNow()
            }
        }
        testRule.services.intakeService.shutdown()
    }

    /**
     * The session spans logged for the newest session part. A part that can be read back holds
     * exactly one, so anything else here is a leak from an adjacent part.
     */
    private fun sessionSpansOnDisk(): List<SpanProto> {
        val directory = storedSessionPartDirectories().maxWithOrNull(SessionPartDirectory.comparator) ?: return emptyList()
        val bytes = File(File(sessionsDir(), directory.dirName), COMPLETED_SPANS_FILE_NAME)
            .takeIf(File::isFile)
            ?.readBytes()
            ?: return emptyList()
        return SpanCollection.ADAPTER.decode(bytes).spans.filter { span ->
            span.attributes.any { it.key == "emb.type" && it.value_ == "ux.session" }
        }
    }

    /**
     * Waits until delivery has deleted every session payload it sent. The server receives a payload
     * before delivery deletes it, so a process stopped in between leaves the payload on disk for the
     * next launch to send again.
     */
    private fun awaitDeliveredSessionPayloadsDeleted() {
        val storage = testRule.services.payloadStorageService
        waitUntilConditionMet(
            dataProvider = { storage.getPayloadsByPriority().count { it.envelopeType == SupportedEnvelopeType.SESSION } },
            condition = { it == 0 },
            errorMessageSupplier = { "a delivered session payload was left on disk" },
        )
    }

    private fun storedSessionPartDirectories(): List<SessionPartDirectory> =
        (sessionsDir().list() ?: emptyArray()).mapNotNull(SessionPartDirectory::fromDirName)

    private fun sessionsDir(): File = storageDir(StorageLocation.SESSION_SPLIT)

    private fun storageDir(location: StorageLocation): File {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return location.asFile(
            errorHandler = FakeErrorHandler(),
            rootDirSupplier = { ctx.filesDir },
            fallbackDirSupplier = { ctx.cacheDir },
        ).value
    }

    /**
     * Fills the payload storage to its count limit, as an earlier offline process would have left
     * it, with crash payloads: they outrank a session part, so the storage rejects the part rather
     * than evicting one of them. Returns what was stored so a test can free the space again.
     */
    private fun fillPayloadStorage(): List<StoredTelemetryMetadata> {
        val dir = storageDir(StorageLocation.PAYLOAD)
        return (0 until PAYLOAD_STORAGE_LIMIT).map { index ->
            StoredTelemetryMetadata(
                timestamp = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 1000L,
                uuid = UUID(0L, index.toLong()).toString(),
                processIdentifier = "earlier-process",
                envelopeType = SupportedEnvelopeType.CRASH,
            ).also { File(dir, it.filename).writeBytes(ByteArray(0)) }
        }
    }

    /**
     * Frees the payload storage the way delivery does: by deleting stored payloads through the
     * storage service, which keeps its index in step with the disk.
     */
    private fun freePayloadStorage(entries: List<StoredTelemetryMetadata>) {
        val storage = testRule.services.payloadStorageService
        val deleted = CountDownLatch(entries.size)
        entries.forEach { storage.delete(it) { deleted.countDown() } }
        assertTrue("the payload storage was not freed", deleted.await(5, TimeUnit.SECONDS))
    }

    private companion object {
        const val COMPLETED_SPANS_FILE_NAME = "completed_spans.pb"

        // the single-file mode's default periodic cache interval
        const val WRITE_INTERVAL_MS = DEFAULT_PERIODIC_CACHE_INTERVAL_MS

        // the multi-file mode's metadata write delay (SessionPartWriterImpl.METADATA_WRITE_DELAY_MS)
        const val METADATA_WRITE_DELAY_MS = 500L
        const val LAST = "last"

        val persistenceWorkers = listOf(
            Worker.Background.SessionPersistenceWorker,
            Worker.Background.PeriodicCacheWorker,
        )

        const val PAYLOAD_STORAGE_LIMIT = 500
    }
}
