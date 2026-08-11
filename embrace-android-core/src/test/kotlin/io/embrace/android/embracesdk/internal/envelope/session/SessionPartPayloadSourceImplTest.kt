package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionPartSpan
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeOtelPayloadMapper
import io.embrace.android.embracesdk.fakes.FakeProcessStateTracker
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanTerminationMode
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class SessionPartPayloadSourceImplTest {

    private lateinit var impl: SessionPartPayloadSourceImpl
    private lateinit var currentSessionPartSpan: FakeCurrentSessionPartSpan
    private lateinit var spanRepository: SpanRepository
    private lateinit var activeSpan: FakeEmbraceSdkSpan
    private val cacheSpan = Span(name = "cache-span")

    @Before
    fun setUp() {
        currentSessionPartSpan = FakeCurrentSessionPartSpan().apply {
            initializeService(1000L)
        }
        activeSpan = FakeEmbraceSdkSpan.started()
        spanRepository = SpanRepository().apply {
            storeCompletedOtelSpans(listOf(cacheSpan))
        }
        spanRepository.trackStartedEmbraceSpan(checkNotNull(currentSessionPartSpan.sessionPartSpan))
        spanRepository.trackStartedEmbraceSpan(activeSpan)
        impl = SessionPartPayloadSourceImpl(
            mapOf("armeabi-v7a" to "my-symbols"),
            currentSessionPartSpan,
            spanRepository,
            FakeOtelPayloadMapper(),
            FakeProcessStateTracker(),
            FakeClock(),
            InternalLoggerImpl(),
        )
    }

    @Test
    fun `session crash`() {
        val payload = impl.getSessionPartPayload(SessionPartSnapshotType.JVM_CRASH, false)
        assertPayloadPopulated(payload = payload, hasSessionSnapshot = false, hasNonSessionSnapshots = false)
        assertNotNull(payload.spans?.single())
    }

    @Test
    fun `session cache`() {
        val payload = impl.getSessionPartPayload(SessionPartSnapshotType.PERIODIC_CACHE, false)
        assertPayloadPopulated(payload = payload, hasSessionSnapshot = true, hasNonSessionSnapshots = true)
        val span = checkNotNull(payload.spans?.single())
        assertEquals("cache-span", span.name)
    }

    @Test
    fun `session lifecycle change`() {
        val payload = impl.getSessionPartPayload(SessionPartSnapshotType.NORMAL_END, true)
        assertPayloadPopulated(payload = payload, hasSessionSnapshot = false, hasNonSessionSnapshots = true)
        assertNotNull(payload.spans?.single())
    }

    @Test
    fun `timed out spans are failed when the session part ends regardless of app state`() {
        val clock = FakeClock()
        val timedOutSpan = FakeEmbraceSdkSpan(terminationMode = SpanTerminationMode.Timeout(1000L)).apply {
            start(clock.now())
        }
        val repository = SpanRepository().apply {
            trackStartedEmbraceSpan(checkNotNull(currentSessionPartSpan.sessionPartSpan))
            trackStartedEmbraceSpan(timedOutSpan)
        }
        val source = SessionPartPayloadSourceImpl(
            null,
            currentSessionPartSpan,
            repository,
            FakeOtelPayloadMapper(),
            FakeProcessStateTracker(),
            clock,
            InternalLoggerImpl(),
        )

        clock.tick(2000L)
        source.getSessionPartPayload(SessionPartSnapshotType.NORMAL_END, true)

        assertFalse(timedOutSpan.isRecording)
        assertEquals(ErrorCodeAttribute.Failure, timedOutSpan.errorCode)
    }

    private fun assertPayloadPopulated(
        payload: SessionPartPayload,
        hasSessionSnapshot: Boolean,
        hasNonSessionSnapshots: Boolean,
    ) {
        assertEquals(mapOf("armeabi-v7a" to "my-symbols"), payload.sharedLibSymbolMapping)
        val snapshots = checkNotNull(payload.spanSnapshots)
        if (hasSessionSnapshot) {
            assertNotNull(snapshots.single { it.hasEmbraceAttribute(EmbType.Ux.Session) })
        } else {
            assertEquals(0, snapshots.filter { it.hasEmbraceAttribute(EmbType.Ux.Session) }.size)
        }

        if (hasNonSessionSnapshots) {
            assertNotNull(snapshots.single { !it.hasEmbraceAttribute(EmbType.Ux.Session) })
        } else {
            assertNull(snapshots.singleOrNull { !it.hasEmbraceAttribute(EmbType.Ux.Session) })
        }
    }
}
