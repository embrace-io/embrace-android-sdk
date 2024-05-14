package io.embrace.android.embracesdk.capture.envelope.session

import io.embrace.android.embracesdk.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeNativeThreadSamplerService
import io.embrace.android.embracesdk.fakes.FakePersistableEmbraceSpan
import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.payload.LegacyExceptionError
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import org.junit.Assert.assertEquals
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
        val errorService = FakeInternalErrorService().apply {
            data = LegacyExceptionError().apply {
                addException(RuntimeException(), "test", FakeClock())
            }
        }
        sink = SpanSinkImpl().apply {
            storeCompletedSpans(listOf(cacheSpan))
        }
        currentSessionSpan = FakeCurrentSessionSpan().apply {
            spanData = listOf(EmbraceSpanData(FakeSpanData("my-span")))
        }
        activeSpan = FakePersistableEmbraceSpan.started()
        spanRepository = SpanRepository()
        spanRepository.trackStartedSpan(activeSpan)
        impl = SessionPayloadSourceImpl(
            errorService,
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
        val span = checkNotNull(payload.spans?.single())
        assertEquals("my-span", span.name)
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
        val span = checkNotNull(payload.spans?.single())
        assertEquals("my-span", span.name)
    }

    private fun assertPayloadPopulated(payload: SessionPayload) {
        val err = checkNotNull(payload.internalError)
        assertEquals(1, err.count)
        assertEquals(mapOf("armeabi-v7a" to "my-symbols"), payload.sharedLibSymbolMapping)
        val snapshots = checkNotNull(payload.spanSnapshots)
        assertEquals(1, snapshots.size)
    }
}
