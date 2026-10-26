package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.fakeContext
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.logging.ErrorHandlerImpl
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.concurrent.thread

internal class SdkStateHolderTest {

    private val context = fakeContext()
    private val calls = mutableListOf<String>()
    private val bufferedErrorHandler = ErrorHandlerImpl({ 0 })
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false, delegate = bufferedErrorHandler)
    private val buffer = BufferingSdkInstance({ 0 }, errorHandler)
    private val failed = FakeInstance(calls, prefix = "failed:")
    private val disabled = FakeInstance(calls, prefix = "disabled:")
    private val recordDeletion: PersistedTelemetryDeletion = { { calls += "deleteTelemetry" } }

    @Test
    fun `initial state`() {
        val instance = FakeInstance(calls)
        val holder = createHolder(instance)
        assertEquals(SdkState.NOT_STARTED, holder.state)
        assertSame(buffer, holder.api.target)
    }

    @Test
    fun `calls before start are not routed to the instance`() {
        val holder = createHolder(FakeInstance(calls))
        holder.api.logInfo("message")
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `successful start`() {
        val instance = FakeInstance(calls)
        val holder = createHolder(instance)
        holder.start(context)
        holder.api.logInfo("message")
        assertEquals(SdkState.ENABLED, holder.state)
        assertSame(instance, holder.api.target)
        assertEquals(listOf("instantiate", "enable", "onTransition", "logInfo/1"), calls)
    }

    @Test
    fun `repeated start is ignored`() {
        val holder = createHolder(FakeInstance(calls))
        holder.start(context)
        holder.start(context)
        assertEquals(SdkState.ENABLED, holder.state)
        assertEquals(listOf("instantiate", "enable", "onTransition"), calls)
    }

    @Test
    fun `unsuccessful start is not retried`() {
        val instance = FakeInstance(calls, startSucceeds = false)
        val holder = createHolder(instance)
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        instance.startSucceeds = true
        holder.start(context)
        holder.api.logInfo("message")
        assertEquals(SdkState.FAILED, holder.state)
        assertSame(failed, holder.api.target)
        assertEquals(listOf("instantiate", "enable", "close", "failed:onTransition", "failed:logInfo/1"), calls)
    }

    @Test
    fun `start that throws is not retried`() {
        val instance = FakeInstance(calls, startThrows = true)
        val holder = createHolder(instance)
        holder.start(context)
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        assertSame(failed, holder.api.target)
        assertEquals(listOf("instantiate", "enable", "close", "failed:onTransition"), calls)
        assertEquals("start failed", errorHandler.sdkErrorMessages.single().throwable?.message)
    }

    @Test
    fun `start fails even if disabling the instance fails`() {
        val instance = FakeInstance(calls, startSucceeds = false, closeThrows = true)
        val holder = createHolder(instance)
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        assertSame(failed, holder.api.target)
        assertEquals(listOf("instantiate", "enable", "close", "failed:onTransition"), calls)
        assertEquals("close failed", errorHandler.sdkErrorMessages.single().throwable?.message)
    }

    @Test
    fun `buffer is handed over to the failed instance`() {
        val instance = FakeInstance(calls, startSucceeds = false)
        buffer.recordCompletedSpan("span", 1, 2)
        createHolder(instance).start(context)
        assertEquals(emptyList<BufferingSdkInstance>(), instance.handedOver)
        assertEquals(listOf(buffer), failed.handedOver)
        assertEquals(listOf("failed:recordCompletedSpan/7"), failed.drained)
    }

    @Test
    fun `start fails even if the handover to the failed instance fails`() {
        failed.transitionThrows = true
        val holder = createHolder(FakeInstance(calls, startSucceeds = false))
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        assertSame(failed, holder.api.target)
        assertEquals("transition failed", errorHandler.sdkErrorMessages.single().throwable?.message)
    }

    @Test
    fun `close after start`() {
        val instance = FakeInstance(calls)
        val holder = createHolder(instance)
        holder.start(context)
        holder.close()
        holder.api.logInfo("message")
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.api.target)
        assertEquals(listOf("instantiate", "enable", "onTransition", "disable", "disabled:logInfo/1"), calls)
    }

    @Test
    fun `calls during teardown are routed to the disabled instance`() {
        val instance = FakeInstance(calls)
        val holder = createHolder(instance)
        holder.start(context)
        instance.onDisable = { holder.api.logInfo("message") }
        holder.close()
        assertEquals(listOf("instantiate", "enable", "onTransition", "disable", "disabled:logInfo/1"), calls)
    }

    @Test
    fun `repeated close is ignored`() {
        val holder = createHolder(FakeInstance(calls))
        holder.start(context)
        holder.close()
        holder.close()
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("instantiate", "enable", "onTransition", "disable"), calls)
    }

    @Test
    fun `start after close is ignored`() {
        val holder = createHolder(FakeInstance(calls))
        holder.start(context)
        holder.close()
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("instantiate", "enable", "onTransition", "disable"), calls)
    }

    @Test
    fun `close before start is terminal`() {
        val holder = createHolder(FakeInstance(calls))
        holder.close()
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.api.target)
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.api.target)
        assertEquals(listOf("disabled:onTransition"), calls)
    }

    @Test
    fun `close after unsuccessful start is terminal`() {
        val instance = FakeInstance(calls, startSucceeds = false)
        val holder = createHolder(instance)
        holder.start(context)
        holder.close()
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.api.target)
        instance.startSucceeds = true
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(
            listOf("instantiate", "enable", "close", "failed:onTransition", "failed:disable", "deleteTelemetry"),
            calls,
        )
    }

    /**
     * The holder coordinates the cleanup: with no instance left to disable, it deletes the SDK's files itself, once.
     */
    @Test
    fun `close after instantiation that throws deletes the persisted telemetry once`() {
        val holder = createHolder { error("instantiation failed") }
        holder.start(context)
        holder.close()
        holder.close()
        assertEquals(listOf("instantiate", "failed:onTransition", "failed:disable", "deleteTelemetry"), calls)
    }

    /**
     * A start disabled by remote config is already disabled, but the app disabling the SDK still deletes what earlier
     * launches persisted, so none of it is sent if a later launch starts.
     */
    @Test
    fun `close after a start disabled by config deletes the persisted telemetry`() {
        val holder = createHolder { null }
        holder.start(context)
        holder.close()
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("instantiate", "disabled:onTransition", "deleteTelemetry"), calls)
    }

    /**
     * A disable made while a start that config disables is still creating its instance is applied once the start
     * completes, so it still deletes what earlier launches persisted.
     */
    @Test
    fun `disable called while instantiating a start disabled by config deletes the persisted telemetry`() {
        lateinit var holder: SdkStateHolder
        holder = createHolder {
            holder.api.disable()
            null
        }
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("instantiate", "disabled:onTransition", "deleteTelemetry"), calls)
    }

    @Test
    fun `close after a failed start still disables the sdk if the deletion fails`() {
        val holder = createHolder(
            instance = FakeInstance(calls, startSucceeds = false),
            persistedTelemetryDeletion = { { error("deletion failed") } },
        )
        holder.start(context)
        holder.close()
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.api.target)
        assertEquals(listOf("instantiate", "enable", "close", "failed:onTransition", "failed:disable"), calls)
    }

    @Test
    fun `failed disable is still terminal`() {
        val instance = FakeInstance(calls, closeThrows = true)
        val holder = createHolder(instance)
        holder.start(context)
        holder.close()
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.api.target)
        assertEquals("disable failed", errorHandler.sdkErrorMessages.single().throwable?.message)
        holder.close()
        assertEquals(listOf("instantiate", "enable", "onTransition", "disable"), calls)
    }

    @Test
    fun `api routes lifecycle calls through the holder`() {
        val holder = createHolder(FakeInstance(calls))
        holder.api.start(context)
        assertEquals(SdkState.ENABLED, holder.state)
        holder.api.logInfo("message")
        holder.api.disable()
        assertEquals(SdkState.DISABLED, holder.state)
        holder.api.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("instantiate", "enable", "onTransition", "logInfo/1", "disable"), calls)
    }

    @Test
    fun `internal interface api follows the dispatcher target`() {
        val holder = createHolder(FakeInstance(calls))
        assertSame(buffer, EmbraceInternalApi.internalInterfaceApi)
        val internalInterface = EmbraceInternalApi.internalInterface

        holder.start(context)
        calls.clear()
        internalInterface.addEnvelopeResource("key", "value")
        assertEquals(listOf("getInternalInterface/0"), calls)

        holder.close()
        calls.clear()
        internalInterface.addEnvelopeResource("key", "value")
        assertEquals(listOf("disabled:getInternalInterface/0"), calls)
    }

    @Test
    fun `internal interface api follows the failed instance`() {
        val holder = createHolder(FakeInstance(calls, startSucceeds = false))
        holder.start(context)
        calls.clear()
        EmbraceInternalApi.internalInterface.addEnvelopeResource("key", "value")
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
    fun `failed transition hook still starts the sdk`() {
        val instance = FakeInstance(calls = calls, transitionThrows = true)
        val holder = createHolder(instance)
        holder.start(context)
        assertEquals(SdkState.ENABLED, holder.state)
        assertEquals("transition failed", errorHandler.sdkErrorMessages.single().throwable?.message)
    }

    @Test
    fun `instance disabled by config is not retried`() {
        var instance: SdkInstance? = null
        val holder = createHolder { instance }
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.api.target)

        instance = FakeInstance(calls)
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.api.target)
        assertEquals(listOf("instantiate", "disabled:onTransition"), calls)
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
        assertSame(failed, holder.api.target)
        assertEquals("instantiation failed", errorHandler.sdkErrorMessages.single().throwable?.message)

        instantiationThrows = false
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        assertSame(failed, holder.api.target)
    }

    @Test
    fun `instance is never instantiated if disabled before start`() {
        val holder = createHolder(FakeInstance(calls))
        holder.close()
        holder.start(context)
        assertEquals(listOf("disabled:onTransition"), calls)
    }

    @Test
    fun `close before start is terminal even if the handover fails`() {
        disabled.transitionThrows = true
        val holder = createHolder(FakeInstance(calls))
        holder.close()
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.api.target)
        assertEquals("transition failed", errorHandler.sdkErrorMessages.single().throwable?.message)
    }

    @Test
    fun `concurrent starts instantiate the instance once`() {
        val holder = createHolder(FakeInstance(calls))
        val threads = List(8) { thread { holder.start(context) } }
        threads.forEach(Thread::join)
        assertEquals(SdkState.ENABLED, holder.state)
        assertEquals(listOf("instantiate", "enable", "onTransition"), calls)
    }

    @Test
    fun `SDK error handler is closed when the sdk is disabled`() {
        val holder = createHolder(FakeInstance(calls))
        holder.start(context)
        val errors = mutableListOf<Throwable>()
        bufferedErrorHandler.drain { throwable, _ -> errors += throwable }
        holder.close()
        trackError()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `SDK error handler is closed when the sdk fails to start`() {
        val holder = createHolder(FakeInstance(calls, startSucceeds = false))
        val errors = mutableListOf<Throwable>()
        bufferedErrorHandler.drain { throwable, _ -> errors += throwable }
        holder.start(context)
        assertEquals(SdkState.FAILED, holder.state)
        trackError()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `SDK error handler is closed when the sdk is disabled by config`() {
        assertErrorHandlerClosedOnStart(createHolder { null }, SdkState.DISABLED)
    }

    @Test
    fun `SDK error handler is closed when instantiation throws`() {
        assertErrorHandlerClosedOnStart(createHolder { error("instantiation failed") }, SdkState.FAILED)
    }

    @Test
    fun `buffer is closed after the sdk starts`() {
        val instance = FakeInstance(calls)
        createHolder(instance).start(context)
        assertBufferClosed(instance)
    }

    @Test
    fun `buffer is closed after an unsuccessful start`() {
        createHolder(FakeInstance(calls, startSucceeds = false)).start(context)
        assertBufferClosed(failed)
    }

    @Test
    fun `buffer is closed after the sdk is disabled by config`() {
        createHolder { null }.start(context)
        assertBufferClosed(disabled)
    }

    @Test
    fun `buffer is closed after close before start`() {
        createHolder(FakeInstance(calls)).close()
        assertBufferClosed(disabled)
    }

    /**
     * Code that runs on the main thread during start can disable the SDK, so the start call
     * must not clobber the desired state set on the SdkStateHolder.
     */
    @Test
    fun `disable called during start is not overwritten by the start`() {
        lateinit var holder: SdkStateHolder
        val instance = FakeInstance(calls = calls, onEnableAttempted = { holder.api.disable() })
        holder = createHolder(instance)
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.api.target)
        assertEquals(listOf("instantiate", "enable", "onTransition", "disable"), calls)
    }

    /**
     * A disable made while the instance finishes its init, such as by an instrumentation provider it loads, is applied once
     * that init has finished, so the instance isn't torn down partway through it.
     */
    @Test
    fun `disable called while the instance finishes its init is applied once it has finished`() {
        lateinit var holder: SdkStateHolder
        var disabledDuringInit = false
        val instance = FakeInstance(
            calls = calls,
            onEnabledHook = {
                holder.api.disable()
                disabledDuringInit = calls.contains("disable")
            },
        )
        holder = createHolder(instance)
        holder.start(context)
        assertFalse(disabledDuringInit)
        assertEquals(SdkState.DISABLED, holder.state)
        assertEquals(listOf("instantiate", "enable", "onTransition", "disable"), calls)
    }

    /**
     * A disable made while another is already held during the start is held as well, rather than applied partway through.
     */
    @Test
    fun `a second disable called during start is held as well`() {
        lateinit var holder: SdkStateHolder
        val instance = FakeInstance(
            calls = calls,
            onEnableAttempted = {
                holder.api.disable()
                holder.api.disable()
            },
        )
        holder = createHolder(instance)
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.api.target)
        assertEquals(listOf("instantiate", "enable", "onTransition", "disable"), calls)
    }

    @Test
    fun `start called during start is ignored`() {
        lateinit var holder: SdkStateHolder
        val instance = FakeInstance(calls = calls, onEnableAttempted = { holder.api.start(context) })
        holder = createHolder(instance)
        holder.start(context)
        assertEquals(SdkState.ENABLED, holder.state)
        assertEquals(listOf("instantiate", "enable", "onTransition"), calls)
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `disable called during a start that fails is applied once the start completes`() {
        lateinit var holder: SdkStateHolder
        val instance = FakeInstance(calls = calls, startSucceeds = false, onEnableAttempted = { holder.api.disable() })
        holder = createHolder(instance)
        holder.start(context)
        assertEquals(SdkState.DISABLED, holder.state)
        assertSame(disabled, holder.api.target)

        // the holder disables the instance rather than closing it, so the instance deletes the telemetry once its own workers
        // finish, and the holder doesn't delete it again
        assertEquals(listOf("instantiate", "enable", "disable", "failed:onTransition", "failed:disable"), calls)
    }

    /**
     * Once the SDK is enabled, public API calls from other threads should reach the instance. It keeps finishing its init
     * after that point (listeners, instrumentation loading, payload send, init tracking), so a call dropped there is lost
     * telemetry.
     */
    @Test
    fun `calls from another thread while the instance finishes its init are not dropped`() {
        lateinit var holder: SdkStateHolder
        val instance = FakeInstance(
            calls = calls,
            onEnabledHook = { thread { holder.api.logInfo("message") }.join() },
        )
        holder = createHolder(instance)
        holder.start(context)
        assertTrue(calls.contains("logInfo/1"))
    }

    /**
     * A call that arrives while the buffered calls are being handed over must not overtake them. Here the app
     * tracks an experiment before start, then untracks it on another thread while the handover is in progress.
     */
    @Test
    fun `a call made during the handover is applied after the buffered calls`() {
        lateinit var holder: SdkStateHolder
        lateinit var instance: FakeInstance
        instance = FakeInstance(
            calls = calls,
            onTransitionHook = { buffer ->
                thread { holder.api.untrackExperiment("a", 2) }.join()
                buffer.drainExperimentCalls(instance) { replayed ->
                    replayed.forEach { calls += "replay:${it.javaClass.simpleName}" }
                }
            },
        )
        holder = createHolder(instance)
        holder.api.trackExperiment("a", "v1", 1)
        holder.start(context)
        val experimentCalls = calls.mapNotNull {
            when (it) {
                "replay:Track" -> "track"
                "replay:Untrack", "untrackExperiment/2" -> "untrack"
                else -> null
            }
        }
        assertEquals(listOf("track", "untrack"), experimentCalls)
    }

    private fun assertErrorHandlerClosedOnStart(holder: SdkStateHolder, expected: SdkState) {
        val errors = mutableListOf<Throwable>()
        bufferedErrorHandler.drain { throwable, _ -> errors += throwable }
        holder.start(context)
        assertEquals(expected, holder.state)
        errors.clear()
        trackError()
        assertTrue(errors.isEmpty())
    }

    private fun assertBufferClosed(target: FakeInstance) {
        assertFalse(buffer.recordCompletedSpan("span", 1, 2))
        assertEquals(emptyList<String>(), target.drained)
    }

    private fun trackError() {
        bufferedErrorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { throw IllegalStateException() }
    }

    private fun createHolder(
        instance: SdkInstance,
        persistedTelemetryDeletion: PersistedTelemetryDeletion = recordDeletion,
    ) = createHolder(persistedTelemetryDeletion) { instance }

    private fun createHolder(
        persistedTelemetryDeletion: PersistedTelemetryDeletion = recordDeletion,
        instanceFactory: () -> SdkInstance?,
    ) = SdkStateHolder(
        realSdkInstanceFactory = {
            calls += "instantiate"
            instanceFactory()
        },
        preStart = buffer,
        failed = { failed },
        disabled = { disabled },
        persistedTelemetryDeletion = persistedTelemetryDeletion,
        telemetryService = FakeInternalTelemetryService(),
        errorHandler = errorHandler,
    )

    private class FakeInstance(
        private val calls: MutableList<String>,
        var startSucceeds: Boolean = true,
        private val startThrows: Boolean = false,
        val closeThrows: Boolean = false,
        var transitionThrows: Boolean = false,
        private val prefix: String = "",
        private val onEnabledHook: () -> Unit = {},
        private val onEnableAttempted: () -> Unit = {},
        private val onTransitionHook: (BufferingSdkInstance) -> Unit = {},
    ) : SdkInstance, InternalSdkApi by recordingSdkApi(calls, prefix) {

        private var started = false

        val handedOver = mutableListOf<BufferingSdkInstance>()

        val drained = mutableListOf<String>()

        var onDisable: () -> Unit = {}

        override fun onTransition(buffer: BufferingSdkInstance) {
            calls += "${prefix}onTransition"
            handedOver += buffer
            buffer.drainCompletedSpans(recordingSdkApi(drained, prefix))
            check(!transitionThrows) { "transition failed" }
            onTransitionHook(buffer)
        }

        override val isStarted: Boolean get() = started

        override fun enable(context: Context) {
            calls += "enable"
            check(!startThrows) { "start failed" }
            onEnableAttempted()
            if (startSucceeds) {
                started = true
            }
        }

        override fun onEnabled() {
            onEnabledHook()
        }

        override fun close() {
            calls += "${prefix}close"
            check(!closeThrows) { "close failed" }
            started = false
        }

        override fun disable() {
            calls += "${prefix}disable"
            onDisable()
            check(!closeThrows) { "disable failed" }
            started = false
        }
    }
}
