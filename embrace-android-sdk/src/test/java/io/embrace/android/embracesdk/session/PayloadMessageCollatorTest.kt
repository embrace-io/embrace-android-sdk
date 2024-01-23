package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.FakePerformanceInfoService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.fakes.FakeThermalStatusService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.FakeWebViewService
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.payload.ExceptionError
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

internal class PayloadMessageCollatorTest {

    private lateinit var collator: PayloadMessageCollator

    private enum class PayloadType {
        BACKGROUND_ACTIVITY,
        SESSION
    }

    @Before
    fun setUp() {
        collator = PayloadMessageCollator(
            configService = FakeConfigService(),
            nativeThreadSamplerService = null,
            thermalStatusService = FakeThermalStatusService(),
            webViewService = FakeWebViewService(),
            userService = FakeUserService(),
            preferencesService = FakePreferenceService(),
            eventService = FakeEventService(),
            logMessageService = FakeLogMessageService(),
            internalErrorService = FakeInternalErrorService().apply { currentExceptionError = ExceptionError() },
            breadcrumbService = FakeBreadcrumbService(),
            metadataService = FakeAndroidMetadataService(),
            performanceInfoService = FakePerformanceInfoService(),
            spansService = SpansService.Companion.featureDisabledSpansService,
            clock = FakeClock(),
            sessionPropertiesService = FakeSessionPropertiesService(),
            startupService = FakeStartupService()
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

        // create envelope
        val payload = collator.buildFinalBackgroundActivityMessage(
            FinalEnvelopeParams.BackgroundActivityParams(
                startMsg,
                15000000000,
                LifeEventType.BKGND_STATE,
                "crashId"
            )
        )
        payload.verifyFinalFieldsPopulated(PayloadType.BACKGROUND_ACTIVITY)
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

        // create envelope
        val payload = collator.buildFinalSessionMessage(
            FinalEnvelopeParams.SessionParams(
                startMsg,
                15000000000,
                LifeEventType.STATE,
                "crashId",
                SessionSnapshotType.NORMAL_END
            )
        )
        payload.verifyFinalFieldsPopulated(PayloadType.SESSION)
    }

    private fun SessionMessage.verifyFinalFieldsPopulated(
        payloadType: PayloadType
    ) {
        assertNotNull(userInfo)
        assertNotNull(appInfo)
        assertNotNull(deviceInfo)
        assertNotNull(performanceInfo)
        assertNotNull(breadcrumbs)
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
