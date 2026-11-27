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
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.session.getSessionPartSpan
import io.embrace.android.embracesdk.internal.session.getUserSessionProperties
import io.embrace.android.embracesdk.internal.state.AppStateKeys
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
    fun `a failed span ends at the latest time a span started when nothing later is recorded`() {
        val deadPart = incompleteEnvelope()
        val latestSnapshotStart = checkNotNull(deadPart.data.spanSnapshots).maxOf { checkNotNull(it.startTimeNanos) }
        val resurrected = checkNotNull(resurrect(deadPart))
        checkNotNull(resurrected.data.spans).forEach { span ->
            assertEquals(latestSnapshotStart.nanosToMillis(), span.endTimeMs())
        }
    }

    @Test
    fun `an event later than every span start and end does not move the end`() {
        val deadPart = incompleteEnvelope()
        val latestSnapshotStart = checkNotNull(deadPart.data.spanSnapshots).maxOf { checkNotNull(it.startTimeNanos) }
        val withLateEvent = deadPart.withSessionSpanEvent(
            SpanEvent(name = "breadcrumb", timestampNanos = latestSnapshotStart + 7_000_000_000L),
        )

        val resurrected = checkNotNull(resurrect(withLateEvent))
        checkNotNull(resurrected.data.spans).forEach { span ->
            assertEquals(latestSnapshotStart.nanosToMillis(), span.endTimeMs())
        }
    }

    @Test
    fun `a failed span ends when the part was last written if that is later than every span start and end`() {
        val deadPart = incompleteEnvelope()
        val latestSnapshotStart = checkNotNull(deadPart.data.spanSnapshots).maxOf { checkNotNull(it.startTimeNanos) }
        val lastWrittenMs = latestSnapshotStart.nanosToMillis() + 7_000L
        val resurrected = checkNotNull(resurrect(deadPart, lastWrittenMs = lastWrittenMs))
        checkNotNull(resurrected.data.spans).forEach { span ->
            assertEquals(lastWrittenMs, span.endTimeMs())
        }
    }

    @Test
    fun `a span end or event later than the last written time does not move the end`() {
        val deadPart = incompleteEnvelope()
        val snapshots = checkNotNull(deadPart.data.spanSnapshots)
        val lastWrittenMs = snapshots.maxOf { checkNotNull(it.startTimeNanos) }.nanosToMillis() + 7_000L
        val lateNanos = (lastWrittenMs + 60_000L).millisToNanos()
        val lateCompleted = snapshots.last().copy(spanId = "late-completed-span-id", endTimeNanos = lateNanos)
        val withLateTelemetry = deadPart
            .withSessionSpanEvent(SpanEvent(name = "breadcrumb", timestampNanos = lateNanos))
            .let { it.copy(data = it.data.copy(spans = listOf(lateCompleted))) }

        val resurrected = checkNotNull(resurrect(withLateTelemetry, lastWrittenMs = lastWrittenMs))
        val spans = checkNotNull(resurrected.data.spans)
        assertEquals(lateNanos, spans.single { it.spanId == lateCompleted.spanId }.endTimeNanos)
        spans.filter { it.spanId != lateCompleted.spanId }.forEach { span ->
            assertEquals(lastWrittenMs, span.endTimeMs())
        }
    }

    @Test
    fun `a span that started after the last written time ends when it started and does not move the end`() {
        val deadPart = incompleteEnvelope()
        val snapshots = checkNotNull(deadPart.data.spanSnapshots)
        val lastWrittenMs = snapshots.maxOf { checkNotNull(it.startTimeNanos) }.nanosToMillis() + 7_000L
        val lateStartNanos = (lastWrittenMs + 60_000L).millisToNanos()
        val lateStarted = snapshots.last().copy(spanId = "late-started-span-id", startTimeNanos = lateStartNanos)
        val withLateStart = deadPart.copy(data = deadPart.data.copy(spanSnapshots = snapshots + lateStarted))

        val resurrected = checkNotNull(resurrect(withLateStart, lastWrittenMs = lastWrittenMs))
        val spans = checkNotNull(resurrected.data.spans)
        assertEquals(lateStartNanos, spans.single { it.spanId == lateStarted.spanId }.endTimeNanos)
        spans.filter { it.spanId != lateStarted.spanId }.forEach { span ->
            assertEquals(lastWrittenMs, span.endTimeMs())
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
                assertEquals(latestSnapshotStart.nanosToMillis(), span.endTimeMs())
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

    @Test
    fun `every span the dead process left running is completed with the end of its link, bar the change log`() {
        // the session part span has a start number, which is how a part shows that its process linked spans to state
        val deadPart = incompleteEnvelope().withChangeLog().withSessionSpanAttribute(AppStateKeys.SEQ, "2")
        val resurrected = checkNotNull(resurrect(deadPart))

        val spans = checkNotNull(resurrected.data.spans)
        val (changeLogs, others) = spans.partition { it.isStateChangeLog() }
        assertEquals(1, changeLogs.size)
        assertEquals(
            null,
            changeLogs.single().attributes.orEmpty().findAttributeValue(AppStateKeys.END_SEQ),
        )
        assertEquals(2, others.size)
        others.forEach { span ->
            assertEquals("5", span.attributes.orEmpty().findAttributeValue(AppStateKeys.END_SEQ))
        }
    }

    @Test
    fun `a span that started at the last number is not given an end number`() {
        val deadPart = incompleteEnvelope().withChangeLog().withSessionSpanAttribute(AppStateKeys.SEQ, "5")
        val resurrected = checkNotNull(resurrect(deadPart))

        val sessionSpan = checkNotNull(resurrected.getSessionPartSpan())
        assertNull(sessionSpan.attributes.orEmpty().findAttributeValue(AppStateKeys.END_SEQ))
    }

    @Test
    fun `no end number is given when the part has no change log`() {
        val resurrected = checkNotNull(resurrect(incompleteEnvelope()))

        checkNotNull(resurrected.data.spans).forEach { span ->
            assertNull(span.attributes.orEmpty().findAttributeValue(AppStateKeys.END_SEQ))
        }
    }

    @Test
    fun `the native crash metadata holds the dead part's last state and none of the live values`() {
        val deadPart = incompleteEnvelope(sessionPartId = FAKE_SESSION_PART_ID).withChangeLog()
        resurrect(deadPart, crash = nativeCrashData(FAKE_SESSION_PART_ID))

        val metadata = nativeCrashService.metadataSent.single()
        assertEquals(PROCESS_ID, metadata[EmbSessionAttributes.EMB_PROCESS_IDENTIFIER])
        assertEquals(
            mapOf(
                "${AppStateKeys.VALUE_PREFIX}network" to "cell",
                "${AppStateKeys.VALUE_PREFIX}screen-automatic" to "Home",
                "${AppStateKeys.VALUE_TYPE_PREFIX}screen-automatic" to AppStateKeys.VALUE_TYPE_SYSTEM,
                AppStateKeys.SEQ to "5",
            ),
            metadata.filterKeys { it.startsWith(AppStateKeys.STEM) },
        )
    }

    @Test
    fun `the native crash metadata has no state when the part has no change log`() {
        resurrect(incompleteEnvelope(sessionPartId = FAKE_SESSION_PART_ID), crash = nativeCrashData(FAKE_SESSION_PART_ID))

        val metadata = nativeCrashService.metadataSent.single()
        assertEquals(emptyMap<String, String>(), metadata.filterKeys { it.startsWith(AppStateKeys.STEM) })
    }

    @Test
    fun `a dead part whose process linked no spans gets no end number, whatever this launch does, and its crash is still stamped`() {
        // the process died with only the model's first step on: no span of its part has a start number. This launch links
        // spans, and must not take that for the dead process having done so.
        val deadPart = incompleteEnvelope(sessionPartId = FAKE_SESSION_PART_ID).withChangeLog()
        val resurrected = checkNotNull(resurrect(deadPart, crash = nativeCrashData(FAKE_SESSION_PART_ID)))

        checkNotNull(resurrected.data.spans).forEach { span ->
            assertNull(span.attributes.orEmpty().findAttributeValue(AppStateKeys.END_SEQ))
        }
        val metadata = nativeCrashService.metadataSent.single()
        assertEquals("5", metadata[AppStateKeys.SEQ])
        assertEquals("cell", metadata["${AppStateKeys.VALUE_PREFIX}network"])
    }

    @Test
    fun `with state recovery off in this launch a dead part gets neither a stamp on its crash nor an end number`() {
        resurrector = SessionPartResurrector(cachedLogEnvelopeStore, recoverDeadProcessState = { false })
        val deadPart = incompleteEnvelope(sessionPartId = FAKE_SESSION_PART_ID)
            .withChangeLog()
            .withSessionSpanAttribute(AppStateKeys.SEQ, "2")
        val resurrected = checkNotNull(resurrect(deadPart, crash = nativeCrashData(FAKE_SESSION_PART_ID)))

        val metadata = nativeCrashService.metadataSent.single()
        assertEquals(emptyMap<String, String>(), metadata.filterKeys { it.startsWith(AppStateKeys.STEM) })
        val (changeLogs, others) = checkNotNull(resurrected.data.spans).partition { it.isStateChangeLog() }
        // the change log itself is still delivered: it is the dead part's own telemetry
        assertEquals(1, changeLogs.size)
        others.forEach { span ->
            assertNull(span.attributes.orEmpty().findAttributeValue(AppStateKeys.END_SEQ))
        }
    }

    @Test
    fun `with state recovery on a dead part whose process linked spans gets both the stamp and the end numbers`() {
        resurrector = SessionPartResurrector(cachedLogEnvelopeStore, recoverDeadProcessState = { true })
        val deadPart = incompleteEnvelope(sessionPartId = FAKE_SESSION_PART_ID)
            .withChangeLog()
            .withSessionSpanAttribute(AppStateKeys.SEQ, "2")
        val resurrected = checkNotNull(resurrect(deadPart, crash = nativeCrashData(FAKE_SESSION_PART_ID)))

        assertEquals("5", nativeCrashService.metadataSent.single()[AppStateKeys.SEQ])
        val (_, others) = checkNotNull(resurrected.data.spans).partition { it.isStateChangeLog() }
        assertEquals(2, others.size)
        others.forEach { span ->
            assertEquals("5", span.attributes.orEmpty().findAttributeValue(AppStateKeys.END_SEQ))
        }
    }

    private fun Envelope<SessionPartPayload>.withChangeLog() = copy(
        data = data.copy(spanSnapshots = checkNotNull(data.spanSnapshots) + DeadPartStateTest.changeLog()),
    )

    private fun resurrect(
        deadPart: Envelope<SessionPartPayload>,
        crash: NativeCrashData? = null,
        userSessionTerminationReason: String? = null,
        isBackgroundOnly: Boolean = false,
        lastWrittenMs: Long? = null,
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
            lastWrittenMs = lastWrittenMs,
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

    /**
     * The span's end time at the millisecond resolution a resurrected part's end time is estimated at.
     */
    private fun Span.endTimeMs(): Long = checkNotNull(endTimeNanos).nanosToMillis()

    private fun Envelope<SessionPartPayload>.sessionSpanAttribute(key: String): String? =
        getSessionPartSpan()?.attributes?.findAttributeValue(key)

    private fun Envelope<SessionPartPayload>.withSessionSpanAttribute(key: String, value: String) =
        withSessionSpan { span -> span.copy(attributes = (span.attributes ?: emptyList()) + Attribute(key, value)) }

    private fun Envelope<SessionPartPayload>.withSessionSpanEvent(event: SpanEvent) =
        withSessionSpan { span -> span.copy(events = (span.events ?: emptyList()) + event) }

    private fun Envelope<SessionPartPayload>.withSessionSpan(update: (Span) -> Span) = copy(
        data = data.copy(
            spanSnapshots = checkNotNull(data.spanSnapshots).map { span ->
                when {
                    span.hasEmbraceAttribute(EmbType.Ux.Session) -> update(span)
                    else -> span
                }
            },
        ),
    )

    private companion object {
        private const val PROCESS_ID = "dead-process-id"
    }
}
