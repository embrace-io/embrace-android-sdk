package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeSessionPropertiesService
import io.embrace.android.embracesdk.anr.AnrOtelMapper
import io.embrace.android.embracesdk.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePerformanceInfoService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.fakes.FakeWebViewService
import io.embrace.android.embracesdk.fakes.fakeCompletedAnrInterval
import io.embrace.android.embracesdk.fakes.fakeInProgressAnrInterval
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.payload.LegacyExceptionError
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.message.FinalEnvelopeParams
import io.embrace.android.embracesdk.session.message.InitialEnvelopeParams
import io.embrace.android.embracesdk.session.message.PayloadMessageCollator
import io.embrace.android.embracesdk.session.message.V1PayloadMessageCollator
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class V1PayloadMessageCollatorTest {

    private lateinit var initModule: FakeInitModule
    private lateinit var coreModule: FakeCoreModule
    private lateinit var collator: PayloadMessageCollator
    private lateinit var gatingService: FakeGatingService
    private lateinit var anrService: FakeAnrService

    private enum class PayloadType {
        BACKGROUND_ACTIVITY,
        SESSION
    }

    @Before
    fun setUp() {
        initModule = FakeInitModule()
        coreModule = FakeCoreModule()
        gatingService = FakeGatingService()
        anrService = FakeAnrService().apply {
            data = listOf(fakeCompletedAnrInterval, fakeInProgressAnrInterval)
        }

        collator = V1PayloadMessageCollator(
            gatingService = gatingService,
            nativeThreadSamplerService = null,
            webViewService = FakeWebViewService(),
            preferencesService = FakePreferenceService(),
            eventService = FakeEventService(),
            logMessageService = FakeLogMessageService(),
            internalErrorService = FakeInternalErrorService().apply {
                data = LegacyExceptionError()
            },
            metadataService = FakeMetadataService(),
            performanceInfoService = FakePerformanceInfoService(),
            spanRepository = initModule.openTelemetryModule.spanRepository,
            spanSink = initModule.openTelemetryModule.spanSink,
            currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan,
            sessionPropertiesService = FakeSessionPropertiesService(),
            startupService = FakeStartupService(),
            anrOtelMapper = AnrOtelMapper(anrService),
            nativeAnrOtelMapper = NativeAnrOtelMapper(null, EmbraceSerializer()),
            logger = initModule.logger
        )
    }

    @Test
    fun `create background activity initial message`() {
        val msg = collator.buildInitialSession(
            InitialEnvelopeParams.BackgroundActivityParams(
                false,
                LifeEventType.BKGND_STATE,
                5
            )
        )
        msg.verifyInitialFieldsPopulated(PayloadType.BACKGROUND_ACTIVITY)
    }

    @Test
    fun `create session initial message`() {
        val msg = collator.buildInitialSession(
            InitialEnvelopeParams.SessionParams(
                false,
                LifeEventType.STATE,
                5
            )
        )
        msg.verifyInitialFieldsPopulated(PayloadType.SESSION)
    }

    @Test
    fun `create background activity end message`() {
        // create start message
        val startMsg = collator.buildInitialSession(
            InitialEnvelopeParams.BackgroundActivityParams(
                false,
                LifeEventType.BKGND_STATE,
                5
            )
        )
        startMsg.verifyInitialFieldsPopulated(PayloadType.BACKGROUND_ACTIVITY)

        // create session
        val payload = collator.buildFinalBackgroundActivityMessage(
            FinalEnvelopeParams.BackgroundActivityParams(
                startMsg,
                15000000000,
                LifeEventType.BKGND_STATE,
                SessionSnapshotType.NORMAL_END,
                initModule.logger,
                true,
                "crashId"
            )
        )
        payload.verifyFinalFieldsPopulated(PayloadType.BACKGROUND_ACTIVITY)
        assertEquals(1, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `create session end message`() {
        // create start message
        val startMsg = collator.buildInitialSession(
            InitialEnvelopeParams.SessionParams(
                false,
                LifeEventType.STATE,
                5
            )
        )
        startMsg.verifyInitialFieldsPopulated(PayloadType.SESSION)

        // create session
        val payload = collator.buildFinalSessionMessage(
            FinalEnvelopeParams.SessionParams(
                startMsg,
                15000000000,
                LifeEventType.STATE,
                SessionSnapshotType.NORMAL_END,
                initModule.logger,
                true,
                "crashId"
            )
        )
        payload.verifyFinalFieldsPopulated(PayloadType.SESSION)
        assertEquals(1, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `anr spans added to message`() {
        // create start message
        val startMsg = collator.buildInitialSession(
            InitialEnvelopeParams.SessionParams(
                true,
                LifeEventType.STATE,
                5
            )
        )

        // create session
        val normalEndPayload = collator.buildFinalSessionMessage(
            FinalEnvelopeParams.SessionParams(
                startMsg,
                15000000000,
                LifeEventType.STATE,
                SessionSnapshotType.NORMAL_END,
                initModule.logger,
                true,
                "crashId"
            )
        )
        val spans = checkNotNull(normalEndPayload.spans)
        assertEquals(2, spans.count { it.name == "emb-thread-blockage" })

        // create session
        val cacheEndPayload = collator.buildFinalSessionMessage(
            FinalEnvelopeParams.SessionParams(
                startMsg,
                15000000000,
                LifeEventType.STATE,
                SessionSnapshotType.PERIODIC_CACHE,
                initModule.logger,
                false,
                "crashId"
            )
        )
        assertNull(cacheEndPayload.spans)
    }

    private fun SessionMessage.verifyFinalFieldsPopulated(
        payloadType: PayloadType
    ) {
        assertNotNull(deviceInfo)
        assertNotNull(performanceInfo)
        session.verifyInitialFieldsPopulated(payloadType)
        session.verifyFinalFieldsPopulated(payloadType)
    }

    private fun Session.verifyInitialFieldsPopulated(payloadType: PayloadType) {
        assertNotNull(sessionId)
        assertEquals(5L, startTime)
        assertFalse(isColdStart)
        assertNotNull(number)

        val expectedState = when (payloadType) {
            PayloadType.BACKGROUND_ACTIVITY -> Session.APPLICATION_STATE_BACKGROUND
            PayloadType.SESSION -> Session.APPLICATION_STATE_FOREGROUND
        }
        val expectedStartType = when (payloadType) {
            PayloadType.BACKGROUND_ACTIVITY -> LifeEventType.BKGND_STATE
            PayloadType.SESSION -> LifeEventType.STATE
        }
        val expectedSessionProps = when (payloadType) {
            PayloadType.BACKGROUND_ACTIVITY -> null
            PayloadType.SESSION -> emptyMap<String, String>()
        }
        assertEquals(expectedState, appState)
        assertEquals(expectedStartType, startType)
        assertEquals(Session.MESSAGE_TYPE_END, messageType)
        assertEquals(expectedSessionProps, properties)
    }

    private fun Session.verifyFinalFieldsPopulated(payloadType: PayloadType) {
        val expectedEndType = when (payloadType) {
            PayloadType.BACKGROUND_ACTIVITY -> LifeEventType.BKGND_STATE
            PayloadType.SESSION -> LifeEventType.STATE
        }
        assertEquals(15000000000L, endTime)
        assertEquals(expectedEndType, endType)
        assertEquals(15000000000L, lastHeartbeatTime)
        assertEquals("crashId", crashReportId)
        assertNotNull(eventIds)
        assertNotNull(infoLogIds)
        assertNotNull(warningLogIds)
        assertNotNull(errorLogIds)
        assertNotNull(infoLogsAttemptedToSend)
        assertNotNull(warnLogsAttemptedToSend)
        assertNotNull(errorLogsAttemptedToSend)
        assertNotNull(exceptionError)
        assertNotNull(unhandledExceptions)
    }
}
