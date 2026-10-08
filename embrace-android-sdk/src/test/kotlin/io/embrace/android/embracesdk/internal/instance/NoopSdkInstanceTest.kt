package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.internal.otel.spans.NoopEmbraceSdkSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

internal class NoopSdkInstanceTest {

    @Test
    fun `reports a stopped SDK`() {
        assertFalse(NoopSdkInstance.isStarted)
        assertEquals("", NoopSdkInstance.deviceId)
        assertNull(NoopSdkInstance.currentUserSessionId)
        assertEquals(LastRunEndState.INVALID, NoopSdkInstance.lastRunEndState)
    }

    @Test
    fun `records nothing`() {
        assertFalse(NoopSdkInstance.addUserSessionProperty("key", "value", PropertyScope.PERMANENT))
        assertFalse(NoopSdkInstance.recordCompletedSpan("span", 1, 2))
        assertSame(NoopEmbraceSdkSpan, NoopSdkInstance.startSpan("span"))
        assertNull(NoopSdkInstance.getSpan("id"))
    }

    @Test
    fun `recordSpan still runs the code`() {
        assertEquals(42, NoopSdkInstance.recordSpan("span") { 42 })
    }

    @Test
    fun `created experiments keep their values`() {
        val experiment = NoopSdkInstance.createExperiment("id", "variant", 5)
        assertEquals("id", experiment.id)
        assertEquals("variant", experiment.variant)
        assertEquals(5L, experiment.startedAt)
    }
}
