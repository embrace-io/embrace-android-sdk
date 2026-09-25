@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.fakes.FakeCachedLogEnvelopeStore
import io.embrace.android.embracesdk.fakes.FakeNativeCrashService
import io.embrace.android.embracesdk.fakes.fakeEnvelopeMetadata
import io.embrace.android.embracesdk.fakes.fakeEnvelopeResource
import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.fakes.fakeLaterEnvelopeMetadata
import io.embrace.android.embracesdk.fakes.fakeLaterEnvelopeResource
import io.embrace.android.embracesdk.fixtures.FAKE_SESSION_PART_ID
import io.embrace.android.embracesdk.fixtures.FAKE_SESSION_PART_ID_2
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.getSessionPartSpan
import io.embrace.android.embracesdk.internal.session.getUserSessionProperties
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SessionPartResurrectorTest {

    private lateinit var cachedLogEnvelopeStore: FakeCachedLogEnvelopeStore
    private lateinit var nativeCrashService: FakeNativeCrashService
    private lateinit var resurrector: SessionPartResurrector

    @Before
    fun setUp() {
        cachedLogEnvelopeStore = FakeCachedLogEnvelopeStore()
        nativeCrashService = FakeNativeCrashService()
        resurrector = SessionPartResurrector(cachedLogEnvelopeStore)
    }

    @Test
    fun `every snapshot becomes a failed span and the session span is promoted`() {
        val deadPart = incompleteEnvelope()
        val snapshotIds = checkNotNull(deadPart.data.spanSnapshots).map(Span::spanId)
        val resurrected = checkNotNull(resurrect(deadPart))

        assertEquals(emptyList<Span>(), resurrected.data.spanSnapshots)
        assertEquals(snapshotIds, checkNotNull(resurrected.data.spans).map(Span::spanId))
        val sessionSpan = checkNotNull(resurrected.data.spans).single { it.hasEmbraceAttribute(EmbType.Ux.Session) }
        assertEquals(Span.Status.ERROR, sessionSpan.status)
        assertTrue(sessionSpan.hasEmbraceAttribute(AppTerminationCause.Crash))
    }

    @Test
    fun `a failed span ends at the session span's last heartbeat`() {
        val deadPart = incompleteEnvelope()
        val resurrected = checkNotNull(resurrect(deadPart))
        checkNotNull(resurrected.data.spans).forEach { span ->
            assertEquals(span.endTimeNanos?.nanosToMillis(), checkNotNull(span.endTimeNanos).nanosToMillis())
        }
    }

    @Test
    fun `a failed span never ends before it started when completed spans predate the part`() {
        val deadPart = incompleteEnvelope()
        val snapshots = checkNotNull(deadPart.data.spanSnapshots)
        val sessionStart = checkNotNull(checkNotNull(deadPart.getSessionPartSpan()).startTimeNanos)
        val carriedOver = snapshots.last().copy(
            spanId = "carried-over-span-id",
            startTimeNanos = sessionStart - 2_000_000_000L,
            endTimeNanos = sessionStart - 1_000_000_000L,
        )
        val withCarriedOverSpan = deadPart.copy(data = deadPart.data.copy(spans = listOf(carriedOver)))

        val resurrected = checkNotNull(resurrect(withCarriedOverSpan))
        val latestSnapshotStart = snapshots.maxOf { checkNotNull(it.startTimeNanos) }
        checkNotNull(resurrected.data.spans)
            .filter { it.spanId != carriedOver.spanId }
            .forEach { span ->
                assertEquals(latestSnapshotStart.nanosToMillis(), checkNotNull(span.endTimeNanos).nanosToMillis())
                assertTrue(checkNotNull(span.endTimeNanos) >= checkNotNull(span.startTimeNanos))
            }
    }

    @Test
    fun `a snapshot already present as a completed span is not converted again`() {
        val deadPart = incompleteEnvelope()
        val sessionSnapshot = checkNotNull(deadPart.getSessionPartSpan())
        val completed = sessionSnapshot.copy(endTimeNanos = 1691000400000000000L)
        val withCompletedSessionSpan = deadPart.copy(data = deadPart.data.copy(spans = listOf(completed)))

        val resurrected = checkNotNull(resurrect(withCompletedSessionSpan))
        val sessionSpans = checkNotNull(resurrected.data.spans).filter { it.hasEmbraceAttribute(EmbType.Ux.Session) }
        assertEquals(listOf(completed), sessionSpans)
    }

    @Test
    fun `a payload with no session part span is not resurrected`() {
        val deadPart = incompleteEnvelope()
        val withoutSessionSpan = deadPart.copy(
            data = deadPart.data.copy(
                spanSnapshots = checkNotNull(deadPart.data.spanSnapshots).filterNot { it.hasEmbraceAttribute(EmbType.Ux.Session) },
            ),
        )
        assertNull(resurrect(withoutSessionSpan))
    }

    @Test
    fun `a payload with two session part spans is not resurrected`() {
        val deadPart = incompleteEnvelope()
        val sessionSnapshot = checkNotNull(deadPart.getSessionPartSpan())
        val duplicated = deadPart.copy(
            data = deadPart.data.copy(
                spanSnapshots = checkNotNull(deadPart.data.spanSnapshots) + sessionSnapshot.copy(spanId = "other-span-id"),
            ),
        )
        assertNull(resurrect(duplicated))
    }

    @Test
    fun `a background only user session marks the session part span`() {
        val resurrected = checkNotNull(resurrect(incompleteEnvelope(), isBackgroundOnly = true))
        assertEquals("1", resurrected.sessionSpanAttribute(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART))
    }

    @Test
    fun `a session part span already marked background only is not marked again`() {
        val deadPart = incompleteEnvelope().withSessionSpanAttribute(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART, "1")
        val resurrected = checkNotNull(resurrect(deadPart, isBackgroundOnly = true))
        assertEquals(
            listOf("1"),
            checkNotNull(resurrected.getSessionPartSpan()?.attributes)
                .filter { it.key == EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART }
                .map(Attribute::data),
        )
    }

    @Test
    fun `a terminated user session marks the session part span as the final one`() {
        val resurrected = checkNotNull(resurrect(incompleteEnvelope(), userSessionTerminationReason = "inactivity"))
        assertEquals("1", resurrected.sessionSpanAttribute(EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART))
        assertEquals("inactivity", resurrected.sessionSpanAttribute(EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON))
    }

    @Test
    fun `a session part span already marked final is not marked again`() {
        val deadPart = incompleteEnvelope().withSessionSpanAttribute(EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART, "1")
        val resurrected = checkNotNull(resurrect(deadPart, userSessionTerminationReason = "inactivity"))
        assertNull(resurrected.sessionSpanAttribute(EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON))
    }

    @Test
    fun `a matching native crash is sent and stamped on the session part span`() {
        val deadPart = incompleteEnvelope(
            sessionPartId = FAKE_SESSION_PART_ID,
            resource = fakeLaterEnvelopeResource,
            metadata = fakeLaterEnvelopeMetadata,
            sessionProperties = mapOf("dead-session-prop" to "some-val"),
        )
        val crash = nativeCrashData(FAKE_SESSION_PART_ID)
        val resurrected = checkNotNull(resurrect(deadPart, crash = crash))
        assertEquals("native-crash-id", resurrected.sessionSpanAttribute(EmbSessionAttributes.EMB_CRASH_ID))

        // the crash log ships with the dead process's resource and metadata, not this process's
        val cached = cachedLogEnvelopeStore.createdEnvelopes.single()
        assertEquals(fakeLaterEnvelopeResource, cached.resource)
        assertEquals(fakeLaterEnvelopeMetadata, cached.metadata)

        val (sent, sentMetadata) = nativeCrashService.nativeCrashesSent.single()
        assertEquals(crash, sent)

        // the fake re-keys the metadata map by value, so the dead process id appears as its own key
        assertEquals(PROCESS_ID, sentMetadata[PROCESS_ID])

        val userSessionProperties = deadPart.getUserSessionProperties()
        assertEquals("some-val", userSessionProperties["dead-session-prop".toEmbraceAttributeName()])
        userSessionProperties.forEach { (key, value) -> assertEquals(value, sentMetadata[key]) }
    }

    @Test
    fun `a native crash for another session part is not stamped`() {
        val deadPart = incompleteEnvelope(sessionPartId = FAKE_SESSION_PART_ID)
        val resurrected = checkNotNull(resurrect(deadPart, crash = nativeCrashData(FAKE_SESSION_PART_ID_2)))
        assertNull(resurrected.sessionSpanAttribute(EmbSessionAttributes.EMB_CRASH_ID))
    }

    @Test
    fun `no native crash service means no crash processing`() {
        val deadPart = incompleteEnvelope(sessionPartId = FAKE_SESSION_PART_ID)
        val resurrected = checkNotNull(
            resurrector.resurrect(
                deadPart = deadPart,
                processIdentifier = PROCESS_ID,
                nativeCrashService = null,
                nativeCrashProvider = { nativeCrashData(FAKE_SESSION_PART_ID) },
                onNativeCrashProcessed = {},
                userSessionTerminationReason = null,
                isBackgroundOnly = false,
            ),
        )
        assertNotNull(resurrected.getSessionPartSpan())
        assertNull(resurrected.sessionSpanAttribute(EmbSessionAttributes.EMB_CRASH_ID))
        assertEquals(emptyList<Envelope<*>>(), cachedLogEnvelopeStore.createdEnvelopes)
        assertTrue(nativeCrashService.nativeCrashesSent.isEmpty())
    }

    private fun resurrect(
        deadPart: Envelope<SessionPartPayload>,
        crash: NativeCrashData? = null,
        userSessionTerminationReason: String? = null,
        isBackgroundOnly: Boolean = false,
    ): Envelope<SessionPartPayload>? {
        val processed = mutableListOf<NativeCrashData>()
        val resurrected = resurrector.resurrect(
            deadPart = deadPart,
            processIdentifier = PROCESS_ID,
            nativeCrashService = nativeCrashService,
            nativeCrashProvider = { partId -> crash?.takeIf { it.sessionPartId == partId } },
            onNativeCrashProcessed = processed::add,
            userSessionTerminationReason = userSessionTerminationReason,
            isBackgroundOnly = isBackgroundOnly,
        )
        assertEquals(listOfNotNull(crash.takeIf { nativeCrashService.nativeCrashesSent.isNotEmpty() }), processed)
        return resurrected
    }

    private fun incompleteEnvelope(
        sessionPartId: String = "fakeIncompleteSessionPartId",
        sessionProperties: Map<String, String>? = null,
        resource: io.embrace.android.embracesdk.internal.payload.EnvelopeResource = fakeEnvelopeResource,
        metadata: io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata = fakeEnvelopeMetadata,
    ) = fakeIncompleteSessionEnvelope(
        sessionPartId = sessionPartId,
        sessionProperties = sessionProperties,
        resource = resource,
        metadata = metadata,
    )

    private fun nativeCrashData(sessionPartId: String) = NativeCrashData(
        nativeCrashId = "native-crash-id",
        sessionPartId = sessionPartId,
        userSessionId = "fake-user-session-id",
        timestamp = 0L,
        crash = null,
        symbols = null,
    )

    private fun Envelope<SessionPartPayload>.sessionSpanAttribute(key: String): String? =
        getSessionPartSpan()?.attributes?.findAttributeValue(key)

    private fun Envelope<SessionPartPayload>.withSessionSpanAttribute(key: String, value: String) = copy(
        data = data.copy(
            spanSnapshots = checkNotNull(data.spanSnapshots).map { span ->
                when {
                    span.hasEmbraceAttribute(EmbType.Ux.Session) ->
                        span.copy(attributes = (span.attributes ?: emptyList()) + Attribute(key, value))
                    else -> span
                }
            },
        ),
    )

    private companion object {
        private const val PROCESS_ID = "dead-process-id"
    }
}
