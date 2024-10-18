package io.embrace.android.embracesdk.internal.session.message

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.CoreModuleImpl
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class PayloadMessageCollatorImplTest {

    private lateinit var initModule: FakeInitModule
    private lateinit var coreModule: CoreModule
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var collator: PayloadMessageCollatorImpl
    private lateinit var gatingService: FakeGatingService

    @Before
    fun setUp() {
        initModule = FakeInitModule()
        coreModule = CoreModuleImpl(RuntimeEnvironment.getApplication(), initModule.logger)
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
                continueMonitoring = true,
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
                continueMonitoring = true,
                crashId = "crashId",
            )
        )
        payload.verifyFinalFieldsPopulated()
        assertEquals(1, gatingService.envelopesFiltered.size)
    }

    @Test
    fun `session span is created when session payload is built if it did not exist before`() {
        currentSessionSpan.endSession(startNewSession = false)
        listOf(true, false).forEach { startupTemperature ->
            LifeEventType.values().forEach { lifeEventType ->
                ApplicationState.values().forEach { previousState ->
                    collator.buildInitialSession(
                        InitialEnvelopeParams(
                            coldStart = startupTemperature,
                            startType = lifeEventType,
                            startTime = 5L,
                            appState = previousState
                        )
                    ).verifyInitialFieldsPopulated()
                }
            }
        }
    }

    private fun Envelope<SessionPayload>.verifyFinalFieldsPopulated() {
        assertNotNull(resource)
        assertNotNull(metadata)
        assertNotNull(data)
        assertNotNull(version)
        assertNotNull(type)
    }

    private fun SessionZygote.verifyInitialFieldsPopulated() {
        assertTrue("Session ID invalid: $this", sessionId.isNotBlank())
        assertEquals(5L, startTime)
    }
}
