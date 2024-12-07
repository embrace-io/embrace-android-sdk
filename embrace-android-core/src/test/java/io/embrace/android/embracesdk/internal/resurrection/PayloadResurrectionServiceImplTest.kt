package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.assertions.findAttributeValue
import io.embrace.android.embracesdk.assertions.findSpansByName
import io.embrace.android.embracesdk.assertions.getLastHeartbeatTimeMs
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.assertions.getStartTime
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeIntakeService
import io.embrace.android.embracesdk.fakes.FakeNativeCrashService
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakePersistableEmbraceSpan
import io.embrace.android.embracesdk.fakes.FakeSpanData.Companion.perfSpanSnapshot
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.fakeEmptyLogEnvelope
import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeNativeCrashStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.isSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.opentelemetry.embCrashId
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.spans.toEmbraceSpanData
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.SpanId
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PayloadResurrectionServiceImplTest {

    private lateinit var intakeService: FakeIntakeService
    private lateinit var cacheStorageService: FakePayloadStorageService
    private lateinit var nativeCrashService: FakeNativeCrashService
    private lateinit var logger: FakeEmbLogger
    private lateinit var serializer: TestPlatformSerializer
    private lateinit var resurrectionService: PayloadResurrectionServiceImpl

    @Before
    fun setUp() {
        intakeService = FakeIntakeService()
        cacheStorageService = FakePayloadStorageService()
        nativeCrashService = FakeNativeCrashService()
        logger = FakeEmbLogger(false)
        serializer = TestPlatformSerializer()
        resurrectionService = PayloadResurrectionServiceImpl(
            intakeService = intakeService,
            cacheStorageService = cacheStorageService,
            logger = logger,
            serializer = serializer,
        )
    }

    @Test
    fun `if no previous cached session then send previous cached sessions should not send anything`() {
        resurrectionService.resurrectOldPayloads({ nativeCrashService })
        assertTrue(intakeService.intakeList.isEmpty())
    }

    @Test
    fun `dead session resurrected and delivered`() {
        deadSessionEnvelope.resurrectPayload()
        val intake = intakeService.getIntakes<SessionPayload>().single()
        assertEquals(intake.metadata, fakeCachedSessionStoredTelemetryMetadata.copy(complete = true))
        assertEquals(0, cacheStorageService.storedPayloadCount())

        val sessionSpan = checkNotNull(intake.envelope.getSessionSpan())
        val expectedStartTimeMs = deadSessionEnvelope.getStartTime()
        val expectedEndTimeMs = deadSessionEnvelope.getLastHeartbeatTimeMs()

        assertEmbraceSpanData(
            span = sessionSpan,
            expectedStartTimeMs = expectedStartTimeMs,
            expectedEndTimeMs = expectedEndTimeMs,
            expectedParentId = SpanId.getInvalid(),
            expectedErrorCode = ErrorCode.FAILURE,
            expectedCustomAttributes = mapOf(
                EmbType.Ux.Session.toEmbraceKeyValuePair()
            )
        )
    }

    @Test
    fun `all payloads from previous app launches are deleted after resurrection`() {
        cacheStorageService.addPayload(
            metadata = fakeNativeCrashStoredTelemetryMetadata,
            data = fakeEmptyLogEnvelope()
        )
        assertEquals(1, cacheStorageService.storedPayloadCount())
        assertEquals(0, cacheStorageService.deleteCount.get())
        resurrectionService.resurrectOldPayloads({ nativeCrashService })

        assertTrue(intakeService.getIntakes<SessionPayload>().isEmpty())
        assertEquals(1, cacheStorageService.deleteCount.get())
    }

    @Test
    fun `snapshot will be delivered as failed span once resurrected`() {
        deadSessionEnvelope.resurrectPayload()

        val sentSession = intakeService.getIntakes<SessionPayload>().single().envelope
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
            expectedParentId = SpanId.getInvalid(),
            expectedErrorCode = ErrorCode.FAILURE,
            expectedCustomAttributes = mapOf(
                EmbType.Performance.Default.toEmbraceKeyValuePair()
            )
        )
    }

    @Test
    fun `do not add failed span from a snapshot if a span with the same id is already in the payload`() {
        messedUpSessionEnvelope.resurrectPayload()

        with(intakeService.getIntakes<SessionPayload>().single().envelope) {
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

        val sessionSpan = intakeService.getIntakes<SessionPayload>().single().envelope.getSessionSpan()
        assertEquals("dead-session-native-crash", sessionSpan?.attributes?.findAttributeValue(embCrashId.name))

        nativeCrashService.addNativeCrashData(
            createNativeCrashData(
                nativeCrashId = "dead-session-native-crash",
                sessionId = "fake-id"
            )
        )
        deadSessionEnvelope.resurrectPayload()

        val attributes =
            checkNotNull(intakeService.getIntakes<SessionPayload>().last().envelope.getSessionSpan()?.attributes)
        assertNull(attributes.findAttributeValue(embCrashId.name))
    }

    @Test
    fun `session payload that doesn't contain session span will not be resurrected`() {
        noSessionSpanEnvelope.resurrectPayload()
        assertResurrectionFailure()
    }

    @Test
    fun `resurrection failure leaves payload cached`() {
        cacheStorageService.addPayload(
            metadata = sessionMetadata,
            data = deadSessionEnvelope
        )
        serializer.errorOnNextOperation()
        resurrectionService.resurrectOldPayloads({ nativeCrashService })
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

        val earlierDeadSession = fakeIncompleteSessionEnvelope(
            sessionId = "anotherFakeSessionId",
            startMs = deadSessionEnvelope.getStartTime() - 100_000L,
            lastHeartbeatTimeMs = deadSessionEnvelope.getStartTime() - 90_000L,
            sessionProperties = mapOf("prop" to "earlier")

        )
        val earlierSessionCrashData = createNativeCrashData(
            nativeCrashId = "native-crash-2",
            sessionId = earlierDeadSession.getSessionId()
        )
        val earlierDeadSessionMetadata = StoredTelemetryMetadata(
            timestamp = earlierDeadSession.getStartTime(),
            uuid = "fake-uuid",
            processId = "fakePid",
            envelopeType = SupportedEnvelopeType.SESSION,
            complete = false
        )
        nativeCrashService.addNativeCrashData(earlierSessionCrashData)
        cacheStorageService.addPayload(
            metadata = earlierDeadSessionMetadata,
            data = earlierDeadSession
        )

        resurrectionService.resurrectOldPayloads({ nativeCrashService })

        val sessionPayloads = intakeService.getIntakes<SessionPayload>()
        assertEquals(2, sessionPayloads.size)
        with(sessionPayloads.first()) {
            assertEquals(sessionMetadata.copy(complete = true), metadata)
            assertEquals(deadSessionEnvelope.getSessionId(), envelope.getSessionId())
            assertEquals(
                "native-crash-1",
                envelope.getSessionSpan()?.attributes?.findAttributeValue(embCrashId.name)
            )
        }

        with(sessionPayloads.last()) {
            assertEquals(earlierDeadSessionMetadata.copy(complete = true), metadata)
            assertEquals(earlierDeadSession.getSessionId(), envelope.getSessionId())
            assertEquals(
                "native-crash-2",
                envelope.getSessionSpan()?.attributes?.findAttributeValue(embCrashId.name)
            )
        }

        assertEquals(2, nativeCrashService.nativeCrashesSent.size)
        with(nativeCrashService.nativeCrashesSent.first()) {
            assertEquals(deadSessionCrashData, first)
            assertTrue(second.keys.none { it.isSessionPropertyAttributeName() })
        }
        with(nativeCrashService.nativeCrashesSent.last()) {
            assertEquals(earlierSessionCrashData, first)
            assertEquals(
                "earlier",
                second.findAttributeValue(second.keys.single { it.isSessionPropertyAttributeName() })
            )
        }
    }

    @Test
    fun `native crashes without sessions are sent properly`() {
        val deadSessionCrashData = createNativeCrashData(
            nativeCrashId = "native-crash-1",
            sessionId = "no-session-id"
        )
        nativeCrashService.addNativeCrashData(deadSessionCrashData)
        resurrectionService.resurrectOldPayloads({ nativeCrashService })

        assertEquals(0, intakeService.getIntakes<SessionPayload>().size)
        assertEquals(1, nativeCrashService.nativeCrashesSent.size)
        with(nativeCrashService.nativeCrashesSent.first()) {
            assertEquals(deadSessionCrashData, first)
            assertTrue(second.keys.none { it.isSessionPropertyAttributeName() })
        }
    }

    private fun Envelope<SessionPayload>.resurrectPayload() {
        cacheStorageService.addPayload(
            metadata = sessionMetadata,
            data = this
        )
        resurrectionService.resurrectOldPayloads({ nativeCrashService })
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
        assertTrue(intakeService.intakeList.isEmpty())
        assertEquals(1, cacheStorageService.storedPayloadCount())
        assertEquals(1, logger.internalErrorMessages.size)
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
                        startedSnapshot.copy(endTimeNanos = startedSnapshot.startTimeNanos + 10000000L).toNewPayload()
                    ),
                    spanSnapshots = data.spanSnapshots?.plus(listOfNotNull(startedSnapshot).map(EmbraceSpanData::toNewPayload))
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
                        FakePersistableEmbraceSpan.sessionSpan(
                            sessionId = "fake-session-span-id",
                            startTimeMs = deadSessionEnvelope.getStartTime() + 1001L,
                            lastHeartbeatTimeMs = deadSessionEnvelope.getStartTime() + 1001L,
                        ).snapshot()
                    )
                )
            )
        )
    }
}
