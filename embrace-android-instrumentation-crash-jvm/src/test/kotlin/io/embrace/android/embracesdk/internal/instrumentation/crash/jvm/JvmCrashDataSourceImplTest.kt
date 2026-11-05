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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

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
    fun `a crash on another thread waits for the first crash's teardown`() {
        setupForHandleCrash(true)
        val releaseTeardown = CountDownLatch(1)
        val firstCrash = startFirstCrashWithBlockedTeardown(releaseTeardown)

        val laterCrash = thread { crashDataSource.logUnhandledJvmThrowable(RuntimeException("later crash")) }
        assertWaiting(laterCrash)

        releaseTeardown.countDown()
        assertReturns(laterCrash)
        assertReturns(firstCrash)
        assertEquals(1, args.destination.logEvents.size)
    }

    @Test
    fun `disabling data capture releases a crash waiting for the first crash's teardown`() {
        setupForHandleCrash(true)
        val releaseTeardown = CountDownLatch(1)
        val firstCrash = startFirstCrashWithBlockedTeardown(releaseTeardown)
        val laterCrash = thread { crashDataSource.logUnhandledJvmThrowable(RuntimeException("later crash")) }
        assertWaiting(laterCrash)

        crashDataSource.onDataCaptureDisabled()
        assertReturns(laterCrash)

        releaseTeardown.countDown()
        assertReturns(firstCrash)
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

    /**
     * Starts a crash on another thread and returns that thread once the crash's teardown has begun. The teardown then blocks
     * until [releaseTeardown] is released.
     */
    private fun startFirstCrashWithBlockedTeardown(releaseTeardown: CountDownLatch): Thread {
        val tearingDown = CountDownLatch(1)
        crashDataSource.addCrashTeardownHandler {
            tearingDown.countDown()
            releaseTeardown.await(TEARDOWN_BLOCKED_MS, TimeUnit.MILLISECONDS)
        }
        val firstCrash = thread { crashDataSource.logUnhandledJvmThrowable(testException) }
        assertTrue(tearingDown.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        return firstCrash
    }

    /**
     * Waits until [crash] is parked with a timeout, which a later crash's thread only is while it waits for the first crash's
     * teardown. Fails as soon as the thread ends instead, as a later crash that doesn't wait returns straight away.
     */
    private fun assertWaiting(crash: Thread) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS)
        while (crash.state != Thread.State.TIMED_WAITING) {
            assertNotEquals("the crash returned without waiting", Thread.State.TERMINATED, crash.state)
            assertTrue("the crash never started waiting", System.nanoTime() < deadline)
            Thread.sleep(1)
        }
    }

    private fun assertReturns(crash: Thread) {
        crash.join(TIMEOUT_MS)
        assertFalse("the crash is still waiting", crash.isAlive)
    }

    private companion object {
        // well inside the 5 s a later crash waits at most, so that a crash returns in time only when it is released, not when
        // its wait runs out
        const val TIMEOUT_MS = 2000L

        // longer than any wait in a test and than the 5 s a later crash waits, so that only the test ends a blocked teardown
        const val TEARDOWN_BLOCKED_MS = 10_000L
    }
}
