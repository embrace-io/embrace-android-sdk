package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.logging.BufferedInternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.otel.spans.NoopEmbraceSdkSpan
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
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
    fun `onPostStart drains buffered calls`() {
        val buffer = createBuffer()
        instance.onPostStart(buffer)
        assertEquals(emptyList<String>(), drainRemaining(buffer))
    }

    @Test
    fun `calls after onPostStart are not buffered`() {
        val buffer = BufferingSdkInstance({ 0 }, logger)
        instance.onPostStart(buffer)
        buffer.setResourceAttribute("key", "value")
        buffer.recordCompletedSpan("span", 1, 2)
        buffer.trackExperiment("exp", "variant", 1)
        assertEquals(emptyList<String>(), drainRemaining(buffer))
    }

    @Test
    fun `onPostStart discards buffered internal errors`() {
        val handler = BufferedInternalErrorHandler(FakeClock())
        val buffer = BufferingSdkInstance({ 0 }, logger, handler)
        handler.trackInternalError(InternalErrorType.DeliverySchedulingFail, IllegalStateException())
        instance.onPostStart(buffer)
        handler.trackInternalError(InternalErrorType.DeliverySchedulingFail, IllegalStateException())

        val destination = FakeTelemetryDestination()
        handler.drainTo(destination, true)
        assertTrue(destination.logEvents.isEmpty())
    }

    private fun createBuffer() = BufferingSdkInstance({ 0 }, logger).apply {
        setResourceAttribute("key", "value")
        recordCompletedSpan("span", 1, 2)
        trackExperiment("exp", "variant", 1)
    }

    /**
     * Drains whatever [buffer] still holds into a recorder and returns what was replayed.
     */
    private fun drainRemaining(buffer: BufferingSdkInstance): List<String> {
        val replayed = mutableListOf<String>()
        val recorder = object : SdkApi by instance {
            override fun setResourceAttribute(key: String, value: String) {
                replayed += key
            }

            override fun recordCompletedSpan(
                name: String,
                startTimeMs: Long,
                endTimeMs: Long,
                errorCode: ErrorCode?,
                parent: EmbraceSpan?,
                attributes: Map<String, String>,
                events: List<EmbraceSpanEvent>,
            ): Boolean {
                replayed += name
                return true
            }
        }
        buffer.drainOTelConfig(recorder)
        buffer.drainCompletedSpans(recorder)
        buffer.drainExperimentCalls(recorder) { calls -> replayed += calls.map { "exp" } }
        return replayed
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
        val msg = logger.warningMessages.single()
        assertEquals("Embrace SDK is not initialized yet, cannot add_breadcrumb.", msg.msg)
        assertEquals(msg.msg, msg.throwable?.message)
        assertTrue(logger.infoMessages.isEmpty())
        assertTrue(logger.sdkNotInitializedMessages.isEmpty())
    }

    @Test
    fun `deviceId logs a dropped call`() {
        assertEquals("", instance.deviceId)
        assertEquals(
            "Embrace SDK is not initialized yet, cannot get_device_id.",
            logger.warningMessages.single().msg,
        )
    }

    @Test
    fun `logs disabled message`() {
        val disabled = NoopSdkInstance(logger, NoopSdkInstance.SDK_DISABLED)
        disabled.addBreadcrumb("crumb")
        assertEquals(
            "Embrace SDK is disabled, cannot add_breadcrumb. Subsequent dropped calls will not be logged.",
            logger.infoMessages.single().msg,
        )
        assertTrue(logger.warningMessages.isEmpty())
    }
}
