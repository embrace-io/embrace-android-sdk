package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.internal.api.SdkApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SdkStateHolderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calls = mutableListOf<String>()
    private val logger = FakeInternalLogger(throwOnInternalError = false)

    @Test
    fun `initial state`() {
        val instance = FakeInstance(calls)
        val holder = SdkStateHolder(instance, FakeInternalTelemetryService(), logger)
        assertEquals(SdkState.NOT_STARTED, holder.state)
        assertSame(instance, holder.dispatcher.target)
    }

    @Test
    fun `successful start`() {
        val instance = FakeInstance(calls)
        val holder = SdkStateHolder(instance, FakeInternalTelemetryService(), logger)
        holder.start(context)
        holder.dispatcher.logInfo("message")
        assertEquals(SdkState.STARTED, holder.state)
        assertSame(instance, holder.dispatcher.target)
        assertEquals(listOf("start", "logInfo/1"), calls)
    }

    @Test
    fun `repeated start is ignored`() {
        val holder = SdkStateHolder(FakeInstance(calls), FakeInternalTelemetryService(), logger)
        holder.start(context)
        holder.start(context)
        assertEquals(SdkState.STARTED, holder.state)
        assertEquals(listOf("start"), calls)
    }

    @Test
    fun `unsuccessful start can be retried`() {
        val instance = FakeInstance(calls, startSucceeds = false)
        val holder = SdkStateHolder(instance, FakeInternalTelemetryService(), logger)
        holder.start(context)
        assertEquals(SdkState.NOT_STARTED, holder.state)
        holder.start(context)
        assertEquals(SdkState.NOT_STARTED, holder.state)
        assertSame(instance, holder.dispatcher.target)
        assertEquals(listOf("start", "start"), calls)
    }

    @Test
    fun `start that throws can be retried`() {
        val instance = FakeInstance(calls, startThrows = true)
        val holder = SdkStateHolder(instance, FakeInternalTelemetryService(), logger)
        holder.start(context)
        assertEquals(SdkState.NOT_STARTED, holder.state)
        assertSame(instance, holder.dispatcher.target)
        assertEquals("start failed", logger.internalErrorMessages.single().throwable?.message)
    }

    @Test
    fun `disable after start`() {
        val instance = FakeInstance(calls)
        val holder = SdkStateHolder(instance, FakeInternalTelemetryService(), logger)
        holder.start(context)
        holder.disable()
        holder.dispatcher.logInfo("message")
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(instance, holder.dispatcher.target)
        assertEquals(listOf("start", "disable", "logInfo/1"), calls)
    }

    @Test
    fun `repeated disable is ignored`() {
        val holder = SdkStateHolder(FakeInstance(calls), FakeInternalTelemetryService(), logger)
        holder.start(context)
        holder.disable()
        holder.disable()
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("start", "disable"), calls)
    }

    @Test
    fun `start after disable is allowed`() {
        val holder = SdkStateHolder(FakeInstance(calls), FakeInternalTelemetryService(), logger)
        holder.start(context)
        holder.disable()
        holder.start(context)
        assertEquals(SdkState.STARTED, holder.state)
        assertEquals(listOf("start", "disable", "start"), calls)
    }

    @Test
    fun `disable before start is ignored`() {
        val holder = SdkStateHolder(FakeInstance(calls), FakeInternalTelemetryService(), logger)
        holder.disable()
        assertEquals(SdkState.NOT_STARTED, holder.state)
        holder.start(context)
        assertEquals(SdkState.STARTED, holder.state)
        assertEquals(listOf("start"), calls)
    }

    @Test
    fun `failed disable can be retried`() {
        val instance = FakeInstance(calls, disableThrows = true)
        val holder = SdkStateHolder(instance, FakeInternalTelemetryService(), logger)
        holder.start(context)
        holder.disable()
        assertEquals(SdkState.STARTED, holder.state)
        assertEquals("disable failed", logger.internalErrorMessages.single().throwable?.message)
        instance.disableThrows = false
        holder.disable()
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("start", "disable", "disable"), calls)
    }

    @Test
    fun `api routes lifecycle calls through the holder`() {
        val holder = SdkStateHolder(FakeInstance(calls), FakeInternalTelemetryService(), logger)
        holder.api.disable()
        assertEquals(SdkState.NOT_STARTED, holder.state)
        holder.api.start(context)
        assertEquals(SdkState.STARTED, holder.state)
        holder.api.logInfo("message")
        holder.api.disable()
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("start", "logInfo/1", "disable"), calls)
    }

    private class FakeInstance(
        private val calls: MutableList<String>,
        private val startSucceeds: Boolean = true,
        private val startThrows: Boolean = false,
        var disableThrows: Boolean = false,
    ) : SdkApi by recordingSdkApi(calls) {

        private var started = false

        override val isStarted: Boolean get() = started

        override fun start(context: Context) {
            calls += "start"
            check(!startThrows) { "start failed" }
            if (startSucceeds) {
                started = true
            }
        }

        override fun disable() {
            calls += "disable"
            check(!disableThrows) { "disable failed" }
            started = false
        }
    }
}
