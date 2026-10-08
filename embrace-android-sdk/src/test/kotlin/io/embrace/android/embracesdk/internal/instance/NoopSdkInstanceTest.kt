package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.otel.spans.NoopEmbraceSdkSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class NoopSdkInstanceTest {

    private lateinit var logger: FakeInternalLogger
    private lateinit var instance: NoopSdkInstance

    @Before
    fun setUp() {
        logger = FakeInternalLogger()
        instance = NoopSdkInstance(logger, NoopSdkInstance.SDK_NOT_INITIALIZED)
    }

    @Test
    fun `reports a stopped SDK`() {
        assertFalse(instance.isStarted)
        assertEquals("", instance.deviceId)
        assertNull(instance.currentUserSessionId)
        assertEquals(LastRunEndState.INVALID, instance.lastRunEndState)
        assertTrue(logger.infoMessages.isEmpty())
    }

    @Test
    fun `records nothing`() {
        assertFalse(instance.addUserSessionProperty("key", "value", PropertyScope.PERMANENT))
        assertFalse(instance.recordCompletedSpan("span", 1, 2))
        assertSame(NoopEmbraceSdkSpan, instance.startSpan("span"))
        assertNull(instance.getSpan("id"))
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
    fun `logs not initialized message once`() {
        instance.addBreadcrumb("crumb")
        instance.logInfo("msg")
        assertEquals(
            "Embrace SDK is not initialized yet, cannot add_breadcrumb.",
            logger.infoMessages.single().msg,
        )
    }

    @Test
    fun `logs disabled message`() {
        val disabled = NoopSdkInstance(logger, NoopSdkInstance.SDK_DISABLED)
        disabled.addBreadcrumb("crumb")
        assertEquals("Embrace SDK is disabled, cannot add_breadcrumb.", logger.infoMessages.single().msg)
    }
}
