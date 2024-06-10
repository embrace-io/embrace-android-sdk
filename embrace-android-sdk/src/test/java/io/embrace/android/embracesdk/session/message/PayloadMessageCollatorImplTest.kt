package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.SessionZygote
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

internal class PayloadMessageCollatorImplTest {

    private lateinit var initModule: FakeInitModule
    private lateinit var coreModule: FakeCoreModule
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
        collator = PayloadMessageCollatorImpl(
            gatingService = gatingService,
            preferencesService = FakePreferenceService(),
            currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan,
            logger = initModule.logger,
            sessionEnvelopeSource = sessionEnvelopeSource
        )
    }

    @Test
    fun `create background activity initial message`() {
        val msg = collator.buildInitialSession(
            InitialEnvelopeParams.BackgroundActivityParams(
                false,
                Session.LifeEventType.BKGND_STATE,
                5
            )
        )
        msg.verifyInitialFieldsPopulated()
    }

    @Test
    fun `create session initial message`() {
        val msg = collator.buildInitialSession(
            InitialEnvelopeParams.SessionParams(
                false,
                Session.LifeEventType.STATE,
                5
            )
        )
        msg.verifyInitialFieldsPopulated()
    }

    @Test
    fun `create background activity end message`() {
        // create start message
        val startMsg = collator.buildInitialSession(
            InitialEnvelopeParams.BackgroundActivityParams(
                false,
                Session.LifeEventType.BKGND_STATE,
                5
            )
        )
        startMsg.verifyInitialFieldsPopulated()

        // create session
        val payload = collator.buildFinalBackgroundActivityMessage(
            FinalEnvelopeParams.BackgroundActivityParams(
                startMsg,
                15000000000,
                Session.LifeEventType.BKGND_STATE,
                SessionSnapshotType.NORMAL_END,
                initModule.logger,
                true,
                "crashId"
            )
        )
        payload.verifyFinalFieldsPopulated()
        assertEquals(1, gatingService.envelopesFiltered.size)
    }

    @Test
    fun `create session end message`() {
        // create start message
        val startMsg = collator.buildInitialSession(
            InitialEnvelopeParams.SessionParams(
                false,
                Session.LifeEventType.STATE,
                5
            )
        )
        startMsg.verifyInitialFieldsPopulated()

        // create session
        val payload = collator.buildFinalSessionMessage(
            FinalEnvelopeParams.SessionParams(
                startMsg,
                15000000000,
                Session.LifeEventType.STATE,
                SessionSnapshotType.NORMAL_END,
                initModule.logger,
                true,
                "crashId",
            )
        )
        payload.verifyFinalFieldsPopulated()
        assertEquals(1, gatingService.envelopesFiltered.size)
    }

    private fun SessionMessage.verifyFinalFieldsPopulated() {
        assertNotNull(resource)
        assertNotNull(metadata)
        assertNotNull(data)
        assertNotNull(newVersion)
        assertNotNull(type)
        session.verifyFinalFieldsPopulated()
    }

    private fun SessionZygote.verifyInitialFieldsPopulated() {
        assertNotNull(sessionId)
        assertEquals(5L, startTime)
    }

    private fun Session.verifyFinalFieldsPopulated() {
        assertEquals(15000000000L, endTime)
        assertEquals(15000000000L, lastHeartbeatTime)
    }
}
