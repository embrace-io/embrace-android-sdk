package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.assertions.findAttributeValue
import io.embrace.android.embracesdk.assertions.findSpansByName
import io.embrace.android.embracesdk.assertions.getLastHeartbeatTimeMs
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.assertions.getStartTime
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeCachedLogEnvelopeStore
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeNativeCrashService
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakeSchedulingService
import io.embrace.android.embracesdk.fakes.FakeSpanData.Companion.perfSpanSnapshot
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.fakeEmptyLogEnvelope
import io.embrace.android.embracesdk.fakes.fakeEnvelopeMetadata
import io.embrace.android.embracesdk.fakes.fakeEnvelopeResource
import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.fakes.fakeLaterEnvelopeMetadata
import io.embrace.android.embracesdk.fakes.fakeLaterEnvelopeResource
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.attrs.embCrashId
import io.embrace.android.embracesdk.internal.arch.attrs.embState
import io.embrace.android.embracesdk.internal.arch.attrs.isEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.CRASH
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeServiceImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.internal.toEmbraceSpanData
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.spans.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.GZIPInputStream

class PayloadResurrectionServiceImplTest {

    private lateinit var payloadStorageService: FakePayloadStorageService
    private lateinit var intakeExecutor: BlockableExecutorService
    private lateinit var schedulingService: FakeSchedulingService
    private lateinit var cacheStorageService: FakePayloadStorageService
    private lateinit var cachedLogEnvelopeStore: FakeCachedLogEnvelopeStore
    private lateinit var nativeCrashService: FakeNativeCrashService
    private lateinit var logger: FakeInternalLogger
    private lateinit var serializer: TestPlatformSerializer
    private lateinit var resurrectionService: PayloadResurrectionServiceImpl

    @Before
    fun setUp() {
        payloadStorageService = FakePayloadStorageService()
        intakeExecutor = BlockableExecutorService(blockingMode = false)
        schedulingService = FakeSchedulingService()
        cacheStorageService = FakePayloadStorageService()
        cachedLogEnvelopeStore = FakeCachedLogEnvelopeStore()
        nativeCrashService = FakeNativeCrashService()
        logger = FakeInternalLogger(false)
        serializer = TestPlatformSerializer()
        resurrectionService = PayloadResurrectionServiceImpl(
            intakeService = IntakeServiceImpl(
                schedulingService,
                payloadStorageService,
                cacheStorageService,
                logger,
                serializer,
                PriorityWorker(intakeExecutor),
            ),
            cacheStorageService = cacheStorageService,
            cachedLogEnvelopeStore = cachedLogEnvelopeStore,
            logger = logger,
            serializer = serializer,
        )
    }

    @Test
    fun `completion listeners fired after successful resurrection`() {
        var listenerCalled = false
        resurrectionService.addResurrectionCompleteListener { listenerCalled = true }
        resurrectInBackground()
        assertTrue(listenerCalled)
    }

    @Test
    fun `completion listeners fired after failed resurrection`() {
        var listenerCalled = false
        resurrectionService.addResurrectionCompleteListener { listenerCalled = true }
        cacheStorageService.addFakePayload(fakeCachedSessionStoredTelemetryMetadata)
        resurrectInBackground()
        assertTrue(listenerCalled)
    }

    @Test
    fun `if no previous cached session then send previous cached sessions should not send anything`() {
        resurrectInBackground()
        assertEquals(0, payloadStorageService.storedPayloadCount())
    }

    @Test
    fun `dead session resurrected and delivered`() {
        deadSessionEnvelope.resurrectPayload()
        val storedMetadata = payloadStorageService.storedPayloadMetadata().single()
        assertEquals(fakeCachedSessionStoredTelemetryMetadata.copy(complete = true), storedMetadata)
        assertEquals(0, cacheStorageService.storedPayloadCount())

        val sessionEnvelope = getStoredParts().single()
        val sessionSpan = checkNotNull(sessionEnvelope.getSessionSpan())
        val expectedStartTimeMs = deadSessionEnvelope.getStartTime()
        val expectedEndTimeMs = deadSessionEnvelope.getLastHeartbeatTimeMs()

        assertEmbraceSpanData(
            span = sessionSpan,
            expectedStartTimeMs = expectedStartTimeMs,
            expectedEndTimeMs = expectedEndTimeMs,
            expectedParentId = OtelIds.INVALID_SPAN_ID,
            expectedErrorCode = ErrorCode.FAILURE,
            expectedCustomAttributes = mapOf(
                EmbType.Ux.Session.asPair()
            )
        )
    }

