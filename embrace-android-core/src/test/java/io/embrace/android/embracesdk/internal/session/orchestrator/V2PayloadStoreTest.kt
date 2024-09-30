package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeIntakeService
import io.embrace.android.embracesdk.fakes.fakeSessionEnvelope
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class V2PayloadStoreTest {

    private lateinit var store: V2PayloadStore
    private lateinit var intakeService: FakeIntakeService

    @Before
    fun setUp() {
        intakeService = FakeIntakeService()
        store = V2PayloadStore(intakeService, FakeClock()) { "fakeuuid" }
    }

    @Test
    fun `test session`() {
        val envelope = fakeSessionEnvelope()
        store.storeSessionPayload(envelope, TransitionType.ON_BACKGROUND)
        verifySessionIntake(envelope)
    }

    @Test
    fun `test session with crash`() {
        val envelope = fakeSessionEnvelope()
        store.storeSessionPayload(envelope, TransitionType.CRASH)
        verifySessionIntake(envelope)
    }

    @Test
    fun `test log`() {
        val envelope = Envelope(data = LogPayload())
        store.storeLogPayload(envelope, true)

        val intake = intakeService.getIntakes<LogPayload>().single()
        assertSame(envelope, intake.envelope)
        assertEquals("1692201601000_log_fakeuuid_v1.json", intake.metadata.filename)
        assertEquals(0, intakeService.shutdownCount)
    }

    @Test
    fun `test shutdown`() {
        store.onCrash()
        assertEquals(1, intakeService.shutdownCount)
    }

    private fun verifySessionIntake(envelope: Envelope<SessionPayload>) {
        val intake = intakeService.getIntakes<SessionPayload>().single()
        assertSame(envelope, intake.envelope)
        assertEquals("1692201601000_session_fakeuuid_v1.json", intake.metadata.filename)
        assertEquals(0, intakeService.shutdownCount)
    }
}
