package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.otel.spans.NoopEmbraceSdkSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class NoopSdkInstanceTest {

    private lateinit var clock: FakeClock
    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var instance: NoopSdkInstance

    @Before
    fun setUp() {
        clock = FakeClock(currentTime = 1000L)
        errorHandler = FakeErrorHandler()
        instance = NoopSdkInstance(clock, errorHandler, NoopSdkInstance.SDK_NOT_INITIALIZED)
    }

    @Test
    fun `reports a stopped SDK`() {
        assertFalse(instance.isStarted)
        assertEquals("", instance.deviceId)
        assertNull(instance.currentUserSessionId)
        assertEquals(LastRunEndState.INVALID, instance.lastRunEndState)
        assertTrue(errorHandler.infoMessages.isEmpty())
    }

    @Test
    fun `records nothing`() {
        assertFalse(instance.addUserSessionProperty("key", "value", PropertyScope.PERMANENT))
        assertFalse(instance.recordCompletedSpan("span", 1, 2))
        assertSame(NoopEmbraceSdkSpan, instance.startSpan("span"))
        assertNull(instance.getSpan("id"))
    }

    @Test
    fun `getSdkCurrentTimeMs returns the clock time`() {
        assertEquals(1000L, instance.getSdkCurrentTimeMs())
    }

    @Test
    fun `recordSpan still runs the code`() {
        assertEquals(42, instance.recordSpan("span") { 42 })
    }

    @Test
    fun `created experiments keep their values`() {
        val experiment = instance.createExperiment("id", "variant", 5)
        assertEquals("id", experiment.id)
        assertEquals("variant", experiment.variant)
        assertEquals(5L, experiment.startedAt)
    }

    @Test
    fun `logs not initialized warning once`() {
        instance.addBreadcrumb("crumb")
        instance.logInfo("msg")
        val msg = errorHandler.warningMessages.single()
        assertEquals("Embrace SDK is not initialized yet, cannot add_breadcrumb.", msg.msg)
        assertEquals(msg.msg, msg.throwable?.message)
        assertTrue(errorHandler.infoMessages.isEmpty())
    }

    @Test
    fun `deviceId logs a dropped call`() {
        assertEquals("", instance.deviceId)
        assertEquals(
            "Embrace SDK is not initialized yet, cannot get_device_id.",
            errorHandler.warningMessages.single().msg,
        )
    }

    @Test
    fun `logs disabled message`() {
        val disabled = NoopSdkInstance(clock, errorHandler, NoopSdkInstance.SDK_DISABLED)
        disabled.addBreadcrumb("crumb")
        assertEquals(
            "Embrace SDK is disabled, cannot add_breadcrumb. Subsequent dropped calls will not be logged.",
            errorHandler.infoMessages.single().msg,
        )
        assertTrue(errorHandler.warningMessages.isEmpty())
    }
}
