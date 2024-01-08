package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.FakePerformanceInfoService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.LifeEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class BackgroundActivityCollatorTest {

    private lateinit var collator: BackgroundActivityCollator

    @Before
    fun setUp() {
        collator = BackgroundActivityCollator(
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
    fun createStartMessage() {
        val msg = collator.createStartMessage(5, false, LifeEventType.BKGND_STATE)
        msg.verifyStartFieldsPopulated()
    }

    @Test
    fun createStartAndStopMessages() {
        // create start message
        val startMsg = collator.createStartMessage(5, false, LifeEventType.BKGND_STATE)
        startMsg.verifyStartFieldsPopulated()

        // create stop message
        val msg = collator.createStopMessage(startMsg, 10, LifeEventType.BKGND_STATE, "crashId")
        msg.verifyStartFieldsPopulated()

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

    private fun Session.verifyStartFieldsPopulated() {
        assertNotNull(sessionId)
        assertEquals(5L, startTime)
        assertEquals(EmbraceBackgroundActivityService.APPLICATION_STATE_BACKGROUND, appState)
        assertFalse(checkNotNull(isColdStart))
        assertEquals(LifeEventType.BKGND_STATE, startType)
        assertNotNull(user)
        assertNotNull(number)
    }
}
