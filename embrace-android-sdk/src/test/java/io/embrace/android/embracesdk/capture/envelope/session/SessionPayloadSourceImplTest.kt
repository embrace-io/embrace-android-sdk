package io.embrace.android.embracesdk.capture.envelope.session

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeNativeThreadSamplerService
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.payload.LegacyExceptionError
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SessionPayloadSourceImplTest {

    private lateinit var impl: SessionPayloadSourceImpl

    @Before
    fun setUp() {
        val errorService = FakeInternalErrorService().apply {
            currentExceptionError = LegacyExceptionError().apply {
                addException(RuntimeException(), "test", FakeClock())
            }
        }
        impl = SessionPayloadSourceImpl(
            errorService,
            FakeNativeThreadSamplerService()
        )
    }

    @Test
    fun `session crash`() {
        val payload = impl.getSessionPayload(SessionSnapshotType.JVM_CRASH)
        assertPayloadPopulated(payload)
    }

    @Test
    fun `session cache`() {
        val payload = impl.getSessionPayload(SessionSnapshotType.PERIODIC_CACHE)
        assertPayloadPopulated(payload)
    }

    @Test
    fun `session lifecycle change`() {
        val payload = impl.getSessionPayload(SessionSnapshotType.NORMAL_END)
        assertPayloadPopulated(payload)
    }

    private fun assertPayloadPopulated(payload: SessionPayload) {
        val err = checkNotNull(payload.internalError)
        assertEquals(1, err.count)
        assertEquals(mapOf("armeabi-v7a" to "my-symbols"), payload.sharedLibSymbolMapping)
    }
}
