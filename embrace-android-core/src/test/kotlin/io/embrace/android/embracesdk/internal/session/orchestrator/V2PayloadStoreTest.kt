package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeIntakeService
import io.embrace.android.embracesdk.fakes.FakePayloadIntake
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeUuidSource
import io.embrace.android.embracesdk.fakes.fakeSessionEnvelope
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class V2PayloadStoreTest {

    private companion object {
        const val USER_SESSION_ID = "fakeUserSessionId"
        const val SESSION_PART_ID = "fakeSessionPartId"
    }

    private lateinit var store: PayloadStoreImpl
    private lateinit var intakeService: FakeIntakeService
    private lateinit var sessionIdsProvider: FakeSessionIdsProvider

    @Before
    fun setUp() {
        intakeService = FakeIntakeService()
        sessionIdsProvider = FakeSessionIdsProvider(userSessionId = USER_SESSION_ID, sessionPartId = SESSION_PART_ID)
        store = PayloadStoreImpl(
            intakeService,
            FakeClock(),
            { "fakeProcessId" },
            FakeUuidSource(),
            sessionIdsProvider::getActiveSessionIds,
        )
    }

    @Test
    fun `test store session`() {
        val envelope = fakeSessionEnvelope()
        store.storeSessionPartPayload(envelope, TransitionType.ON_BACKGROUND)
        verifySessionIntake(
            envelope,
            intakeService.getIntakes(),
            "p3_1692201601000_fakeuuid_fakeProcessId_true_session_${USER_SESSION_ID}_${SESSION_PART_ID}_v2.json",
        )
    }

    @Test
    fun `test store session with crash`() {
        val envelope = fakeSessionEnvelope()
        store.storeSessionPartPayload(envelope, TransitionType.CRASH)
        verifySessionIntake(
            envelope,
            intakeService.getIntakes(),
            "p3_1692201601000_fakeuuid_fakeProcessId_true_session_${USER_SESSION_ID}_${SESSION_PART_ID}_v2.json",
        )
    }

    @Test
    fun `test log`() {
        val envelope = Envelope(data = LogPayload())
        store.storeLogPayload(envelope, true)

        val intake = intakeService.getIntakes<LogPayload>().single()
        assertSame(envelope, intake.envelope)
        assertEquals(
            "p5_1692201601000_fakeuuid_fakeProcessId_true_unknown_${USER_SESSION_ID}_${SESSION_PART_ID}_v2.json",
            intake.metadata.filename,
        )
        assertEquals(0, intakeService.shutdownCount)
    }

    @Test
    fun `test shutdown`() {
        store.handleCrash("fakeCrashId")
        assertEquals(1, intakeService.shutdownCount)
    }

    @Test
    fun `test snapshot`() {
        val envelope = fakeSessionEnvelope()
        store.cacheSessionPartSnapshot(envelope)
        verifySessionIntake(
            envelope,
            intakeService.getIntakes(false),
            "p3_1692201601000_fakeuuid_fakeProcessId_false_session_${USER_SESSION_ID}_${SESSION_PART_ID}_v2.json",
        )
    }

    @Test
    fun `test different log types`() {
        // crash
        listOf(
            System.Crash,
            System.NativeCrash,
            System.ReactNativeCrash,
        ).forEach { type ->
            storeLogWithType(type)
            assertEquals(SupportedEnvelopeType.CRASH, getLastLogMetadata().envelopeType)
            assertEquals(type.value, getLastLogMetadata().payloadType.value)
        }

        // log
        listOf(
            System.Exit,
            System.Exception,
            System.Log,
        ).forEach { type ->
            storeLogWithType(type)
            assertEquals(SupportedEnvelopeType.LOG, getLastLogMetadata().envelopeType)
            assertEquals(type.value, getLastLogMetadata().payloadType.value)
        }

        // network
        storeLogWithType(System.NetworkCapturedRequest)
        assertEquals(SupportedEnvelopeType.BLOB, getLastLogMetadata().envelopeType)
        assertEquals(System.NetworkCapturedRequest.value, getLastLogMetadata().payloadType.value)
    }

    @Test
    fun `test log attachment`() {
        val envelope = Envelope(data = Pair("test", ByteArray(5)))
        store.storeAttachment(envelope)

        val intake = intakeService.getIntakes<Pair<String, ByteArray>>().single()
        assertSame(envelope, intake.envelope)
        assertEquals(
            "p4_1692201601000_fakeuuid_fakeProcessId_true_attachment_${USER_SESSION_ID}_${SESSION_PART_ID}_v2.json",
            intake.metadata.filename,
        )
        assertEquals(0, intakeService.shutdownCount)
    }

    private fun storeLogWithType(type: EmbType) {
        val envelope = Envelope(
            data = LogPayload(
                logs = listOf(
                    Log(attributes = listOf(Attribute("emb.type", type.value))),
                ),
            ),
        )
        store.storeLogPayload(envelope, true)
    }

    private fun getLastLogMetadata(): StoredTelemetryMetadata {
        return intakeService.getIntakes<LogPayload>().last().metadata
    }

    private fun verifySessionIntake(
        envelope: Envelope<SessionPartPayload>,
        intakes: List<FakePayloadIntake<SessionPartPayload>>,
        filename: String,
    ) {
        val intake = intakes.single()
        assertSame(envelope, intake.envelope)
        assertEquals(filename, intake.metadata.filename)
        assertEquals(0, intakeService.shutdownCount)
    }
}
