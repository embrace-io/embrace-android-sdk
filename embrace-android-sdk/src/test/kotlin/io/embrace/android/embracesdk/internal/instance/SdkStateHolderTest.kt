package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.NoopInternalInterfaceApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SdkStateHolderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calls = mutableListOf<String>()
    private val logger = FakeInternalLogger(throwOnInternalError = false)
    private val buffer = BufferingSdkInstance({ 0 }, logger)
    private val failed = FakeInstance(calls, prefix = "failed:")
    private val disabled = FakeInstance(calls, prefix = "disabled:")

    @Test
    fun `initial state`() {
        val instance = FakeInstance(calls)
        val holder = createHolder(instance)
        assertEquals(SdkState.NOT_STARTED, holder.state)
        assertSame(buffer, holder.dispatcher.target)
    }

    @Test
    fun `calls before start are not routed to the instance`() {
        val holder = createHolder(FakeInstance(calls))
        holder.dispatcher.logInfo("message")
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `successful start`() {
        val instance = FakeInstance(calls)
        val holder = createHolder(instance)
        holder.start(context)
        holder.dispatcher.logInfo("message")
        assertEquals(SdkState.STARTED, holder.state)
        assertSame(instance, holder.dispatcher.target)
        assertEquals(listOf("instantiate", "start", "onPostStart", "logInfo/1"), calls)
    }

    @Test
    fun `repeated start is ignored`() {
        val holder = createHolder(FakeInstance(calls))
        holder.start(context)
        holder.start(context)
        assertEquals(SdkState.STARTED, holder.state)
        assertEquals(listOf("instantiate", "start", "onPostStart"), calls)
    }

    @Test
    fun `unsuccessful start is not retried`() {
        val instance = FakeInstance(calls, startSucceeds = false)
        val holder = createHolder(instance)
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        instance.startSucceeds = true
        holder.start(context)
        holder.dispatcher.logInfo("message")
        assertEquals(SdkState.FAILED, holder.state)
        assertSame(failed, holder.dispatcher.target)
        assertEquals(listOf("instantiate", "start", "close", "failed:onPostStart", "failed:logInfo/1"), calls)
    }

    @Test
    fun `start that throws is not retried`() {
        val instance = FakeInstance(calls, startThrows = true)
        val holder = createHolder(instance)
        holder.start(context)
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        assertSame(failed, holder.dispatcher.target)
        assertEquals(listOf("instantiate", "start", "close", "failed:onPostStart"), calls)
        assertEquals("start failed", logger.internalErrorMessages.single().throwable?.message)
    }

    @Test
    fun `start fails even if disabling the instance fails`() {
        val instance = FakeInstance(calls, startSucceeds = false, closeThrows = true)
        val holder = createHolder(instance)
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        assertSame(failed, holder.dispatcher.target)
        assertEquals(listOf("instantiate", "start", "close", "failed:onPostStart"), calls)
        assertEquals("close failed", logger.internalErrorMessages.single().throwable?.message)
    }

    @Test
    fun `buffer is handed over to the failed instance`() {
        val instance = FakeInstance(calls, startSucceeds = false)
        createHolder(instance).start(context)
        assertEquals(emptyList<BufferingSdkInstance>(), instance.handedOver)
        assertEquals(listOf(buffer), failed.handedOver)
    }

    @Test
    fun `start fails even if the handover to the failed instance fails`() {
        failed.postStartThrows = true
        val holder = createHolder(FakeInstance(calls, startSucceeds = false))
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        assertSame(failed, holder.dispatcher.target)
        assertEquals("post start failed", logger.internalErrorMessages.single().throwable?.message)
    }

    @Test
    fun `close after start`() {
        val instance = FakeInstance(calls)
        val holder = createHolder(instance)
        holder.start(context)
        holder.close()
        holder.dispatcher.logInfo("message")
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.dispatcher.target)
        assertEquals(listOf("instantiate", "start", "onPostStart", "close", "disabled:logInfo/1"), calls)
    }

    @Test
    fun `repeated close is ignored`() {
        val holder = createHolder(FakeInstance(calls))
        holder.start(context)
        holder.close()
        holder.close()
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("instantiate", "start", "onPostStart", "close"), calls)
    }

    @Test
    fun `start after close is ignored`() {
        val holder = createHolder(FakeInstance(calls))
        holder.start(context)
        holder.close()
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("instantiate", "start", "onPostStart", "close"), calls)
    }

    @Test
    fun `close before start is terminal`() {
        val holder = createHolder(FakeInstance(calls))
        holder.close()
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.dispatcher.target)
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.dispatcher.target)
        assertEquals(listOf("disabled:onPostStart"), calls)
    }

    @Test
    fun `close after unsuccessful start is terminal`() {
        val instance = FakeInstance(calls, startSucceeds = false)
        val holder = createHolder(instance)
        holder.start(context)
        holder.close()
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.dispatcher.target)
        instance.startSucceeds = true
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("instantiate", "start", "close", "failed:onPostStart"), calls)
    }

    @Test
    fun `failed close is still terminal`() {
        val instance = FakeInstance(calls, closeThrows = true)
        val holder = createHolder(instance)
        holder.start(context)
        holder.close()
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.dispatcher.target)
        assertEquals("close failed", logger.internalErrorMessages.single().throwable?.message)
        holder.close()
        assertEquals(listOf("instantiate", "start", "onPostStart", "close"), calls)
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
        assertEquals(listOf("instantiate", "start", "onPostStart", "logInfo/1", "close"), calls)
    }

    @Test
    fun `internal interface api follows the dispatcher target`() {
        val holder = createHolder(FakeInstance(calls))
        assertSame(buffer, EmbraceInternalApi.internalInterfaceApi)
        assertSame(NoopInternalInterfaceApi.internalInterface, EmbraceInternalApi.internalInterface)

        holder.start(context)
        calls.clear()
        EmbraceInternalApi.internalInterface
        assertEquals(listOf("getInternalInterface/0"), calls)

        holder.close()
        calls.clear()
        EmbraceInternalApi.internalInterface
        assertEquals(listOf("disabled:getInternalInterface/0"), calls)
    }

    @Test
    fun `internal interface api follows the failed instance`() {
        val holder = createHolder(FakeInstance(calls, startSucceeds = false))
        holder.start(context)
        calls.clear()
        EmbraceInternalApi.internalInterface
        assertEquals(listOf("failed:getInternalInterface/0"), calls)
    }

    @Test
    fun `buffer is handed over to the started instance`() {
        val instance = FakeInstance(calls)
        createHolder(instance).start(context)
        assertEquals(listOf(buffer), instance.handedOver)
    }

    @Test
    fun `buffer is handed over to the disabled instance`() {
        val instance = FakeInstance(calls)
        createHolder(instance).close()
        assertEquals(emptyList<BufferingSdkInstance>(), instance.handedOver)
        assertEquals(listOf(buffer), disabled.handedOver)
    }

    @Test
    fun `buffer is not handed over when disabling a started instance`() {
        val instance = FakeInstance(calls)
        val holder = createHolder(instance)
        holder.start(context)
        holder.close()
        assertEquals(emptyList<BufferingSdkInstance>(), disabled.handedOver)
    }

    @Test
    fun `failed post start hook still starts the sdk`() {
        val instance = FakeInstance(calls, postStartThrows = true)
        val holder = createHolder(instance)
        holder.start(context)
        assertEquals(SdkState.STARTED, holder.state)
        assertEquals("post start failed", logger.internalErrorMessages.single().throwable?.message)
    }

    @Test
    fun `failed instantiation is not retried`() {
        var instance: SdkInstance? = null
        val holder = createHolder { instance }
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        assertSame(failed, holder.dispatcher.target)

        instance = FakeInstance(calls)
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        assertSame(failed, holder.dispatcher.target)
        assertEquals(listOf("instantiate", "failed:onPostStart"), calls)
    }

    @Test
    fun `instantiation that throws is not retried`() {
        val instance = FakeInstance(calls)
        var instantiationThrows = true
        val holder = createHolder {
            check(!instantiationThrows) { "instantiation failed" }
            instance
        }
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        assertSame(failed, holder.dispatcher.target)
        assertEquals("instantiation failed", logger.internalErrorMessages.single().throwable?.message)

        instantiationThrows = false
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        assertSame(failed, holder.dispatcher.target)
    }

    @Test
    fun `instance is never instantiated if disabled before start`() {
        val holder = createHolder(FakeInstance(calls))
        holder.close()
        holder.start(context)
        assertEquals(listOf("disabled:onPostStart"), calls)
    }

    @Test
    fun `close before start is terminal even if the handover fails`() {
        disabled.postStartThrows = true
        val holder = createHolder(FakeInstance(calls))
        holder.close()
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.dispatcher.target)
        assertEquals("post start failed", logger.internalErrorMessages.single().throwable?.message)
    }

    private fun createHolder(instance: SdkInstance) = createHolder { instance }

    private fun createHolder(instanceFactory: () -> SdkInstance?) = SdkStateHolder(
        instanceFactory = {
            calls += "instantiate"
            instanceFactory()
        },
        preStart = { buffer },
        failed = { failed },
        disabled = { disabled },
        telemetryService = FakeInternalTelemetryService(),
        errorHandler = logger,
    )

    private class FakeInstance(
        private val calls: MutableList<String>,
        var startSucceeds: Boolean = true,
        private val startThrows: Boolean = false,
        val closeThrows: Boolean = false,
        var postStartThrows: Boolean = false,
        private val prefix: String = "",
    ) : SdkInstance, InternalSdkApi by recordingSdkApi(calls, prefix) {

        private var started = false

        val handedOver = mutableListOf<BufferingSdkInstance>()

        override fun onPostStart(buffer: BufferingSdkInstance) {
            calls += "${prefix}onPostStart"
            handedOver += buffer
            check(!postStartThrows) { "post start failed" }
        }

        override val isStarted: Boolean get() = started

        override fun start(context: Context) {
            calls += "start"
            check(!startThrows) { "start failed" }
            if (startSucceeds) {
                started = true
            }
        }

        override fun close() {
            calls += "close"
            check(!closeThrows) { "close failed" }
            started = false
        }
    }
}