    @Test
    fun `all payloads from previous app launches are deleted after resurrection`() {
        cacheStorageService.addPayload(
            metadata = fakeCachedCrashEnvelopeMetadata,
            data = fakeEmptyLogEnvelope()
        )
        assertEquals(1, cacheStorageService.storedPayloadCount())
        assertEquals(0, cacheStorageService.deleteCount.get())
        resurrectInBackground()

        assertEquals(0, payloadStorageService.storedPayloadCount())
        assertEquals(1, cacheStorageService.deleteCount.get())
    }

    @Test
    fun `snapshot will be delivered as failed span once resurrected`() {
        deadSessionEnvelope.resurrectPayload()

        val sentSession = getStoredParts().single()
        assertEquals(2, sentSession.data.spans?.size)
        assertEquals(0, sentSession.data.spanSnapshots?.size)

        val sessionSpan = checkNotNull(sentSession.getSessionSpan())
        val spanSnapshot =
            checkNotNull(
                deadSessionEnvelope.data.spanSnapshots?.filterNot { it.spanId == sessionSpan.spanId }
                    ?.single()
            )
        val resurrectedSnapshot = sentSession.findSpansByName(checkNotNull(spanSnapshot.name)).single()

        assertEmbraceSpanData(
            span = resurrectedSnapshot,
            expectedStartTimeMs = checkNotNull(spanSnapshot.startTimeNanos?.nanosToMillis()),
            expectedEndTimeMs = checkNotNull(sessionSpan.endTimeNanos?.nanosToMillis()),
            expectedParentId = OtelIds.INVALID_SPAN_ID,
            expectedErrorCode = ErrorCode.FAILURE,
            expectedCustomAttributes = mapOf(
                EmbType.Performance.Default.asPair()
            )
        )
    }

    @Test
    fun `do not add failed span from a snapshot if a span with the same id is already in the payload`() {
        messedUpSessionEnvelope.resurrectPayload()

        with(getStoredParts().single()) {
            assertEquals(3, data.spans?.size)
            assertEquals(0, data.spanSnapshots?.size)
        }
    }

    @Test
    fun `crash ID is only added to session span with matching session ID`() {
        nativeCrashService.addNativeCrashData(
            createNativeCrashData(
                nativeCrashId = "dead-session-native-crash",
                sessionId = deadSessionEnvelope.getSessionId()
            )
        )
        deadSessionEnvelope.resurrectPayload()

        val sessionSpan = getStoredParts().single().getSessionSpan()
        assertEquals("dead-session-native-crash", sessionSpan?.attributes?.findAttributeValue(embCrashId.name))

        nativeCrashService.addNativeCrashData(
            createNativeCrashData(
                nativeCrashId = "dead-session-native-crash",
                sessionId = "fake-id"
            )
        )
        deadSessionEnvelope.resurrectPayload()

        val attributes =
            checkNotNull(getStoredParts().last().getSessionSpan()?.attributes)
        assertNull(attributes.findAttributeValue(embCrashId.name))
    }

    @Test
    fun `session payload that doesn't contain session span will not be resurrected`() {
        noSessionSpanEnvelope.resurrectPayload()
        assertResurrectionFailure()
    }

    @Test
    fun `resurrection failure deletes cache file and logs unrecoverable error`() {
        cacheStorageService.addPayload(
            metadata = sessionMetadata,
            data = deadSessionEnvelope
        )
        serializer.errorOnNextOperation()
        resurrectInBackground()
        assertResurrectionFailure()
    }

