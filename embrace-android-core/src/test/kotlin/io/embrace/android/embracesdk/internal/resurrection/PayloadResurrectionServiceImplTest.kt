package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.assertions.findAttributeValue
import io.embrace.android.embracesdk.assertions.findSpansByName
import io.embrace.android.embracesdk.assertions.getLastHeartbeatTimeMs
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.assertions.getStartTime
import io.embrace.android.embracesdk.assertions.getUserSessionId
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
import io.embrace.android.embracesdk.fixtures.FAKE_SESSION_PART_ID
import io.embrace.android.embracesdk.fixtures.FAKE_SESSION_PART_ID_2
import io.embrace.android.embracesdk.fixtures.FAKE_USER_SESSION_ID
import io.embrace.android.embracesdk.fixtures.FAKE_USER_SESSION_ID_2
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeSessionStoredTelemetryMetadata2
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.attrs.isEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.CRASH
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeServiceImpl
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.UserSessionRestoreDecision
import io.embrace.android.embracesdk.internal.session.getSessionPartSpan
import io.embrace.android.embracesdk.internal.toEmbraceSpanData
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
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
            payloadStorageService = payloadStorageService,
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
        val sessionPartSpan = checkNotNull(sessionEnvelope.getSessionPartSpan())
        val expectedStartTimeMs = deadSessionEnvelope.getStartTime()
        val expectedEndTimeMs = deadSessionEnvelope.getLastHeartbeatTimeMs()

        assertEmbraceSpanData(
            span = sessionPartSpan,
            expectedStartTimeMs = expectedStartTimeMs,
            expectedEndTimeMs = expectedEndTimeMs,
            expectedParentId = OtelIds.INVALID_SPAN_ID,
            expectedErrorCode = ErrorCode.FAILURE,
            expectedCustomAttributes = mapOf(
                EmbType.Ux.Session.asPair(),
                AppTerminationCause.Crash.asPair(),
            ),
        )
    }

    @Test
    fun `all payloads from previous app launches are deleted after resurrection`() {
        cacheStorageService.addPayload(
            metadata = fakeCachedCrashEnvelopeMetadata,
            data = fakeEmptyLogEnvelope(),
        )
        assertEquals(1, cacheStorageService.storedPayloadCount())
        assertEquals(0, cacheStorageService.deleteCount.get())
        resurrectInBackground()

        assertEquals(0, payloadStorageService.storedPayloadCount())
        assertEquals(1, cacheStorageService.deleteCount.get())
    }

    @Test
    fun `cached session is skipped if payloadStorageService has payload with same session id`() {
        // simulate a race where a complete session payload _and_ cached session payload are both on disk
        payloadStorageService.addPayload(
            metadata = fakeSessionStoredTelemetryMetadata,
            data = deadSessionEnvelope,
        )
        cacheStorageService.addPayload(
            metadata = sessionMetadata,
            data = deadSessionEnvelope,
        )
        assertEquals(1, payloadStorageService.storedPayloadCount())
        assertEquals(1, cacheStorageService.storedPayloadCount())

        resurrectInBackground()

        val stored = payloadStorageService.storedPayloadMetadata().single()
        assertEquals(fakeSessionStoredTelemetryMetadata, stored)
        assertEquals(0, cacheStorageService.storedPayloadCount())
    }

    @Test
    fun `duplicate session snapshots only send the most recent one`() {
        val earlierMeta = sessionMetadata.copy(
            timestamp = sessionMetadata.timestamp - 5_000L,
            uuid = "earlier-uuid",
        )
        val earlierEnvelope = fakeIncompleteSessionEnvelope(
            startMs = earlierMeta.timestamp,
            lastHeartbeatTimeMs = earlierMeta.timestamp + 100L,
        )
        cacheStorageService.addPayload(metadata = earlierMeta, data = earlierEnvelope)
        cacheStorageService.addPayload(metadata = sessionMetadata, data = deadSessionEnvelope)

        resurrectInBackground()

        val stored = payloadStorageService.storedPayloadMetadata().single()
        assertEquals(sessionMetadata.copy(complete = true), stored)
        assertEquals(0, cacheStorageService.storedPayloadCount())
    }

    @Test
    fun `legacy session snapshot with empty session IDs bypasses dedup and is resurrected`() {
        // simulate a payload that uses v1 filename encoding
        val legacyMeta = sessionMetadata.copy(userSessionId = "", sessionPartId = "")
        cacheStorageService.addPayload(metadata = legacyMeta, data = deadSessionEnvelope)

        resurrectInBackground()

        val stored = payloadStorageService.storedPayloadMetadata().single()
        assertEquals(legacyMeta.copy(complete = true), stored)
        assertEquals(0, cacheStorageService.storedPayloadCount())
    }

    @Test
    fun `dedupe does not affect unrelated sessions`() {
        payloadStorageService.addPayload(
            metadata = fakeSessionStoredTelemetryMetadata,
            data = deadSessionEnvelope,
        )
        cacheStorageService.addPayload(
            metadata = sessionMetadata,
            data = deadSessionEnvelope,
        )

        // Session B is only in the cache — should be resurrected normally.
        val sessionBMeta = fakeSessionStoredTelemetryMetadata2.copy(complete = false)
        val sessionBEnvelope = fakeIncompleteSessionEnvelope(
            userSessionId = "session-b",
            startMs = sessionBMeta.timestamp,
            lastHeartbeatTimeMs = sessionBMeta.timestamp + 1000L,
        )
        cacheStorageService.addPayload(metadata = sessionBMeta, data = sessionBEnvelope)

        resurrectInBackground()

        val storedKeys = payloadStorageService.storedPayloadMetadata()
            .map { it.userSessionId to it.sessionPartId }
            .toSet()
        assertEquals(
            setOf(
                FAKE_USER_SESSION_ID to FAKE_SESSION_PART_ID,
                FAKE_USER_SESSION_ID_2 to FAKE_SESSION_PART_ID_2,
            ),
            storedKeys,
        )
        assertEquals(2, payloadStorageService.storedPayloadCount())
        assertEquals(0, cacheStorageService.storedPayloadCount())
    }

    @Test
    fun `snapshot will be delivered as failed span once resurrected`() {
        deadSessionEnvelope.resurrectPayload()

        val sentSession = getStoredParts().single()
        assertEquals(2, sentSession.data.spans?.size)
        assertEquals(0, sentSession.data.spanSnapshots?.size)

        val sessionPartSpan = checkNotNull(sentSession.getSessionPartSpan())
        val spanSnapshot =
            checkNotNull(
                deadSessionEnvelope.data.spanSnapshots?.filterNot { it.spanId == sessionPartSpan.spanId }
                    ?.single(),
            )
        val resurrectedSnapshot = sentSession.findSpansByName(checkNotNull(spanSnapshot.name)).single()

        assertEmbraceSpanData(
            span = resurrectedSnapshot,
            expectedStartTimeMs = checkNotNull(spanSnapshot.startTimeNanos?.nanosToMillis()),
            expectedEndTimeMs = checkNotNull(sessionPartSpan.endTimeNanos?.nanosToMillis()),
            expectedParentId = OtelIds.INVALID_SPAN_ID,
            expectedErrorCode = ErrorCode.FAILURE,
            expectedCustomAttributes = mapOf(
                EmbType.Performance.Default.asPair(),
            ),
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
    fun `crash ID is only added to session part span with matching session ID`() {
        nativeCrashService.addNativeCrashData(
            createNativeCrashData(
                nativeCrashId = "dead-session-native-crash",
                sessionPartId = deadSessionEnvelope.getSessionPartId(),
            ),
        )
        deadSessionEnvelope.resurrectPayload()

        val sessionPartSpan = getStoredParts().single().getSessionPartSpan()
        assertEquals("dead-session-native-crash", sessionPartSpan?.attributes?.findAttributeValue(EmbSessionAttributes.EMB_CRASH_ID))

        // clear snapshots before running second scenario
        payloadStorageService.clearStorage()
        nativeCrashService.addNativeCrashData(
            createNativeCrashData(
                nativeCrashId = "dead-session-native-crash",
                sessionPartId = "fake-id",
            ),
        )
        deadSessionEnvelope.resurrectPayload()

        val attributes =
            checkNotNull(getStoredParts().last().getSessionPartSpan()?.attributes)
        assertNull(attributes.findAttributeValue(EmbSessionAttributes.EMB_CRASH_ID))
    }

    @Test
    fun `native crash attaches to a legacy session part that carries only session id`() {
        val legacySessionId = "legacy-session-id"
        nativeCrashService.addNativeCrashData(
            createNativeCrashData(
                nativeCrashId = "legacy-native-crash",
                sessionPartId = legacySessionId,
            ),
        )
        cacheStorageService.addPayload(
            metadata = sessionMetadata.copy(userSessionId = "", sessionPartId = ""),
            data = deadSessionEnvelope.asPreUserSessionPayload(legacySessionId),
        )

        resurrectInBackground()

        val sessionPartSpan = getStoredParts().single().getSessionPartSpan()
        assertEquals(
            "legacy-native-crash",
            sessionPartSpan?.attributes?.findAttributeValue(EmbSessionAttributes.EMB_CRASH_ID),
        )
    }

    @Test
    fun `final session part marker and termination reason stamped on a resurrected snapshot whose user session ended on restore`() {
        cacheStorageService.addPayload(metadata = sessionMetadata, data = deadSessionEnvelope)
        resurrectInBackground(
            restoreDecision = UserSessionRestoreDecision.Terminated(
                userSessionId = FAKE_USER_SESSION_ID,
                backgroundOnly = false,
                reason = EmbUserSessionTerminationReasonValues.INACTIVITY,
            ),
        )

        val attributes = checkNotNull(getStoredParts().single().getSessionPartSpan()?.attributes)
        assertEquals("1", attributes.findAttributeValue(EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART))
        assertEquals(
            EmbUserSessionTerminationReasonValues.INACTIVITY,
            attributes.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON),
        )
    }

    @Test
    fun `final session part marker and termination reason not stamped when no user session ended on restore`() {
        cacheStorageService.addPayload(metadata = sessionMetadata, data = deadSessionEnvelope)
        resurrectInBackground()

        val attributes = checkNotNull(getStoredParts().single().getSessionPartSpan()?.attributes)
        assertNull(attributes.findAttributeValue(EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART))
        assertNull(attributes.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON))
    }

    @Test
    fun `final session part marker and termination reason not stamped when the ended user session id does not match`() {
        cacheStorageService.addPayload(metadata = sessionMetadata, data = deadSessionEnvelope)
        resurrectInBackground(
            restoreDecision = UserSessionRestoreDecision.Terminated(
                userSessionId = FAKE_USER_SESSION_ID_2,
                backgroundOnly = false,
                reason = EmbUserSessionTerminationReasonValues.MAX_DURATION_REACHED,
            ),
        )

        val attributes = checkNotNull(getStoredParts().single().getSessionPartSpan()?.attributes)
        assertNull(attributes.findAttributeValue(EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART))
        assertNull(attributes.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON))
    }

    @Test
    fun `only the last session part of a terminated user session is marked`() {
        val earlierMeta = sessionMetadata.copy(
            uuid = sessionMetadata.uuid + "-earlier",
            sessionPartId = sessionMetadata.sessionPartId + "-earlier",
            timestamp = sessionMetadata.timestamp,
        )
        val laterMeta = sessionMetadata.copy(
            uuid = sessionMetadata.uuid + "-later",
            sessionPartId = sessionMetadata.sessionPartId + "-later",
            timestamp = sessionMetadata.timestamp + 1000L,
        )
        cacheStorageService.addPayload(
            metadata = earlierMeta,
            data = fakeIncompleteSessionEnvelope(userSessionId = "earlier-part"),
        )
        cacheStorageService.addPayload(
            metadata = laterMeta,
            data = fakeIncompleteSessionEnvelope(userSessionId = "later-part"),
        )
        resurrectInBackground(
            restoreDecision = UserSessionRestoreDecision.Terminated(
                userSessionId = FAKE_USER_SESSION_ID,
                backgroundOnly = false,
                reason = EmbUserSessionTerminationReasonValues.MAX_DURATION_REACHED,
            ),
        )

        val parts = getStoredParts().associateBy { it.getUserSessionId() }
        val laterAttrs = checkNotNull(parts.getValue("later-part").getSessionPartSpan()?.attributes)
        assertEquals("1", laterAttrs.findAttributeValue(EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART))
        assertEquals(
            EmbUserSessionTerminationReasonValues.MAX_DURATION_REACHED,
            laterAttrs.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON),
        )

        val earlierAttrs = checkNotNull(parts.getValue("earlier-part").getSessionPartSpan()?.attributes)
        assertNull(earlierAttrs.findAttributeValue(EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART))
        assertNull(earlierAttrs.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON))
    }

    @Test
    fun `background-only marker and termination reason stamped on a resurrected part of a terminated background-only session`() {
        cacheStorageService.addPayload(metadata = sessionMetadata, data = deadSessionEnvelope)
        resurrectInBackground(
            restoreDecision = UserSessionRestoreDecision.Terminated(
                userSessionId = FAKE_USER_SESSION_ID,
                backgroundOnly = true,
                reason = EmbUserSessionTerminationReasonValues.MAX_DURATION_REACHED,
            ),
        )

        val attributes = checkNotNull(getStoredParts().single().getSessionPartSpan()?.attributes)
        assertEquals("1", attributes.findAttributeValue(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART))
    }

    @Test
    fun `background-only marker stamped on a resurrected part of a restored background-only session`() {
        cacheStorageService.addPayload(metadata = sessionMetadata, data = deadSessionEnvelope)
        resurrectInBackground(
            restoreDecision = UserSessionRestoreDecision.Restored(
                userSessionId = FAKE_USER_SESSION_ID,
                backgroundOnly = true,
            ),
        )

        val attributes = checkNotNull(getStoredParts().single().getSessionPartSpan()?.attributes)
        assertEquals("1", attributes.findAttributeValue(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART))
        assertNull(attributes.findAttributeValue(EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART))
    }

    @Test
    fun `background-only marker not stamped when the restored session is not background-only`() {
        cacheStorageService.addPayload(metadata = sessionMetadata, data = deadSessionEnvelope)
        resurrectInBackground(
            restoreDecision = UserSessionRestoreDecision.Restored(
                userSessionId = FAKE_USER_SESSION_ID,
                backgroundOnly = false,
            ),
        )

        val attributes = checkNotNull(getStoredParts().single().getSessionPartSpan()?.attributes)
        assertNull(attributes.findAttributeValue(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART))
    }

    @Test
    fun `background-only marker not stamped on a part that does not match the terminated session ID`() {
        cacheStorageService.addPayload(metadata = sessionMetadata, data = deadSessionEnvelope)
        resurrectInBackground(
            restoreDecision = UserSessionRestoreDecision.Terminated(
                userSessionId = FAKE_USER_SESSION_ID_2,
                backgroundOnly = true,
                reason = EmbUserSessionTerminationReasonValues.MAX_DURATION_REACHED,
            ),
        )

        val attributes = checkNotNull(getStoredParts().single().getSessionPartSpan()?.attributes)
        assertNull(attributes.findAttributeValue(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART))
    }

    @Test
    fun `session payload that doesn't contain session part span will not be resurrected`() {
        noSessionPartSpanEnvelope.resurrectPayload()
        assertResurrectionFailure()
    }

    @Test
    fun `resurrection failure deletes cache file and logs unrecoverable error`() {
        cacheStorageService.addPayload(
            metadata = sessionMetadata,
            data = deadSessionEnvelope,
        )
        serializer.errorOnNextOperation()
        resurrectInBackground()
        assertResurrectionFailure()
    }

    @Test
    fun `session payload skipped without error when cache returns null stream`() {
        cacheStorageService.addFakePayload(sessionMetadata)
        val service = serviceWithPayloadStream(null)

        service.resurrectOldPayloads(nativeCrashServiceProvider = { nativeCrashService })

        assertEquals(0, payloadStorageService.storedPayloadCount())
        assertEquals(0, cacheStorageService.storedPayloadCount())
        assertEquals(1, cacheStorageService.deleteCount.get())
        assertTrue(logger.internalErrorMessages.isEmpty())
    }

    @Test
    fun `session payload skipped without error when gzip stream is invalid`() {
        cacheStorageService.addFakePayload(sessionMetadata)
        val service = serviceWithPayloadStream("hi there".byteInputStream())

        service.resurrectOldPayloads(nativeCrashServiceProvider = { nativeCrashService })

        assertEquals(0, payloadStorageService.storedPayloadCount())
        assertEquals(0, cacheStorageService.storedPayloadCount())
        assertEquals(1, cacheStorageService.deleteCount.get())
        assertTrue(logger.internalErrorMessages.isEmpty())
    }

    @Test
    fun `sessionless native crash sent without envelope data when crash envelope stream returns null`() {
        val deadSessionCrashData = createNativeCrashData(
            nativeCrashId = "native-crash-1",
            sessionPartId = "no-session-id",
        )
        cacheStorageService.addPayload(
            metadata = fakeCachedCrashEnvelopeMetadata,
            data = fakeEmptyLogEnvelope(),
        )
        nativeCrashService.addNativeCrashData(deadSessionCrashData)

        val service = serviceWithPayloadStream(null)
        service.resurrectOldPayloads(nativeCrashServiceProvider = { nativeCrashService })

        assertEquals(0, payloadStorageService.storedPayloadCount())
        assertEquals(0, cacheStorageService.storedPayloadCount())
        assertTrue(cachedLogEnvelopeStore.createdEnvelopes.isEmpty())

        assertEquals(1, nativeCrashService.nativeCrashesSent.size)
        with(nativeCrashService.nativeCrashesSent.first()) {
            assertEquals(deadSessionCrashData, first)
        }

        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals(
            InternalErrorType.NativeCrashResurrectionError.toString(),
            logger.internalErrorMessages.single().msg,
        )
    }

    @Test
    fun `session payload that contains more than one span will not be resurrected`() {
        multipleSessionPartSpanEnvelope.resurrectPayload()
        assertResurrectionFailure()
    }

    @Test
    fun `multiple native crashes will be resurrected properly with the crash data sent separately`() {
        val deadSessionCrashData = createNativeCrashData(
            nativeCrashId = "native-crash-1",
            sessionPartId = deadSessionEnvelope.getSessionPartId(),
        )
        nativeCrashService.addNativeCrashData(deadSessionCrashData)
        cacheStorageService.addPayload(
            metadata = sessionMetadata,
            data = deadSessionEnvelope,
        )

        val oldResource = fakeEnvelopeResource.copy(appVersion = "1.4", sdkVersion = "6.13", osVersion = "10")
        val oldMetadata = fakeEnvelopeMetadata.copy(username = "old-admin")
        val earlierDeadSession = fakeIncompleteSessionEnvelope(
            userSessionId = "anotherFakeSessionId",
            sessionPartId = "anotherFakeSessionPartId",
            startMs = deadSessionEnvelope.getStartTime() - 100_000L,
            lastHeartbeatTimeMs = deadSessionEnvelope.getStartTime() - 90_000L,
            sessionProperties = mapOf("prop" to "earlier"),
            resource = oldResource,
            metadata = oldMetadata,

        )
        val earlierSessionCrashData = createNativeCrashData(
            nativeCrashId = "native-crash-2",
            sessionPartId = earlierDeadSession.getSessionPartId(),
        )
        val earlierDeadSessionMetadata = StoredTelemetryMetadata(
            timestamp = earlierDeadSession.getStartTime(),
            uuid = "fake-uuid",
            processIdentifier = "fakePid",
            envelopeType = SupportedEnvelopeType.SESSION,
            complete = false,
        )
        nativeCrashService.addNativeCrashData(earlierSessionCrashData)
        cacheStorageService.addPayload(
            metadata = earlierDeadSessionMetadata,
            data = earlierDeadSession,
        )

        resurrectInBackground()

        val sessionEnvelopes = getStoredParts()
        val sessionMetadataList = payloadStorageService.storedPayloadMetadata()
        assertEquals(2, sessionEnvelopes.size)

        with(sessionMetadataList.first()) {
            assertEquals(sessionMetadata.copy(complete = true), this)
        }
        with(sessionEnvelopes.first()) {
            assertEquals(deadSessionEnvelope.getUserSessionId(), getUserSessionId())
            assertEquals(
                "native-crash-1",
                getSessionPartSpan()?.attributes?.findAttributeValue(EmbSessionAttributes.EMB_CRASH_ID),
            )
            assertEquals(
                "foreground",
                getSessionPartSpan()?.attributes?.findAttributeValue(EmbSessionAttributes.EMB_STATE),
            )
        }

        with(sessionMetadataList.last()) {
            assertEquals(earlierDeadSessionMetadata.copy(complete = true), this)
        }
        with(sessionEnvelopes.last()) {
            assertEquals(earlierDeadSession.getUserSessionId(), getUserSessionId())
            assertEquals(
                "native-crash-2",
                getSessionPartSpan()?.attributes?.findAttributeValue(EmbSessionAttributes.EMB_CRASH_ID),
            )
            assertEquals(
                "foreground",
                getSessionPartSpan()?.attributes?.findAttributeValue(EmbSessionAttributes.EMB_STATE),
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
                second.findAttributeValue(second.keys.single { it.isEmbraceAttributeName() }),
            )
        }
    }

    @Test
    fun `native crashes without sessions are sent properly`() {
        val deadSessionCrashData = createNativeCrashData(
            nativeCrashId = "native-crash-1",
            sessionPartId = "no-session-id",
        )
        cacheStorageService.addPayload(
            metadata = fakeCachedCrashEnvelopeMetadata,
            data = fakeEmptyLogEnvelope(
                resource = fakeLaterEnvelopeResource,
                metadata = fakeLaterEnvelopeMetadata,
            ),
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
            assertTrue(second.keys.none { it.isEmbraceAttributeName() || EmbSessionAttributes.EMB_STATE == it })
        }
    }

    @Test
    fun `native crashes without sessions or cached crash envelopes sent`() {
        val deadSessionCrashData = createNativeCrashData(
            nativeCrashId = "native-crash-1",
            sessionPartId = "no-session-id",
        )
        nativeCrashService.addNativeCrashData(deadSessionCrashData)
        resurrectInBackground()

        assertEquals(0, payloadStorageService.storedPayloadCount())

        assertTrue(cachedLogEnvelopeStore.createdEnvelopes.isEmpty())
        assertEquals(1, nativeCrashService.nativeCrashesSent.size)
        with(nativeCrashService.nativeCrashesSent.first()) {
            assertEquals(deadSessionCrashData, first)
            assertTrue(second.keys.none { it.isEmbraceAttributeName() || EmbSessionAttributes.EMB_STATE == it })
        }
    }

    @Test
    fun `dead session is resurrected with no crashId when nativeCrashService is null`() {
        cacheStorageService.addPayload(
            metadata = sessionMetadata,
            data = deadSessionEnvelope,
        )
        resurrectInBackground({ null })

        val storedMetadata = payloadStorageService.storedPayloadMetadata().single()
        assertEquals(fakeCachedSessionStoredTelemetryMetadata.copy(complete = true), storedMetadata)
        assertEquals(0, cacheStorageService.storedPayloadCount())
        val sessionPartSpan = checkNotNull(getStoredParts().single().getSessionPartSpan())
        assertNull(checkNotNull(sessionPartSpan.attributes).findAttributeValue(EmbSessionAttributes.EMB_CRASH_ID))
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
                staleEntry: StoredTelemetryMetadata?,
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
            payloadStorageService = payloadStorageService,
            cacheStorageService = cacheStorageService,
            cachedLogEnvelopeStore = cachedLogEnvelopeStore,
            logger = logger,
            serializer = serializer,
        )

        cacheStorageService.addPayload(sessionMetadata, deadSessionEnvelope)

        var listenerCalled = false
        service.addResurrectionCompleteListener { listenerCalled = true }
        service.resurrectOldPayloads(nativeCrashServiceProvider = { nativeCrashService })

        assertTrue(listenerCalled)
        assertTrue(
            logger.internalErrorMessages.any {
                it.throwable is TimeoutException
            },
        )
    }

    /**
     * Runs resurrection on a background thread to simulate what happens in production
     */
    private fun resurrectInBackground(
        nativeCrashServiceProvider: () -> NativeCrashService? = { nativeCrashService },
        restoreDecision: UserSessionRestoreDecision? = null,
    ) {
        val thread = Thread {
            resurrectionService.resurrectOldPayloads(nativeCrashServiceProvider, { restoreDecision })
        }
        thread.start()
        thread.join(5000)
    }

    private fun Envelope<SessionPartPayload>.resurrectPayload() {
        cacheStorageService.addPayload(
            metadata = sessionMetadata,
            data = this,
        )
        resurrectInBackground()
    }

    /**
     * Rewrites a session payload to look like one from an SDK that predates the user session concept
     */
    private fun Envelope<SessionPartPayload>.asPreUserSessionPayload(
        legacySessionId: String,
    ): Envelope<SessionPartPayload> {
        return copy(
            data = data.copy(
                spans = data.spans.toPreUserSessionSpan(legacySessionId),
                spanSnapshots = data.spanSnapshots.toPreUserSessionSpan(legacySessionId),
            ),
        )
    }

    private fun List<Span>?.toPreUserSessionSpan(legacySessionId: String): List<Span>? =
        this?.map { span ->
            span.copy(
                attributes = checkNotNull(span.attributes)
                    .filterNot { it.key == EmbSessionAttributes.EMB_SESSION_PART_ID || it.key == EmbSessionAttributes.EMB_USER_SESSION_ID }
                    .map {
                        if (it.key == SessionAttributes.SESSION_ID) {
                            it.copy(data = legacySessionId)
                        } else {
                            it
                        }
                    },
            )
        }

    private fun getStoredParts(): List<Envelope<SessionPartPayload>> {
        return payloadStorageService.storedPayloads().map { bytes ->
            serializer.fromJson(
                GZIPInputStream(ByteArrayInputStream(bytes)),
                Envelope.sessionEnvelopeSerializer,
            )
        }
    }

    private fun serviceWithPayloadStream(payloadStream: InputStream?): PayloadResurrectionServiceImpl {
        val nullStreamCacheStorage = object : PayloadStorageService by cacheStorageService {
            override fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream? = payloadStream
        }
        return PayloadResurrectionServiceImpl(
            intakeService = IntakeServiceImpl(
                schedulingService,
                payloadStorageService,
                nullStreamCacheStorage,
                logger,
                serializer,
                PriorityWorker(intakeExecutor),
            ),
            payloadStorageService = payloadStorageService,
            cacheStorageService = nullStreamCacheStorage,
            cachedLogEnvelopeStore = cachedLogEnvelopeStore,
            logger = logger,
            serializer = serializer,
        )
    }

    private fun createNativeCrashData(
        nativeCrashId: String,
        sessionPartId: String,
    ) = NativeCrashData(
        nativeCrashId = nativeCrashId,
        sessionPartId = sessionPartId,
        userSessionId = "fake-user-session-id",
        timestamp = 0L,
        crash = null,
        symbols = null,
    )

    private fun assertResurrectionFailure() {
        assertEquals(0, payloadStorageService.storedPayloadCount())
        assertEquals(0, cacheStorageService.storedPayloadCount())
        assertEquals(1, logger.internalErrorMessages.size)
        assertEquals(
            InternalErrorType.PayloadResurrectionPayloadFail.toString(),
            logger.internalErrorMessages.single().msg,
        )
    }

    private companion object {
        private val startedSnapshot = perfSpanSnapshot.toEmbraceSpanData()
        val sessionMetadata = fakeCachedSessionStoredTelemetryMetadata
        val deadSessionEnvelope = fakeIncompleteSessionEnvelope(
            startMs = sessionMetadata.timestamp,
            lastHeartbeatTimeMs = sessionMetadata.timestamp + 1000L,
        )
        val messedUpSessionEnvelope = with(deadSessionEnvelope) {
            copy(
                data = data.copy(
                    spans = listOf(
                        startedSnapshot.copy(endTimeNanos = startedSnapshot.startTimeNanos + 10000000L).toEmbracePayload(),
                    ),
                    spanSnapshots = data.spanSnapshots?.plus(listOfNotNull(startedSnapshot).map(EmbraceSpanData::toEmbracePayload)),
                ),
            )
        }
        val noSessionPartSpanEnvelope = deadSessionEnvelope.copy(
            data = deadSessionEnvelope.data.copy(
                spanSnapshots = emptyList(),
            ),
        )
        val multipleSessionPartSpanEnvelope = deadSessionEnvelope.copy(
            data = deadSessionEnvelope.data.copy(
                spanSnapshots = deadSessionEnvelope.data.spanSnapshots?.plus(
                    checkNotNull(
                        FakeEmbraceSdkSpan.sessionPartSpan(
                            userSessionId = "fake-session-span-id",
                            startTimeMs = deadSessionEnvelope.getStartTime() + 1001L,
                            lastHeartbeatTimeMs = deadSessionEnvelope.getStartTime() + 1001L,
                        ).snapshot(),
                    ),
                ),
            ),
        )
        val fakeCachedCrashEnvelopeMetadata = StoredTelemetryMetadata(
            timestamp = 1000L,
            uuid = "old-session-id",
            processIdentifier = "old-process-id",
            envelopeType = CRASH,
            complete = false,
            payloadType = PayloadType.UNKNOWN,
        )
    }
}
