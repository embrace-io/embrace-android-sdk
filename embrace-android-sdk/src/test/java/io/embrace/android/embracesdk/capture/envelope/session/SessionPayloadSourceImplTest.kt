package io.embrace.android.embracesdk.capture.envelope.session

import io.embrace.android.embracesdk.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeNativeThreadSamplerService
import io.embrace.android.embracesdk.fakes.FakePersistableEmbraceSpan
import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

internal class SessionPayloadSourceImplTest {

    private lateinit var impl: SessionPayloadSourceImpl
    private lateinit var sink: SpanSinkImpl
    private lateinit var currentSessionSpan: FakeCurrentSessionSpan
    private lateinit var spanRepository: SpanRepository
    private lateinit var activeSpan: FakePersistableEmbraceSpan
    private val cacheSpan = FakeSpanData(name = "cache-span")

    @Before
    fun setUp() {
        sink = SpanSinkImpl().apply {
            storeCompletedSpans(listOf(cacheSpan))
        }
        currentSessionSpan = FakeCurrentSessionSpan().apply {
            initializeService(1000L)
        }
        activeSpan = FakePersistableEmbraceSpan.started()
        spanRepository = SpanRepository()
        spanRepository.trackStartedSpan(activeSpan)
        impl = SessionPayloadSourceImpl(
            FakeNativeThreadSamplerService(),
            sink,
            currentSessionSpan,
            spanRepository,
            EmbLoggerImpl(),
        ) { FakeSessionPropertiesService() }
    }

    @Test
    fun `session crash`() {
        val payload = impl.getSessionPayload(SessionSnapshotType.JVM_CRASH)
        assertPayloadPopulated(payload)
        assertNotNull(payload.spans?.single())
    }

    @Test
    fun `session cache`() {
        val payload = impl.getSessionPayload(SessionSnapshotType.PERIODIC_CACHE)
        assertPayloadPopulated(payload)
        val span = checkNotNull(payload.spans?.single())
        assertEquals("cache-span", span.name)
    }

    @Test
    fun `session lifecycle change`() {
        val payload = impl.getSessionPayload(SessionSnapshotType.NORMAL_END)
        assertPayloadPopulated(payload)
        assertNotNull(payload.spans?.single())
    }

    private fun assertPayloadPopulated(payload: SessionPayload) {
        assertEquals(mapOf("armeabi-v7a" to "my-symbols"), payload.sharedLibSymbolMapping)
        val snapshots = checkNotNull(payload.spanSnapshots)
        assertEquals(1, snapshots.size)
    }
}