    @Test
    fun `session payload that contains more than one span will not be resurrected`() {
        multipleSessionSpanEnvelope.resurrectPayload()
        assertResurrectionFailure()
    }

    @Test
    fun `multiple native crashes will be resurrected properly with the crash data sent separately`() {
        val deadSessionCrashData = createNativeCrashData(
            nativeCrashId = "native-crash-1",
            sessionId = deadSessionEnvelope.getSessionId()
        )
        nativeCrashService.addNativeCrashData(deadSessionCrashData)
        cacheStorageService.addPayload(
            metadata = sessionMetadata,
            data = deadSessionEnvelope
        )

        val oldResource = fakeEnvelopeResource.copy(appVersion = "1.4", sdkVersion = "6.13", osVersion = "10")
        val oldMetadata = fakeEnvelopeMetadata.copy(username = "old-admin")
        val earlierDeadSession = fakeIncompleteSessionEnvelope(
            sessionId = "anotherFakeSessionId",
            startMs = deadSessionEnvelope.getStartTime() - 100_000L,
            lastHeartbeatTimeMs = deadSessionEnvelope.getStartTime() - 90_000L,
            sessionProperties = mapOf("prop" to "earlier"),
            resource = oldResource,
            metadata = oldMetadata

        )
        val earlierSessionCrashData = createNativeCrashData(
            nativeCrashId = "native-crash-2",
            sessionId = earlierDeadSession.getSessionId()
        )
        val earlierDeadSessionMetadata = StoredTelemetryMetadata(
            timestamp = earlierDeadSession.getStartTime(),
            uuid = "fake-uuid",
            processIdentifier = "fakePid",
            envelopeType = SupportedEnvelopeType.SESSION,
            complete = false
        )
        nativeCrashService.addNativeCrashData(earlierSessionCrashData)
        cacheStorageService.addPayload(
            metadata = earlierDeadSessionMetadata,
            data = earlierDeadSession
        )

        resurrectInBackground()

        val sessionEnvelopes = getStoredParts()
        val sessionMetadataList = payloadStorageService.storedPayloadMetadata()
        assertEquals(2, sessionEnvelopes.size)

        with(sessionMetadataList.first()) {
            assertEquals(sessionMetadata.copy(complete = true), this)
        }
        with(sessionEnvelopes.first()) {
            assertEquals(deadSessionEnvelope.getSessionId(), getSessionId())
            assertEquals(
                "native-crash-1",
                getSessionSpan()?.attributes?.findAttributeValue(embCrashId.name)
            )
            assertEquals(
                "foreground",
                getSessionSpan()?.attributes?.findAttributeValue(embState.name)
            )
        }

        with(sessionMetadataList.last()) {
            assertEquals(earlierDeadSessionMetadata.copy(complete = true), this)
        }
        with(sessionEnvelopes.last()) {
            assertEquals(earlierDeadSession.getSessionId(), getSessionId())
            assertEquals(
                "native-crash-2",
                getSessionSpan()?.attributes?.findAttributeValue(embCrashId.name)
            )
            assertEquals(
                "foreground",
                getSessionSpan()?.attributes?.findAttributeValue(embState.name)
            )
        }

        val createdEnvelopes = cachedLogEnvelopeStore.createdEnvelopes
        assertEquals(2, createdEnvelopes.size)
        with(createdEnvelopes.first()) {
            assertEquals(fakeEnvelopeResource, resource)
            assertEquals(fakeEnvelopeMetadata, metadata)
        }
        with(createdEnvelopes.last()) {
            assertEquals(oldResource, resource)
            assertEquals(oldMetadata, metadata)
        }

        assertEquals(2, nativeCrashService.nativeCrashesSent.size)
        with(nativeCrashService.nativeCrashesSent.first()) {
            assertEquals(deadSessionCrashData, first)
            assertTrue(second.keys.none { it.isEmbraceAttributeName() })
        }
        with(nativeCrashService.nativeCrashesSent.last()) {
            assertEquals(earlierSessionCrashData, first)
            assertEquals(
                "earlier",
                second.findAttributeValue(second.keys.single { it.isEmbraceAttributeName() })
            )
        }
    }

