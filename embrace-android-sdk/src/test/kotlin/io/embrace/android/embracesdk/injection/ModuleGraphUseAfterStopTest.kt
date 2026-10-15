package io.embrace.android.embracesdk.injection

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationModule
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.fakes.FakeReadWriteSpan
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModuleImpl
import io.embrace.android.embracesdk.internal.injection.loadInstrumentation
import io.embrace.android.embracesdk.internal.injection.markSdkInitComplete
import io.embrace.android.embracesdk.internal.injection.postInit
import io.embrace.android.embracesdk.internal.injection.postLoadInstrumentation
import io.embrace.android.embracesdk.internal.injection.registerListeners
import io.embrace.android.embracesdk.internal.injection.triggerPayloadSend
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import io.opentelemetry.kotlin.NoopOpenTelemetry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.ScheduledThreadPoolExecutor

/**
 * [ModuleInitBootstrapper.stop] (called by `Embrace.disable()`) swaps the bootstrapper's delegate back to
 * `UninitializedModuleGraph` which throws. SdkInitActions capture the bootstrapper rather than the
 * current module, meaning the uninitialized module could be called and throw.
 */
@RunWith(AndroidJUnit4::class)
internal class ModuleGraphUseAfterStopTest {

    private lateinit var context: Context
    private lateinit var logger: FakeInternalLogger
    private lateinit var workerThreadModule: CapturingWorkerThreadModule
    private lateinit var bootstrapper: ModuleInitBootstrapper

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        context = application.applicationContext
        logger = FakeInternalLogger(
            throwOnInternalError = false,
            ignoredErrors = listOf(InternalErrorType.NativeReadFail, InternalErrorType.UrlStreamHandlerFactoryInstallFail),
        )
        workerThreadModule = CapturingWorkerThreadModule()
        bootstrapper = ModuleInitBootstrapper(
            initModule = FakeInitModule(clock = FakeClock(), logger = logger),
            configServiceSupplier = { _, _, _, _, _ -> FakeConfigService() },
            coreModuleSupplier = { _, _, _ -> FakeCoreModule() },
            workerThreadModuleSupplier = { workerThreadModule },
            instrumentationModuleSupplier = { _, _, _, _, _, _, _, _, _ ->
                FakeInstrumentationModule(application, logger = logger)
            },
        )
    }

    @After
    fun tearDown() {
        workerThreadModule.shutdownCapturing()
    }

    @Test
    fun `stop is final for the process`() {
        startSdk()
        bootstrapper.stop()
        assertFalse(bootstrapper.init(context))
    }

    @Test
    fun `sdk init attributes provider is safe after stop`() {
        startSdk()
        val startupService = bootstrapper.dataCaptureServiceModule.startupService
        bootstrapper.stop()
        assertNoThrow("StartupService.getSdkInitAttributes") {
            startupService.getSdkInitAttributes()
        }
    }

    @Test
    fun `deferred sdk init span task is safe after stop`() {
        startSdk()
        bootstrapper.stop()
        assertTasksDoNotThrow(workerThreadModule.tasksFrom(Phase.MARK_SDK_INIT_COMPLETE))
    }

    @Test
    fun `deferred network connectivity registration is safe after stop`() {
        startSdk()
        bootstrapper.stop()
        assertTasksDoNotThrow(workerThreadModule.tasksFrom(Phase.POST_LOAD_INSTRUMENTATION))
    }

    @Test
    fun `deferred payload resurrection and delivery tasks are safe after stop`() {
        startSdk()
        bootstrapper.stop()
        assertTasksDoNotThrow(workerThreadModule.tasksFrom(Phase.TRIGGER_PAYLOAD_SEND))
    }

    @Test
    fun `internal error handler provider is safe after stop`() {
        startSdk()
        val errorHandlerProvider = bootstrapper.initModule.logger.errorHandlerProvider
        bootstrapper.stop()
        assertNoThrow("InternalLogger.errorHandlerProvider") {
            errorHandlerProvider()
        }
    }

    @Test
    fun `log stored callback is safe after stop`() {
        startSdk()
        val logSink = bootstrapper.openTelemetryModule.logSink
        bootstrapper.stop()
        assertEquals(StoreDataResult.SUCCESS, logSink.storeLogs(listOf(Log(body = "after-stop"))))
    }

    @Test
    fun `event metadata provider is safe when a log is emitted after stop`() {
        startSdk()
        val processor = bootstrapper.openTelemetryModule.otelSdkConfig.logRecordProcessor
        bootstrapper.stop()
        assertNoThrow("LogRecordProcessor.onEmit") {
            processor.onEmit(FakeReadWriteLogRecord(), NoopOpenTelemetry.context.implicit())
        }
    }

    @Test
    fun `user id provider is safe when a span starts after stop`() {
        startSdk()
        val processor = bootstrapper.openTelemetryModule.otelSdkConfig.spanProcessor
        bootstrapper.stop()
        assertNoThrow("SpanProcessor.onStart") {
            processor.onStart(FakeReadWriteSpan(), NoopOpenTelemetry.context.implicit())
        }
    }

    @Test
    fun `public tracing api is safe after stop`() {
        startSdk()
        val tracingApi = bootstrapper.openTelemetryModule.tracingApi
        bootstrapper.stop()
        assertNoThrow("TracingApi.startSpan") {
            tracingApi.startSpan("after-stop")?.stop()
        }
    }

    @Test
    fun `session part change listeners all run after stop`() {
        startSdk()
        val tracker = bootstrapper.essentialServiceModule.sessionPartTracker
        var sentinelInvoked = false
        tracker.addSessionPartChangeListener { sentinelInvoked = true }

        bootstrapper.stop()
        tracker.newActiveSessionPart({}, { null }, ProcessState.FOREGROUND)

        assertTrue("A listener registered after the SDK's own was skipped", sentinelInvoked)
    }

    /**
     * Mirrors EmbraceImpl.start() task order.
     */
    private fun startSdk() {
        assertTrue(bootstrapper.init(context))
        bootstrapper.postInit()
        bootstrapper.registerListeners()
        bootstrapper.loadInstrumentation()
        workerThreadModule.recording(Phase.POST_LOAD_INSTRUMENTATION) { bootstrapper.postLoadInstrumentation() }
        workerThreadModule.recording(Phase.TRIGGER_PAYLOAD_SEND) { bootstrapper.triggerPayloadSend() }
        workerThreadModule.recording(Phase.MARK_SDK_INIT_COMPLETE) { bootstrapper.markSdkInitComplete { emptyMap() } }
        assertEquals(
            "SDK start was not clean, so the use-after-stop assertions would be meaningless",
            emptyList<FakeInternalLogger.LogMessage>(),
            logger.internalErrorMessages,
        )
    }

    private fun assertTasksDoNotThrow(tasks: List<Runnable>) {
        assertTrue("Expected the phase to defer at least one task", tasks.isNotEmpty())
        tasks.forEachIndexed { index, task ->
            assertNoThrow("deferred task #$index") { task.run() }
        }
    }

    private fun assertNoThrow(description: String, action: () -> Unit) {
        try {
            action()
        } catch (exc: Throwable) {
            fail("$description threw after stop(): $exc\n${exc.stackTraceToString()}")
        }
    }

    private enum class Phase {
        POST_LOAD_INSTRUMENTATION,
        TRIGGER_PAYLOAD_SEND,
        MARK_SDK_INIT_COMPLETE,
    }

    private class CapturingWorkerThreadModule(
        private val base: WorkerThreadModule = WorkerThreadModuleImpl(),
    ) : WorkerThreadModule by base {

        private val executor = CapturingExecutor()
        private val worker = BackgroundWorker(executor)
        private val tasksByPhase = mutableMapOf<Phase, List<Runnable>>()

        override fun backgroundWorker(worker: Worker.Background): BackgroundWorker = when (worker) {
            Worker.Background.NonIoRegWorker, Worker.Background.IoRegWorker -> this.worker
            else -> base.backgroundWorker(worker)
        }

        fun recording(phase: Phase, action: () -> Unit) {
            val start = executor.submitted.size
            action()
            tasksByPhase[phase] = executor.submitted.subList(start, executor.submitted.size).toList()
        }

        fun tasksFrom(phase: Phase): List<Runnable> = tasksByPhase[phase].orEmpty()

        fun shutdownCapturing() {
            executor.shutdownNow()
            base.close()
        }
    }

    private class CapturingExecutor : ScheduledThreadPoolExecutor(1) {
        val submitted = mutableListOf<Runnable>()

        override fun submit(task: Runnable): Future<*> {
            submitted.add(task)
            return CompletableFuture.completedFuture(null)
        }
    }
}
