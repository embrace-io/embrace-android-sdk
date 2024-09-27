package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakePersistableEmbraceSpan
import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
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
        spanRepository.trackStartedSpan(checkNotNull(currentSessionSpan.sessionSpan))
        spanRepository.trackStartedSpan(activeSpan)
        impl = SessionPayloadSourceImpl(
            { mapOf("armeabi-v7a" to "my-symbols") },
            sink,
            currentSessionSpan,
            spanRepository,
            object : OtelPayloadMapper {
                override fun getSessionPayload(
                    endType: SessionSnapshotType,
                    crashId: String?
                ): List<Span> = emptyList()
            },
            EmbLoggerImpl()
        )
    }

    @Test
    fun `session crash`() {
        val payload = impl.getSessionPayload(SessionSnapshotType.JVM_CRASH, false)
        assertPayloadPopulated(payload = payload, hasSessionSnapshot = false)
        assertNotNull(payload.spans?.single())
    }

    @Test
    fun `session cache`() {
        val payload = impl.getSessionPayload(SessionSnapshotType.PERIODIC_CACHE, false)
        assertPayloadPopulated(payload = payload, hasSessionSnapshot = true)
        val span = checkNotNull(payload.spans?.single())
        assertEquals("cache-span", span.name)
    }

    @Test
    fun `session lifecycle change`() {
        val payload = impl.getSessionPayload(SessionSnapshotType.NORMAL_END, true)
        assertPayloadPopulated(payload = payload, hasSessionSnapshot = false)
        assertNotNull(payload.spans?.single())
    }

    private fun assertPayloadPopulated(
        payload: SessionPayload,
        hasSessionSnapshot: Boolean
    ) {
        assertEquals(mapOf("armeabi-v7a" to "my-symbols"), payload.sharedLibSymbolMapping)
        val snapshots = checkNotNull(payload.spanSnapshots)
        if (hasSessionSnapshot) {
            assertNotNull(snapshots.single { it.hasFixedAttribute(EmbType.Ux.Session) })
        } else {
            assertEquals(0, snapshots.filter { it.hasFixedAttribute(EmbType.Ux.Session) }.size)
        }

        assertNotNull(snapshots.single { !it.hasFixedAttribute(EmbType.Ux.Session) })
    }
}