    @Test
    fun `native crashes without sessions are sent properly`() {
        val deadSessionCrashData = createNativeCrashData(
            nativeCrashId = "native-crash-1",
            sessionId = "no-session-id"
        )
        cacheStorageService.addPayload(
            metadata = fakeCachedCrashEnvelopeMetadata,
            data = fakeEmptyLogEnvelope(
                resource = fakeLaterEnvelopeResource,
                metadata = fakeLaterEnvelopeMetadata
            )
        )
        nativeCrashService.addNativeCrashData(deadSessionCrashData)
        resurrectInBackground()

        assertEquals(0, payloadStorageService.storedPayloadCount())

        with(cachedLogEnvelopeStore.createdEnvelopes.single()) {
            assertEquals(fakeLaterEnvelopeResource, resource)
            assertEquals(fakeLaterEnvelopeMetadata, metadata)
        }

        assertEquals(1, nativeCrashService.nativeCrashesSent.size)
        with(nativeCrashService.nativeCrashesSent.first()) {
            assertEquals(deadSessionCrashData, first)
            assertTrue(second.keys.none { it.isEmbraceAttributeName() || embState.name == it })
        }
    }

    @Test
    fun `native crashes without sessions or cached crash envelopes sent`() {
        val deadSessionCrashData = createNativeCrashData(
            nativeCrashId = "native-crash-1",
            sessionId = "no-session-id"
        )
        nativeCrashService.addNativeCrashData(deadSessionCrashData)
        resurrectInBackground()

        assertEquals(0, payloadStorageService.storedPayloadCount())

        assertTrue(cachedLogEnvelopeStore.createdEnvelopes.isEmpty())
        assertEquals(1, nativeCrashService.nativeCrashesSent.size)
        with(nativeCrashService.nativeCrashesSent.first()) {
            assertEquals(deadSessionCrashData, first)
            assertTrue(second.keys.none { it.isEmbraceAttributeName() || embState.name == it })
        }
    }

    @Test
    fun `dead session is resurrected with no crashId when nativeCrashService is null`() {
        cacheStorageService.addPayload(
            metadata = sessionMetadata,
            data = deadSessionEnvelope
        )
        resurrectInBackground { null }

        val storedMetadata = payloadStorageService.storedPayloadMetadata().single()
        assertEquals(fakeCachedSessionStoredTelemetryMetadata.copy(complete = true), storedMetadata)
        assertEquals(0, cacheStorageService.storedPayloadCount())
        val sessionSpan = checkNotNull(getStoredParts().single().getSessionSpan())
        assertNull(checkNotNull(sessionSpan.attributes).findAttributeValue(embCrashId.name))
    }

    @Test
    fun `resurrection completes even if a listener throws`() {
        val latch = CountDownLatch(1)
        cacheStorageService.addPayload(sessionMetadata, deadSessionEnvelope)
        resurrectionService.addResurrectionCompleteListener {
            throw IllegalStateException()
        }
        resurrectionService.addResurrectionCompleteListener {
            latch.countDown()
        }

        resurrectInBackground()
        latch.await(1000, TimeUnit.MILLISECONDS)
        assertEquals(1, payloadStorageService.storedPayloadCount())
    }

    @Test
    fun `resurrection timeout logged when future throws timeout on get`() {
        val hangingIntakeService = object : IntakeService {
            override fun shutdown() {}
            override fun take(
                intake: Envelope<*>,
                metadata: StoredTelemetryMetadata,
                staleEntry: StoredTelemetryMetadata?
            ): Future<*> {
                return object : Future<Unit> {
                    override fun cancel(mayInterruptIfRunning: Boolean) = false
                    override fun isCancelled() = false
                    override fun isDone() = false
                    override fun get() = throw TimeoutException("test")
                    override fun get(timeout: Long, unit: TimeUnit) = throw TimeoutException("test")
                }
            }
        }
        val service = PayloadResurrectionServiceImpl(
            intakeService = hangingIntakeService,
            cacheStorageService = cacheStorageService,
            cachedLogEnvelopeStore = cachedLogEnvelopeStore,
            logger = logger,
            serializer = serializer,
        )

        cacheStorageService.addPayload(sessionMetadata, deadSessionEnvelope)

        var listenerCalled = false
        service.addResurrectionCompleteListener { listenerCalled = true }
        service.resurrectOldPayloads { nativeCrashService }

        assertTrue(listenerCalled)
        assertTrue(
            logger.internalErrorMessages.any {
                it.throwable is TimeoutException
            }
        )
    }

