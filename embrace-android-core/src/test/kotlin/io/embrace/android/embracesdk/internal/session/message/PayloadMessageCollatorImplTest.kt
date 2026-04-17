package io.embrace.android.embracesdk.internal.session.message

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeOrdinalStore
import io.embrace.android.embracesdk.fakes.FakeSessionPartPayloadSource
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.CoreModuleImpl
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
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
    private lateinit var currentSessionPartSpan: CurrentSessionPartSpan
    private lateinit var collator: PayloadMessageCollatorImpl

    @Before
    fun setUp() {
        initModule = FakeInitModule()
        coreModule = CoreModuleImpl(RuntimeEnvironment.getApplication(), initModule)
        val sessionPartEnvelopeSource = SessionPartEnvelopeSourceImpl(
            metadataSource = FakeEnvelopeMetadataSource(),
            resourceSource = FakeEnvelopeResourceSource(),
            payloadSource = FakeSessionPartPayloadSource()
        )
        currentSessionPartSpan = initModule.openTelemetryModule.currentSessionPartSpan
        collator = PayloadMessageCollatorImpl(
            store = FakeOrdinalStore(),
            currentSessionPartSpan = currentSessionPartSpan,
            sessionPartEnvelopeSource = sessionPartEnvelopeSource
        )
    }

    @Test
    fun `create background activity initial message`() {
        val msg = collator.buildInitialPart(
            InitialEnvelopeParams(
                false,
                LifeEventType.BKGND_STATE,
                5,
                AppState.BACKGROUND
            )
        )
        msg.verifyInitialFieldsPopulated()
    }

    @Test
    fun `create session initial message`() {
        val msg = collator.buildInitialPart(
            InitialEnvelopeParams(
                false,
                LifeEventType.STATE,
                5,
                AppState.FOREGROUND
            )
        )
        msg.verifyInitialFieldsPopulated()
    }

    @Test
    fun `create background activity end message`() {
        // create start message
        val startMsg = collator.buildInitialPart(
            InitialEnvelopeParams(
                false,
                LifeEventType.BKGND_STATE,
                5,
                AppState.BACKGROUND
            )
        )
        startMsg.verifyInitialFieldsPopulated()

        // create session
        val payload = collator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = startMsg,
                endType = SessionPartSnapshotType.NORMAL_END,
                logger = initModule.logger,
                continueMonitoring = true,
                crashId = "crashId"
            )
        )
        payload.verifyFinalFieldsPopulated()
    }

    @Test
    fun `create session end message`() {
        // create start message
        val startMsg = collator.buildInitialPart(
            InitialEnvelopeParams(
                false,
                LifeEventType.STATE,
                5,
                AppState.FOREGROUND
            )
        )
        startMsg.verifyInitialFieldsPopulated()

        // create session
        val payload = collator.buildFinalEnvelope(
            FinalEnvelopeParams(
                initial = startMsg,
                endType = SessionPartSnapshotType.NORMAL_END,
                logger = initModule.logger,
                continueMonitoring = true,
                crashId = "crashId",
            )
        )
        payload.verifyFinalFieldsPopulated()
    }

    @Test
    fun `session span is created when session payload is built if it did not exist before`() {
        currentSessionPartSpan.endSession(startNewSession = false)
        listOf(true, false).forEach { startupTemperature ->
            LifeEventType.entries.forEach { lifeEventType ->
                AppState.entries.forEach { previousState ->
                    collator.buildInitialPart(
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

    private fun Envelope<SessionPartPayload>.verifyFinalFieldsPopulated() {
        assertNotNull(resource)
        assertNotNull(metadata)
        assertNotNull(data)
        assertNotNull(version)
        assertNotNull(type)
    }

    private fun SessionPartToken.verifyInitialFieldsPopulated() {
        assertTrue("Session ID invalid: $this", sessionPartId.isNotBlank())
        assertEquals(5L, startTime)
    }
}
