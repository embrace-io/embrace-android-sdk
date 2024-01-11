package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.FakePerformanceInfoService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeThermalStatusService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.FakeWebViewService
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import io.embrace.android.embracesdk.session.PayloadMessageCollator.PayloadType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class PayloadMessageCollatorTest {

    private lateinit var collator: PayloadMessageCollator

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
            internalErrorService = FakeInternalErrorService(),
            breadcrumbService = FakeBreadcrumbService(),
            metadataService = FakeAndroidMetadataService(),
            performanceInfoService = FakePerformanceInfoService(),
            spansService = SpansService.Companion.featureDisabledSpansService,
            clock = FakeClock()
        )
    }

    @Test
    fun `create background activity initial message`() {
        val msg = collator.buildInitialSession(
            PayloadType.BACKGROUND_ACTIVITY,
            false,
            LifeEventType.BKGND_STATE,
            5,
            null
        )
        msg.verifyInitialFieldsPopulated(PayloadType.BACKGROUND_ACTIVITY)
    }

    @Test
    fun `create session initial message`() {
        val msg = collator.buildInitialSession(
            PayloadType.SESSION,
            false,
            LifeEventType.STATE,
            5,
            null
        )
        msg.verifyInitialFieldsPopulated(PayloadType.SESSION)
    }

    @Test
    fun `create background activity end message`() {
        // create start message
        val startMsg = collator.buildInitialSession(
            PayloadType.BACKGROUND_ACTIVITY,
            false,
            LifeEventType.BKGND_STATE,
            5,
            null
        )
        startMsg.verifyInitialFieldsPopulated(PayloadType.BACKGROUND_ACTIVITY)

        // create stop message
        val msg = collator.createBackgroundActivityEndMessage(startMsg, 10, LifeEventType.BKGND_STATE, "crashId")
        msg.verifyInitialFieldsPopulated(PayloadType.BACKGROUND_ACTIVITY)

        with(msg) {
            assertEquals(LifeEventType.BKGND_STATE, endType)
            assertEquals(10L, endTime)
            assertEquals("crashId", crashReportId)
        }

        // create envelope
        with(checkNotNull(collator.buildBgActivityMessage(msg, true))) {
            assertSame(msg, session)
            assertNotNull(userInfo)
            assertNotNull(appInfo)
            assertNotNull(deviceInfo)
            assertNotNull(performanceInfo)
            assertNotNull(breadcrumbs)
        }
    }

    private fun Session.verifyInitialFieldsPopulated(payloadType: PayloadType) {
        assertNotNull(sessionId)
        assertEquals(5L, startTime)
        assertFalse(isColdStart)
        assertNotNull(user)
        assertNotNull(number)

        val expectedState = when (payloadType) {
            PayloadType.BACKGROUND_ACTIVITY -> Session.APPLICATION_STATE_BACKGROUND
            PayloadType.SESSION -> Session.APPLICATION_STATE_FOREGROUND
        }
        val expectedStartType = when (payloadType) {
            PayloadType.BACKGROUND_ACTIVITY -> LifeEventType.BKGND_STATE
            PayloadType.SESSION -> LifeEventType.STATE
        }
        assertEquals(expectedState, appState)
        assertEquals(expectedStartType, startType)
    }
}
