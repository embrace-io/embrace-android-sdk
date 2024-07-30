package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.SessionZygote
import io.embrace.android.embracesdk.internal.session.message.FinalEnvelopeParams
import io.embrace.android.embracesdk.internal.session.message.InitialEnvelopeParams
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PayloadMessageCollatorImplTest {

    private lateinit var initModule: FakeInitModule
    private lateinit var coreModule: FakeCoreModule
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var collator: PayloadMessageCollatorImpl
    private lateinit var gatingService: FakeGatingService

    @Before
    fun setUp() {
        initModule = FakeInitModule()
        coreModule = FakeCoreModule()
        gatingService = FakeGatingService()
        val sessionEnvelopeSource = SessionEnvelopeSourceImpl(
            metadataSource = FakeEnvelopeMetadataSource(),
            resourceSource = FakeEnvelopeResourceSource(),
            sessionPayloadSource = FakeSessionPayloadSource()
        )
        currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan
        collator = PayloadMessageCollatorImpl(
            gatingService = gatingService,
            preferencesService = FakePreferenceService(),
            currentSessionSpan = currentSessionSpan,
            sessionEnvelopeSource = sessionEnvelopeSource
        )
    }

    @Test
    fun `create background activity initial message`() {
        val msg = collator.buildInitialSession(
            InitialEnvelopeParams(
                false,
                LifeEventType.BKGND_STATE,
                5,
                ApplicationState.BACKGROUND
            )
        )
        msg.verifyInitialFieldsPopulated()
    }

    @Test
    fun `create session initial message`() {
        val msg = collator.buildInitialSession(
            InitialEnvelopeParams(
                false,
                LifeEventType.STATE,
                5,
                ApplicationState.FOREGROUND
            )
        )
        msg.verifyInitialFieldsPopulated()
    }

    @Test
    fun `create background activity end message`() {
        // create start message
        val startMsg = collator.buildInitialSession(
            InitialEnvelopeParams(
                false,
                LifeEventType.BKGND_STATE,
                5,
                ApplicationState.BACKGROUND
            )
        )
        startMsg.verifyInitialFieldsPopulated()

        // create session
        val payload = collator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = startMsg,
                endType = SessionSnapshotType.NORMAL_END,
                logger = initModule.logger,
                backgroundActivityEnabled = true,
                crashId = "crashId"
            )
        )
        payload.verifyFinalFieldsPopulated()
        assertEquals(1, gatingService.envelopesFiltered.size)
    }

    @Test
    fun `create session end message`() {
        // create start message
        val startMsg = collator.buildInitialSession(
            InitialEnvelopeParams(
                false,
                LifeEventType.STATE,
                5,
                ApplicationState.FOREGROUND
            )
        )
        startMsg.verifyInitialFieldsPopulated()

        // create session
        val payload = collator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = startMsg,
                endType = SessionSnapshotType.NORMAL_END,
                logger = initModule.logger,
                backgroundActivityEnabled = true,
                crashId = "crashId",
            )
        )
        payload.verifyFinalFieldsPopulated()
        assertEquals(1, gatingService.envelopesFiltered.size)
    }

    @Test
    fun `session span is created when session payload is built if it did not exist before`() {
        currentSessionSpan.endSession(startNewSession = false)
        val msg = collator.buildInitialSession(
            InitialEnvelopeParams(
                false,
                LifeEventType.STATE,
                5,
                ApplicationState.FOREGROUND
            )
        )
        msg.verifyInitialFieldsPopulated()
    }

    private fun Envelope<SessionPayload>.verifyFinalFieldsPopulated() {
        assertNotNull(resource)
        assertNotNull(metadata)
        assertNotNull(data)
        assertNotNull(version)
        assertNotNull(type)
    }

    private fun SessionZygote.verifyInitialFieldsPopulated() {
        assertTrue(sessionId.isNotBlank())
        assertEquals(5L, startTime)
    }
}
