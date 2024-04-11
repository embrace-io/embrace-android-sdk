package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.FakeSessionPropertiesService
import io.embrace.android.embracesdk.anr.AnrOtelMapper
import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePerformanceInfoService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.fakes.FakeThermalStatusService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.FakeWebViewService
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.payload.LegacyExceptionError
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class V2PayloadMessageCollatorTest {

    private lateinit var initModule: FakeInitModule
    private lateinit var coreModule: FakeCoreModule
    private lateinit var v1collator: V1PayloadMessageCollator
    private lateinit var v2collator: V2PayloadMessageCollator
    private lateinit var gatingService: FakeGatingService

    private enum class PayloadType {
        BACKGROUND_ACTIVITY,
        SESSION
    }

    @Before
    fun setUp() {
        initModule = FakeInitModule()
        coreModule = FakeCoreModule()
        gatingService = FakeGatingService()
        v1collator = V1PayloadMessageCollator(
            gatingService = gatingService,
            configService = FakeConfigService(),
            nativeThreadSamplerService = null,
            thermalStatusService = FakeThermalStatusService(),
            webViewService = FakeWebViewService(),
            userService = FakeUserService(),
            preferencesService = FakePreferenceService(),
            eventService = FakeEventService(),
            logMessageService = FakeLogMessageService(),
            internalErrorService = FakeInternalErrorService().apply { currentExceptionError = LegacyExceptionError() },
            breadcrumbService = FakeBreadcrumbService(),
            metadataService = FakeMetadataService(),
            performanceInfoService = FakePerformanceInfoService(),
            spanRepository = initModule.openTelemetryModule.spanRepository,
            spanSink = initModule.openTelemetryModule.spanSink,
            currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan,
            sessionPropertiesService = FakeSessionPropertiesService(),
            startupService = FakeStartupService(),
            anrOtelMapper = AnrOtelMapper(FakeAnrService()),
            logger = initModule.logger
        )
        val sessionEnvelopeSource = SessionEnvelopeSourceImpl(
            metadataSource = FakeEnvelopeMetadataSource(),
            resourceSource = FakeEnvelopeResourceSource(),
            sessionPayloadSource = FakeSessionPayloadSource()
        )
        v2collator = V2PayloadMessageCollator(gatingService, v1collator, sessionEnvelopeSource, initModule.logger)
    }

    @Test
    fun `create background activity initial message`() {
        val msg = v2collator.buildInitialSession(
            InitialEnvelopeParams.BackgroundActivityParams(
                false,
                Session.LifeEventType.BKGND_STATE,
                5
            )
        )
        msg.verifyInitialFieldsPopulated(PayloadType.BACKGROUND_ACTIVITY)
    }

    @Test
    fun `create session initial message`() {
        val msg = v2collator.buildInitialSession(
            InitialEnvelopeParams.SessionParams(
                false,
                Session.LifeEventType.STATE,
                5
            )
        )
        msg.verifyInitialFieldsPopulated(PayloadType.SESSION)
    }

    @Test
    fun `create background activity end message`() {
        // create start message
        val startMsg = v2collator.buildInitialSession(
            InitialEnvelopeParams.BackgroundActivityParams(
                false,
                Session.LifeEventType.BKGND_STATE,
                5
            )
        )
        startMsg.verifyInitialFieldsPopulated(PayloadType.BACKGROUND_ACTIVITY)

        // create session
        val payload = v2collator.buildFinalBackgroundActivityMessage(
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
        payload.verifyFinalFieldsPopulated(PayloadType.BACKGROUND_ACTIVITY)
        assertEquals(1, gatingService.envelopesFiltered.size)
    }

    @Test
    fun `create session end message`() {
        // create start message
        val startMsg = v2collator.buildInitialSession(
            InitialEnvelopeParams.SessionParams(
                false,
                Session.LifeEventType.STATE,
                5
            )
        )
        startMsg.verifyInitialFieldsPopulated(PayloadType.SESSION)

        // create session
        val payload = v2collator.buildFinalSessionMessage(
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
        payload.verifyFinalFieldsPopulated(PayloadType.SESSION)
        assertEquals(1, gatingService.envelopesFiltered.size)
    }

    private fun SessionMessage.verifyFinalFieldsPopulated(
        payloadType: PayloadType
    ) {
        assertNull(userInfo)
        assertNull(version)
        assertNull(spans)
        assertNotNull(appInfo)
        assertNotNull(deviceInfo)
        assertNotNull(performanceInfo)
        assertNotNull(breadcrumbs)
        assertNotNull(resource)
        assertNotNull(metadata)
        assertNotNull(data)
        assertNotNull(newVersion)
        assertNotNull(type)
        session.verifyInitialFieldsPopulated(payloadType)
        session.verifyFinalFieldsPopulated(payloadType)
    }

    private fun Session.verifyInitialFieldsPopulated(payloadType: PayloadType) {
        assertNotNull(sessionId)
        assertEquals(5L, startTime)
        Assert.assertFalse(isColdStart)
        assertNotNull(number)

        val expectedState = when (payloadType) {
            PayloadType.BACKGROUND_ACTIVITY -> Session.APPLICATION_STATE_BACKGROUND
            PayloadType.SESSION -> Session.APPLICATION_STATE_FOREGROUND
        }
        val expectedStartType = when (payloadType) {
            PayloadType.BACKGROUND_ACTIVITY -> Session.LifeEventType.BKGND_STATE
            PayloadType.SESSION -> Session.LifeEventType.STATE
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
            PayloadType.BACKGROUND_ACTIVITY -> Session.LifeEventType.BKGND_STATE
            PayloadType.SESSION -> Session.LifeEventType.STATE
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
