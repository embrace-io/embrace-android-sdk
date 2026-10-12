package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.InternalInterfaceApi
import io.embrace.android.embracesdk.internal.NoopInternalInterfaceApi
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
        val holder = createHolder(instance)
        assertEquals(SdkState.NOT_STARTED, holder.state)
        assertSame(instance, holder.dispatcher.target)
    }

    @Test
    fun `successful start`() {
        val instance = FakeInstance(calls)
        val holder = createHolder(instance)
        holder.start(context)
        holder.dispatcher.logInfo("message")
        assertEquals(SdkState.STARTED, holder.state)
        assertSame(instance, holder.dispatcher.target)
        assertEquals(listOf("start", "logInfo/1"), calls)
    }

    @Test
    fun `repeated start is ignored`() {
        val holder = createHolder(FakeInstance(calls))
        holder.start(context)
        holder.start(context)
        assertEquals(SdkState.STARTED, holder.state)
        assertEquals(listOf("start"), calls)
    }

    @Test
    fun `unsuccessful start can be retried`() {
        val instance = FakeInstance(calls, startSucceeds = false)
        val holder = createHolder(instance)
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
        val holder = createHolder(instance)
        holder.start(context)
        assertEquals(SdkState.NOT_STARTED, holder.state)
        assertSame(instance, holder.dispatcher.target)
        assertEquals("start failed", logger.internalErrorMessages.single().throwable?.message)
    }

    @Test
    fun `disable after start`() {
        val instance = FakeInstance(calls)
        val holder = createHolder(instance)
        holder.start(context)
        holder.disable()
        holder.dispatcher.logInfo("message")
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(instance, holder.dispatcher.target)
        assertEquals(listOf("start", "disable", "logInfo/1"), calls)
    }

    @Test
    fun `repeated disable is ignored`() {
        val holder = createHolder(FakeInstance(calls))
        holder.start(context)
        holder.disable()
        holder.disable()
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("start", "disable"), calls)
    }

    @Test
    fun `start after disable is ignored`() {
        val holder = createHolder(FakeInstance(calls))
        holder.start(context)
        holder.disable()
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("start", "disable"), calls)
    }

    @Test
    fun `disable before start is terminal`() {
        val holder = createHolder(FakeInstance(calls))
        holder.disable()
        assertEquals(SdkState.DISABLED, holder.state)
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `disable after unsuccessful start is terminal`() {
        val instance = FakeInstance(calls, startSucceeds = false)
        val holder = createHolder(instance)
        holder.start(context)
        holder.disable()
        assertEquals(SdkState.DISABLED, holder.state)
        instance.startSucceeds = true
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("start"), calls)
    }

    @Test
    fun `failed disable can be retried`() {
        val instance = FakeInstance(calls, disableThrows = true)
        val holder = createHolder(instance)
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
        val holder = createHolder(FakeInstance(calls))
        holder.api.start(context)
        assertEquals(SdkState.STARTED, holder.state)
        holder.api.logInfo("message")
        holder.api.disable()
        assertEquals(SdkState.DISABLED, holder.state)
        holder.api.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("start", "logInfo/1", "disable"), calls)
    }

    @Test
    fun `internal interface api follows state`() {
        val internalInterfaceApi = FakeInternalInterfaceApi()
        EmbraceInternalApi.internalInterfaceApi = internalInterfaceApi
        val instance = FakeInstance(calls, startSucceeds = false)
        val holder = createHolder(instance, internalInterfaceApi)
        assertSame(NoopInternalInterfaceApi, EmbraceInternalApi.internalInterfaceApi)

        holder.start(context)
        assertSame(NoopInternalInterfaceApi, EmbraceInternalApi.internalInterfaceApi)

        instance.startSucceeds = true
        holder.start(context)
        assertSame(internalInterfaceApi, EmbraceInternalApi.internalInterfaceApi)

        holder.disable()
        assertSame(NoopInternalInterfaceApi, EmbraceInternalApi.internalInterfaceApi)

        holder.start(context)
        assertSame(NoopInternalInterfaceApi, EmbraceInternalApi.internalInterfaceApi)
    }

    private fun createHolder(
        instance: SdkApi,
        internalInterfaceApi: InternalInterfaceApi = NoopInternalInterfaceApi,
    ) = SdkStateHolder(
        instance = { instance },
        preStart = { instance },
        disabled = { instance },
        telemetryService = FakeInternalTelemetryService(),
        errorHandler = logger,
        internalInterfaceApi = internalInterfaceApi,
    )

    private class FakeInternalInterfaceApi : InternalInterfaceApi by NoopInternalInterfaceApi

    private class FakeInstance(
        private val calls: MutableList<String>,
        var startSucceeds: Boolean = true,
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
