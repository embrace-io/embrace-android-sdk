package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import android.app.Application
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class JvmCrashDataSourceImplTest {

    private lateinit var crashDataSource: JvmCrashDataSourceImpl
    private lateinit var args: FakeInstrumentationArgs
    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var testException: Exception
    private lateinit var ctx: Application
    private var modifier: ((TelemetryAttributes) -> SchemaType)? = null

    @Before
    fun setUp() {
        ctx = Application()
        args = FakeInstrumentationArgs(ctx)
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        testException = RuntimeException("Test exception")
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    private fun setupForHandleCrash(crashHandlerEnabled: Boolean = false) {
        args = FakeInstrumentationArgs(
            ctx,
            errorHandler = errorHandler,
            configService = FakeConfigService(
                config = EmbraceConfig(autoDataCapture = { AutoDataCaptureConfig(jvmCrashCaptureEnabled = { crashHandlerEnabled }) }),
            ),
        )
        crashDataSource = JvmCrashDataSourceImpl(args).apply {
            telemetryModifier = modifier
        }
    }

    @Test
    fun `test crash handler order`() {
        setupForHandleCrash(true)
        val observedOrder = mutableListOf<Int>()
        crashDataSource.addCrashTeardownHandler { observedOrder.add(1) }
        crashDataSource.addCrashTeardownHandler { observedOrder.add(2) }
        crashDataSource.addCrashTeardownHandler { observedOrder.add(3) }
        crashDataSource.logUnhandledJvmThrowable(testException)
        assertEquals(listOf(1, 2, 3), observedOrder)
    }

    @Test
    fun `a throwing teardown handler does not skip the handlers after it`() {
        setupForHandleCrash(true)
        val observedOrder = mutableListOf<Int>()
        crashDataSource.addCrashTeardownHandler { observedOrder.add(1) }
        crashDataSource.addCrashTeardownHandler { error("boom") }
        crashDataSource.addCrashTeardownHandler { observedOrder.add(3) }
        crashDataSource.logUnhandledJvmThrowable(testException)
        assertEquals(listOf(1, 3), observedOrder)
        assertEquals(1, args.destination.logEvents.size)
        assertEquals(listOf(SdkErrorType.CrashTeardownFail.toString()), errorHandler.sdkErrorMessages.map { it.msg })
    }

    @Test
    fun `teardown runs when capturing the crash fails`() {
        modifier = { throw OutOfMemoryError("boom") }
        setupForHandleCrash(true)
        val crashIds = mutableListOf<String>()
        crashDataSource.addCrashTeardownHandler { crashIds.add(it) }
        crashDataSource.logUnhandledJvmThrowable(testException)
        assertEquals(0, args.destination.logEvents.size)
        assertEquals(1, crashIds.size)
        assertEquals(listOf(SdkErrorType.DataSourceDataCaptureFail.toString()), errorHandler.sdkErrorMessages.map { it.msg })
    }

    @Test
    fun `teardown runs when crash capture is disabled by config`() {
        setupForHandleCrash(false)
        val crashIds = mutableListOf<String>()
        crashDataSource.addCrashTeardownHandler { crashIds.add(it) }
        crashDataSource.logUnhandledJvmThrowable(testException)
        assertEquals(0, args.destination.logEvents.size)
        assertEquals(1, crashIds.size)
    }

    @Test
    fun `crash after data capture disabled is neither captured nor torn down`() {
        setupForHandleCrash(true)
        val observedOrder = mutableListOf<Int>()
        crashDataSource.addCrashTeardownHandler { observedOrder.add(1) }
        crashDataSource.onDataCaptureDisabled()

        crashDataSource.logUnhandledJvmThrowable(testException)
        assertEquals(0, args.destination.logEvents.size)
        assertEquals(emptyList<Int>(), observedOrder)
    }

    @Test
    fun `disabling data capture detaches the handler but still delegates to the previous one`() {
        val previous = EmbraceUncaughtExceptionHandlerTest.TestUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(previous)
        setupForHandleCrash(true)
        val handler = Thread.getDefaultUncaughtExceptionHandler() as EmbraceUncaughtExceptionHandler
        crashDataSource.onDataCaptureDisabled()

        // left installed so that handlers which wrap it are not clobbered
        assertSame(handler, Thread.getDefaultUncaughtExceptionHandler())
        handler.uncaughtException(Thread.currentThread(), testException)
        assertEquals(0, args.destination.logEvents.size)
        assertSame(testException, previous.throwable)
    }

    @Test
    fun `test exception handler sends telemetry with config option enabled`() {
        setupForHandleCrash(true)
        assert(Thread.getDefaultUncaughtExceptionHandler() is EmbraceUncaughtExceptionHandler)
        crashDataSource.logUnhandledJvmThrowable(IllegalStateException())
        assertEquals(1, args.destination.logEvents.size)
    }

    @Test
    fun `default crash handler delegate will not be set if it is already an embrace handler`() {
        val embraceDefaultHandler = EmbraceUncaughtExceptionHandler(
            defaultHandler = null,
            dataSource = FakeJvmCrashDataSource(),
            errorHandler = FakeErrorHandler(),
        )
        Thread.setDefaultUncaughtExceptionHandler(embraceDefaultHandler)
        setupForHandleCrash(true)
        assertSame(Thread.getDefaultUncaughtExceptionHandler(), embraceDefaultHandler)
    }

    @Test
    fun `test exception handler does not send telemetry with config option disabled`() {
        setupForHandleCrash(false)
        assert(Thread.getDefaultUncaughtExceptionHandler() is EmbraceUncaughtExceptionHandler)
        assertEquals(0, args.destination.logEvents.size)
    }
}