    /**
     * Runs resurrection on a background thread to simulate what happens in production
     */
    private fun resurrectInBackground(nativeCrashServiceProvider: () -> NativeCrashService? = { nativeCrashService }) {
        val thread = Thread { resurrectionService.resurrectOldPayloads(nativeCrashServiceProvider) }
        thread.start()
        thread.join(5000)
    }

    private fun Envelope<SessionPartPayload>.resurrectPayload() {
        cacheStorageService.addPayload(
            metadata = sessionMetadata,
            data = this
        )
        resurrectInBackground()
    }

    private fun getStoredParts(): List<Envelope<SessionPartPayload>> {
        return payloadStorageService.storedPayloads().map { bytes ->
            serializer.fromJson(
                GZIPInputStream(ByteArrayInputStream(bytes)),
                Envelope.sessionEnvelopeType
            )
        }
    }

    private fun createNativeCrashData(
        nativeCrashId: String,
        sessionId: String,
    ) = NativeCrashData(
        nativeCrashId = nativeCrashId,
        sessionId = sessionId,
        timestamp = 0L,
        crash = null,
        symbols = null,
    )

    private fun assertResurrectionFailure() {
        assertEquals(0, payloadStorageService.storedPayloadCount())
        assertEquals(0, cacheStorageService.storedPayloadCount())
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals(
            InternalErrorType.PAYLOAD_RESURRECTION_PAYLOAD_FAIL.toString(),
            logger.internalErrorMessages.single().msg
        )
    }

    private companion object {
        private val startedSnapshot = perfSpanSnapshot.toEmbraceSpanData()
        val sessionMetadata = fakeCachedSessionStoredTelemetryMetadata
        val deadSessionEnvelope = fakeIncompleteSessionEnvelope(
            startMs = sessionMetadata.timestamp,
            lastHeartbeatTimeMs = sessionMetadata.timestamp + 1000L
        )
        val messedUpSessionEnvelope = with(deadSessionEnvelope) {
            copy(
                data = data.copy(
                    spans = listOf(
                        startedSnapshot.copy(endTimeNanos = startedSnapshot.startTimeNanos + 10000000L).toEmbracePayload()
                    ),
                    spanSnapshots = data.spanSnapshots?.plus(listOfNotNull(startedSnapshot).map(EmbraceSpanData::toEmbracePayload))
                )
            )
        }
        val noSessionSpanEnvelope = deadSessionEnvelope.copy(
            data = deadSessionEnvelope.data.copy(
                spanSnapshots = emptyList()
            )
        )
        val multipleSessionSpanEnvelope = deadSessionEnvelope.copy(
            data = deadSessionEnvelope.data.copy(
                spanSnapshots = deadSessionEnvelope.data.spanSnapshots?.plus(
                    checkNotNull(
                        FakeEmbraceSdkSpan.sessionSpan(
                            sessionId = "fake-session-span-id",
                            startTimeMs = deadSessionEnvelope.getStartTime() + 1001L,
                            lastHeartbeatTimeMs = deadSessionEnvelope.getStartTime() + 1001L,
                        ).snapshot()
                    )
                )
            )
        )
        val fakeCachedCrashEnvelopeMetadata = StoredTelemetryMetadata(
            timestamp = 1000L,
            uuid = "old-session-id",
            processIdentifier = "old-process-id",
            envelopeType = CRASH,
            complete = false,
            payloadType = PayloadType.UNKNOWN
        )
    }
}
